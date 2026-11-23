package net.gasmp.core.casino;

import net.gasmp.core.GASMPCore;
import net.gasmp.core.gui.ScreenUi;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side Shards-based arcade engine. Shards are virtual server currency; no real-money payments. */
public final class CasinoService implements Listener, CommandExecutor, TabCompleter {
    private record Result(String label, double grossPayout) { }
    private final GASMPCore plugin;
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private org.bukkit.configuration.file.YamlConfiguration config;
    private final Map<UUID, BukkitTask> spinTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> spinTicks = new ConcurrentHashMap<>();
    private final CasinoDuelService duels;

    public CasinoService(GASMPCore plugin) {
        this.plugin = plugin;
        reload();
        this.duels = new CasinoDuelService(plugin, this);
        Bukkit.getPluginManager().registerEvents(duels, plugin);
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "casino.yml");
        if (!file.exists()) plugin.saveResource("casino.yml", false);
        config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        // This server's requested Arcade currency is GASMP Shards, not Vault Coins.
        config.set("settings.enabled", true);
        // Make every shipped game available after a reload, including the two
        // quick-match entries that older configs may have left switched off.
        for (String game : List.of("roulette", "darts", "drums", "races", "pyramid", "stairs",
                "lottery", "crash", "classic", "blackjack", "rps",
                "tictactoe-quick-match", "poker-quick-match")) {
            config.set("games." + game, true);
        }
        if (!config.contains("settings.minimum-bet")) config.set("settings.minimum-bet", 1);
        if (!config.contains("settings.maximum-bet")) config.set("settings.maximum-bet", 100);
        try {
            config.save(file);
        } catch (java.io.IOException error) {
            plugin.getLogger().warning("Could not persist Arcade settings: " + error.getMessage());
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Casino is a player command."); return true; }
        if (!plugin.getConfig().getBoolean("modules.casino", true) || !config.getBoolean("settings.enabled", true)) {
            player.sendMessage(color("&cThe GASMP arcade is currently disabled.")); return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { help(player); return true; }
        if (args[0].equalsIgnoreCase("challenge") || args[0].equalsIgnoreCase("accept")
                || args[0].equalsIgnoreCase("decline") || args[0].equalsIgnoreCase("duel")) {
            return duels.handle(player, args);
        }
        if (args[0].equalsIgnoreCase("menu")) { openMenu(player, 10.0); return true; }
        if (args[0].equalsIgnoreCase("odds")) { showOdds(player); return true; }
        if (spinTasks.containsKey(player.getUniqueId())) {
            player.sendMessage(color("&cFinish or close your current arcade spin first."));
            return true;
        }

        String game = args[0].toLowerCase(Locale.ROOT);
        String configKey = switch (game) {
            case "slots" -> "drums";
            case "rps" -> "rps";
            case "tictactoe" -> "tictactoe-quick-match";
            case "poker" -> "poker-quick-match";
            default -> game;
        };
        if (!config.getBoolean("games." + configKey, false)) {
            player.sendMessage(color("&cThat arcade game is disabled in casino.yml.")); return true;
        }
        if (args.length < 2) { player.sendMessage(color("&e/casino " + game + " <shards> [choice]")); return true; }

        long bet;
        try { bet = Long.parseLong(args[1]); }
        catch (NumberFormatException ex) { player.sendMessage(color("&cEnter a whole-number Shard wager.")); return true; }
        long min = Math.max(1L, (long) Math.ceil(config.getDouble("settings.minimum-bet", 1.0)));
        long max = Math.max(min, (long) Math.floor(config.getDouble("settings.maximum-bet", 100.0)));
        if (bet < min || bet > max) {
            player.sendMessage(color("&cWager must be between " + min + " and " + max + " Shards.")); return true;
        }
        if (duels.isBusy(player.getUniqueId())) {
            player.sendMessage(color("&cFinish or leave your multiplayer Arcade challenge first."));
            return true;
        }
        if (active.contains(player.getUniqueId())) {
            player.sendMessage(color("&cYour previous game is still processing.")); return true;
        }
        long cooldown = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (cooldown > System.currentTimeMillis()) {
            player.sendMessage(color("&cWait " + ((cooldown-System.currentTimeMillis()+999)/1000)
                    + "s before playing again.")); return true;
        }

        Result result;
        try { result = play(game, bet, args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : ""); }
        catch (IllegalArgumentException ex) { player.sendMessage(color("&c" + ex.getMessage())); return true; }
        if (result == null) { player.sendMessage(color("&cUnknown game. Use /casino help.")); return true; }

        active.add(player.getUniqueId());
        // Shards are the arcade's only currency. Debit first, then credit the
        // rounded whole-Shard payout through the existing transactional ledger.
        plugin.getDataStore().changeShards(player.getUniqueId(), player.getName(), player.getUniqueId(),
                "ARCADE_WAGER", bet, net.gasmp.core.data.PlayerDataStore.ShardChange.TAKE,
                "GASMP Arcade: " + game)
                .whenComplete((balance, debitError) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (debitError != null) {
                        active.remove(player.getUniqueId());
                        player.sendMessage(color("&cYou do not have enough Shards, or the Shard ledger is unavailable."));
                        plugin.getLogger().fine("Arcade wager rejected for " + player.getUniqueId() + ": " + rootMessage(debitError));
                        return;
                    }
                    settleShardsGame(player, game, bet, result);
                }));
        return true;
    }

    private void settleShardsGame(Player player, String game, long bet, Result result) {
        long payout = Math.max(0L, Math.round(result.grossPayout()));
        java.util.concurrent.CompletableFuture<Long> credit = payout > 0L
                ? plugin.getDataStore().changeShards(player.getUniqueId(), player.getName(), null,
                        "ARCADE_REWARD", payout, net.gasmp.core.data.PlayerDataStore.ShardChange.GIVE,
                        "GASMP Arcade win: " + game)
                : java.util.concurrent.CompletableFuture.completedFuture(0L);

        credit.whenComplete((balance, payoutError) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (payoutError != null) {
                plugin.getDataStore().changeShards(player.getUniqueId(), player.getName(), null,
                        "ARCADE_REFUND", bet, net.gasmp.core.data.PlayerDataStore.ShardChange.GIVE,
                        "Refund after arcade payout failure: " + game)
                        .whenComplete((refunded, refundError) -> {
                            if (refundError != null) plugin.getLogger().severe("Arcade refund failed for "
                                    + player.getUniqueId() + ": " + rootMessage(refundError));
                        });
                player.sendMessage(color("&cArcade payout failed. Your wager is being refunded."));
                plugin.getLogger().severe("Arcade payout failed for " + player.getUniqueId() + ": " + rootMessage(payoutError));
                active.remove(player.getUniqueId());
                return;
            }

            String outcome = payout > bet ? "WIN" : payout == bet ? "PUSH" : "LOSS";
            plugin.getDataStore().recordCasinoTransaction(player.getUniqueId(), player.getName(), game,
                    bet, payout, outcome).exceptionally(error -> {
                plugin.getLogger().warning("Arcade audit write failed: " + rootMessage(error)); return null;
            });
            long net = payout - bet;
            player.sendMessage(color("&6&lGASMP ARCADE &8» &f" + result.label()));
            player.sendMessage(color(outcome.equals("WIN") ? "&aWIN &8| &d+" + net + " Shards"
                    : outcome.equals("PUSH") ? "&ePUSH &8| &fYour wager was returned"
                    : "&cLOSS &8| &f-" + bet + " Shards"));
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis()
                    + config.getLong("settings.cooldown-seconds", 5) * 1000L);
            active.remove(player.getUniqueId());
        }));
    }

    /** Canonical game key shared by solo and multiplayer Arcade modes. */
    public String canonicalGame(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "slots" -> "drums";
            case "rockpaperscissors" -> "rps";
            case "tictactoe-quick-match" -> "tictactoe";
            case "poker-quick-match" -> "poker";
            default -> raw.toLowerCase(Locale.ROOT);
        };
    }

    public boolean isGameEnabled(String game) {
        String key = switch (canonicalGame(game)) {
            case "tictactoe" -> "tictactoe-quick-match";
            case "poker" -> "poker-quick-match";
            default -> canonicalGame(game);
        };
        return config.getBoolean("games." + key, false);
    }

    public long minimumBet() {
        return Math.max(1L, (long) Math.ceil(config.getDouble("settings.minimum-bet", 1.0)));
    }

    public long maximumBet() {
        return Math.max(minimumBet(), Math.min(1_000_000L,
                (long) Math.floor(config.getDouble("settings.maximum-bet", 100.0))));
    }

    /** Runs the established game resolver at a one-Shard scale for duel scoring. */
    public double rollDuelScore(String game, String choice) {
        Result result = play(canonicalGame(game), 1.0, choice);
        return result == null ? 0.0 : result.grossPayout();
    }

    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private void openMenu(Player player, double selectedBet) {
        CasinoMenuHolder holder = new CasinoMenuHolder(CasinoMenuHolder.Stage.MENU, null, selectedBet);
        Inventory inventory = create(holder, 54, ScreenUi.title("casino", "&8GASMP &7• &6Arcade"));
        set(inventory, 4, Material.SUNFLOWER, "&6&lGASMP ARCADE", "&7Choose a wager, then pick a game.",
                "&7Virtual Shards only; no real-money deposits or cash-out.");
        double[] bets = {1, 5, 10, 25, 50};
        int[] betSlots = {20, 21, 22, 23, 24};
        for (int i = 0; i < bets.length; i++) {
            double bet = bets[i];
            boolean selected = Math.abs(selectedBet - bet) < 0.0001;
            set(inventory, betSlots[i], selected ? Material.LIME_CONCRETE : Material.GOLD_NUGGET,
                    (selected ? "&a&lSELECTED " : "&e") + money(bet) + " Shards", "&7Click to select this wager.");
            holder.action(betSlots[i], "BET:" + bet);
        }
        String[][] games = {
                {"roulette", "Roulette", "WHEEL"}, {"drums", "Slot Drums", "JUKEBOX"},
                {"darts", "Darts", "TARGET"}, {"races", "Races", "SADDLE"},
                {"pyramid", "Pyramid", "GOLD_BLOCK"}, {"stairs", "Stairs", "LADDER"},
                {"lottery", "Lottery", "PAPER"}, {"crash", "Crash", "FIREWORK_ROCKET"},
                {"classic", "Classic", "SUNFLOWER"}, {"blackjack", "Blackjack", "BLACK_BANNER"},
                {"rps", "Rock Paper Scissors", "SHEARS"}, {"tictactoe", "Tic-Tac-Toe quick match", "OAK_SIGN"},
                {"poker", "Poker quick match", "DIAMOND"}
        };
        int[] gameSlots = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42};
        int gameIndex = 0;
        for (String[] game : games) {
            Material icon = Material.matchMaterial(game[2]);
            if (icon == null) icon = Material.PAPER;
            int slot = gameSlots[gameIndex++];
            set(inventory, slot, icon, "&6" + game[1], "&7Selected wager: &e" + money(selectedBet), "&aClick to continue.");
            holder.action(slot, "GAME:" + game[0]);
        }
        set(inventory, 49, Material.BARRIER, "&cClose");
        player.openInventory(inventory);
    }

    private void openChoices(Player player, String game, double bet) {
        CasinoMenuHolder holder = new CasinoMenuHolder(CasinoMenuHolder.Stage.CHOICES, game, bet);
        Inventory inventory = create(holder, 36, ScreenUi.title("casino_choices", "&8GASMP &7• &6" + game.toUpperCase(Locale.ROOT)));
        set(inventory, 4, Material.GOLD_INGOT, "&6&l" + game.toUpperCase(Locale.ROOT), "&7Wager: &e" + money(bet) + " Shards", "&7Choose your play.");
        if (game.equals("blackjack") || game.equals("drums") || game.equals("tictactoe") || game.equals("poker")) {
            set(inventory, 13, Material.LIME_CONCRETE, "&a&lPLAY NOW", "&7Start this game with the selected wager.");
            holder.action(13, "PLAY:");
        } else {
            List<String> choices = choicesFor(game);
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21};
            for (int i = 0; i < choices.size() && i < slots.length; i++) {
                String choice = choices.get(i);
                Material icon = choiceIcon(game, choice);
                set(inventory, slots[i], icon, "&e" + choice.toUpperCase(Locale.ROOT), "&7Click to play.");
                holder.action(slots[i], "PLAY:" + choice);
            }
        }
        set(inventory, 31, Material.ARROW, "&fBack to arcade");
        holder.action(31, "BACK");
        set(inventory, 35, Material.BARRIER, "&cClose");
        player.openInventory(inventory);
    }

    private List<String> choicesFor(String game) {
        return switch (game) {
            case "roulette" -> List.of("red", "black");
            case "darts" -> List.of("high", "low", "bull");
            case "races" -> List.of("1", "2", "3", "4", "5");
            case "pyramid" -> List.of("1", "2", "3");
            case "stairs" -> List.of("1", "2", "3", "4", "5");
            case "lottery" -> List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
            case "crash" -> List.of("1.10", "1.25", "1.50", "2.00", "3.00", "5.00");
            case "classic" -> List.of("heads", "tails");
            case "rps" -> List.of("rock", "paper", "scissors");
            default -> List.of();
        };
    }

    private Material choiceIcon(String game, String choice) {
        if (game.equals("roulette")) return choice.equals("red") ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
        if (game.equals("rps")) return switch (choice) {
            case "rock" -> Material.COBBLESTONE;
            case "paper" -> Material.PAPER;
            default -> Material.SHEARS;
        };
        if (game.equals("darts")) return choice.equals("bull") ? Material.TARGET : Material.ARROW;
        if (game.equals("classic")) return Material.SUNFLOWER;
        if (game.equals("crash")) return Material.FIREWORK_ROCKET;
        return Material.PAPER;
    }

    private void startSpin(Player player, String game, double bet, String choice) {
        if (spinTasks.containsKey(player.getUniqueId())) return;
        CasinoMenuHolder holder = new CasinoMenuHolder(CasinoMenuHolder.Stage.SPIN, game, bet);
        Inventory inventory = create(holder, 27, ScreenUi.title("casino_spin", "&8GASMP &7• &6Spinning..."));
        set(inventory, 4, Material.CLOCK, "&6&lGOOD LUCK", "&7Wager: &e" + money(bet) + " Shards");
        player.openInventory(inventory);
        spinTicks.put(player.getUniqueId(), 0);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != holder) {
                cancelSpin(player.getUniqueId());
                return;
            }
            int tick = spinTicks.merge(player.getUniqueId(), 1, Integer::sum);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            renderCasinoFrame(inventory, game, tick, random);
            if (tick % 4 == 0) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f,
                        0.75f + (tick / 40.0f) * 0.9f + random.nextFloat() * 0.15f);
            }
            if (tick >= 40) {
                cancelSpin(player.getUniqueId());
                player.closeInventory();
                // Resolve the server-side result only after the cosmetic spin has finished.
                onCommand(player, null, "casino", new String[]{game, Long.toString(Math.round(bet)), choice});
            }
        }, 0L, 1L);
        spinTasks.put(player.getUniqueId(), task);
    }

    private void renderCasinoFrame(Inventory inventory, String game, int tick, ThreadLocalRandom random) {
        double progress = tick / 40.0;
        int center = 13;
        switch (game) {
            case "roulette" -> {
                Material[] wheel = {Material.RED_CONCRETE, Material.BLACK_CONCRETE, Material.LIME_CONCRETE};
                for (int slot : new int[]{10, 11, 12, 14, 15, 16}) {
                    Material tile = wheel[random.nextInt(wheel.length)];
                    set(inventory, slot, tile, tile == Material.LIME_CONCRETE ? "&a0" : tile == Material.RED_CONCRETE ? "&cRED" : "&8BLACK");
                }
                set(inventory, center, Material.COMPASS, "&6&lROULETTE WHEEL", "&7Wheel spinning...", "&e" + Math.min(100, tick * 2) + "%");
            }
            case "drums", "slots" -> {
                Material[] symbols = {Material.DIAMOND, Material.NETHER_STAR, Material.GOLD_INGOT,
                        Material.EMERALD, Material.AMETHYST_SHARD, Material.FIREWORK_STAR};
                String[] names = {"GEM", "STAR", "GOLD", "CROWN", "SHARD", "FIRE"};
                int[] slots = {11, 13, 15};
                for (int slot : slots) {
                    int n = random.nextInt(symbols.length);
                    set(inventory, slot, symbols[n], "&e&l" + names[n], "&7Reel is spinning...");
                }
                set(inventory, 4, Material.JUKEBOX, "&6&lSLOT DRUMS", "&7Three reels in motion...");
            }
            case "darts" -> {
                set(inventory, 13, Material.TARGET, "&c&lDARTBOARD", "&7Aiming...", "&e" + Math.min(100, tick * 3) + "%");
                set(inventory, 11, Material.ARROW, "&fDart", "&7Flight animation");
                set(inventory, 15, Material.REDSTONE_TORCH, "&cTarget lock", "&7Bullseye calibration");
            }
            case "races" -> {
                for (int lane = 0; lane < 5; lane++) {
                    int slot = 10 + lane;
                    int distance = Math.min(4, (int)(progress * 5) + random.nextInt(0, 2));
                    set(inventory, slot, Material.RAIL, "&7Lane " + (lane + 1), "&f" + "▰".repeat(distance) + "&8" + "▱".repeat(4-distance));
                }
                set(inventory, 22, Material.SADDLE, "&6&lRACE IN PROGRESS", "&7Five lanes • finish approaching");
            }
            case "pyramid" -> {
                for (int i = 0; i < 3; i++) {
                    int slot = 11 + i * 2;
                    set(inventory, slot, (tick / 3 + i) % 3 == 0 ? Material.GOLD_BLOCK : Material.SANDSTONE,
                            "&6Door " + (i + 1), "&7The chambers are shifting...");
                }
                set(inventory, 22, Material.TORCH, "&e&lPYRAMID", "&7Ancient doors rotating...");
            }
            case "stairs" -> {
                for (int i = 0; i < 5; i++) {
                    boolean lit = i < Math.min(5, tick / 8);
                    set(inventory, 10 + i, lit ? Material.GOLD_BLOCK : Material.STONE_BRICKS,
                            lit ? "&aStep " + (i + 1) : "&7Step " + (i + 1), lit ? "&aClimbed" : "&8Waiting");
                }
                set(inventory, 22, Material.LADDER, "&6&lSTAIR CLIMB", "&7The path is being revealed...");
            }
            case "lottery" -> {
                for (int slot : new int[]{11, 13, 15}) set(inventory, slot, Material.PAPER,
                        "&e&l" + random.nextInt(1, 11), "&7Lottery ball");
                set(inventory, 22, Material.BARREL, "&6&lLOTTERY DRAW", "&7Drawing numbers...");
            }
            case "crash" -> {
                double x = 1.0 + progress * (1.0 + random.nextDouble() * 1.6);
                set(inventory, 13, Material.FIREWORK_ROCKET, "&a&l" + String.format(Locale.US, "%.2fx", x),
                        "&7Multiplier rising...", "&eTarget lock pending");
                set(inventory, 11, Material.LIME_STAINED_GLASS_PANE, "&a╱", "&7Live multiplier curve");
                set(inventory, 15, Material.REDSTONE_BLOCK, "&cCRASH RISK", "&7Watch the multiplier");
            }
            case "classic" -> {
                set(inventory, 13, random.nextBoolean() ? Material.GOLD_NUGGET : Material.IRON_NUGGET,
                        "&6&lCOIN FLIP", "&7Flipping...");
                set(inventory, 11, Material.GOLD_BLOCK, "&eHeads");
                set(inventory, 15, Material.IRON_BLOCK, "&fTails");
            }
            case "blackjack" -> {
                int a = random.nextInt(2, 12), b = random.nextInt(2, 12);
                int dealer = random.nextInt(2, 12);
                set(inventory, 11, Material.PAPER, "&fPlayer card", "&7" + (tick > 15 ? a : "?") );
                set(inventory, 13, Material.PAPER, "&fPlayer card", "&7" + (tick > 25 ? b : "?") );
                set(inventory, 15, Material.BLACK_BANNER, "&8Dealer card", "&7" + (tick > 32 ? dealer : "?") );
                set(inventory, 22, Material.CLOCK, "&6&lBLACKJACK", "&7Cards are being dealt...");
            }
            case "rps" -> {
                Material[] choices = {Material.COBBLESTONE, Material.PAPER, Material.SHEARS};
                String[] labels = {"ROCK", "PAPER", "SCISSORS"};
                int pick = random.nextInt(3);
                set(inventory, 11, choices[pick], "&e" + labels[pick], "&7Your choice");
                int cpu = random.nextInt(3);
                set(inventory, 15, choices[cpu], "&c" + labels[cpu], "&7Arcade choice");
                set(inventory, 13, Material.CLOCK, "&6&lDUEL", "&7Locking choices...");
            }
            case "tictactoe" -> {
                Material[] board = {Material.RED_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE};
                for (int slot : new int[]{10,11,12,19,20,21,28,29,30}) {
                    Material mark = board[random.nextInt(board.length)];
                    set(inventory, slot, mark, mark == board[0] ? "&cX" : "&bO", "&7Quick-match board preview");
                }
                set(inventory, 24, Material.OAK_SIGN, "&6&lTIC-TAC-TOE", "&7Opponent is thinking...");
            }
            case "poker" -> {
                Material[] cards = {Material.PAPER, Material.MAP, Material.FILLED_MAP, Material.WRITABLE_BOOK};
                for (int slot : new int[]{10,12,14,16}) set(inventory, slot, cards[random.nextInt(cards.length)],
                        "&fCard", "&7Hand reveal...");
                set(inventory, 22, Material.DIAMOND, "&b&lPOKER QUICK MATCH", "&7Comparing hands...");
            }
            default -> {
                set(inventory, center, Material.CLOCK, "&6&lGOOD LUCK", "&7Wager animation");
            }
        }
        if (tick >= 36) set(inventory, 22, Material.LIME_DYE, "&a&lRESOLVING", "&7Server is locking the result...");
    }

    private void cancelSpin(UUID playerId) {
        BukkitTask task = spinTasks.remove(playerId);
        if (task != null) task.cancel();
        spinTicks.remove(playerId);
    }

    private Inventory create(CasinoMenuHolder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, color(title));
        holder.inventory(inventory);
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName(" ");
        filler.setItemMeta(meta);
        for (int i = 0; i < size; i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == size / 9 - 1 || col == 0 || col == 8) inventory.setItem(i, filler.clone());
        }
        return inventory;
    }

    private void set(Inventory inventory, int slot, Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        if (lore.length > 0) {
            List<String> lines = new ArrayList<>();
            for (String line : lore) lines.add(color(line));
            meta.setLore(lines);
        }
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CasinoMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        if (holder.stage() == CasinoMenuHolder.Stage.SPIN) return;
        if (slot == 49 || slot == 35) { player.closeInventory(); return; }
        String action = holder.action(slot);
        if (action == null) return;
        if (action.startsWith("BET:")) {
            openMenu(player, Double.parseDouble(action.substring(4)));
        } else if (action.startsWith("GAME:")) {
            String game = action.substring(5);
            String configKey = switch (game) {
                case "tictactoe" -> "tictactoe-quick-match";
                case "poker" -> "poker-quick-match";
                default -> game;
            };
            if (!config.getBoolean("games." + configKey, false)) {
                player.sendMessage(color("&cThat game is disabled.")); return;
            }
            openChoices(player, game, holder.bet());
        } else if (action.equals("BACK")) {
            openMenu(player, holder.bet());
        } else if (action.startsWith("PLAY:")) {
            String choice = action.substring(5);
            player.closeInventory();
            startSpin(player, holder.game(), holder.bet(), choice);
        }
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CasinoMenuHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CasinoMenuHolder holder
                && holder.stage() == CasinoMenuHolder.Stage.SPIN) {
            UUID playerId = event.getPlayer().getUniqueId();
            // If a player deliberately closes a spin, it is cancelled before any wager is taken.
            if (spinTasks.containsKey(playerId)) cancelSpin(playerId);
        }
    }

    private Result play(String game, double bet, String choice) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return switch (game) {
            case "roulette" -> {
                requireChoice(choice, List.of("red", "black"), "Choose red or black.");
                int number = r.nextInt(37); boolean red = Set.of(1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36).contains(number);
                boolean win = number != 0 && (choice.equals("red") == red);
                yield new Result("Roulette landed on " + (number == 0 ? "GREEN 0" : (red ? "RED " : "BLACK ") + number) + ".", win ? bet * 1.95 : 0.0);
            }
            case "darts" -> {
                requireChoice(choice, List.of("high", "low", "bull"), "Choose high, low or bull.");
                int score = r.nextInt(1, 101);
                boolean win = choice.equals("bull") ? score >= 98 : choice.equals("high") ? score >= 51 : score <= 50;
                yield new Result("Dart score: " + score + ".", win ? bet * (choice.equals("bull") ? 32.0 : 1.90) : 0.0);
            }
            case "drums", "slots" -> {
                String[] symbols = {"GEM", "STAR", "CROWN", "BOLT", "COIN"};
                String a=symbols[r.nextInt(symbols.length)], b=symbols[r.nextInt(symbols.length)], c=symbols[r.nextInt(symbols.length)];
                double payout = a.equals(b) && b.equals(c) ? bet * 7.0 : a.equals(b) || b.equals(c) || a.equals(c) ? bet * 1.4 : 0.0;
                yield new Result("Reels: [" + a + "] [" + b + "] [" + c + "]", payout);
            }
            case "races" -> {
                int lane = parseBounded(choice, 1, 5, "Choose a lane from 1 to 5.");
                int winner = r.nextInt(1, 6);
                yield new Result("Race winner: lane " + winner + ". You chose lane " + lane + ".", lane == winner ? bet * 4.75 : 0.0);
            }
            case "pyramid" -> {
                int door = parseBounded(choice, 1, 3, "Choose pyramid door 1, 2 or 3.");
                int safe = r.nextInt(1, 4);
                yield new Result("The safe door was " + safe + ".", door == safe ? bet * 2.85 : 0.0);
            }
            case "stairs" -> {
                int steps = parseBounded(choice, 1, 5, "Choose 1 to 5 stairs.");
                int climbed = 0; for (int i=0;i<steps;i++) { if (r.nextDouble() < 0.75) climbed++; else break; }
                double payout = climbed == steps ? bet * Math.min(10.0, 0.96 / Math.pow(0.75, steps)) : 0.0;
                yield new Result("You climbed " + climbed + "/" + steps + " stairs.", payout);
            }
            case "lottery" -> {
                int guess = parseBounded(choice, 1, 10, "Choose a lottery number from 1 to 10.");
                int drawn = r.nextInt(1, 11);
                yield new Result("The lottery drew " + drawn + ".", guess == drawn ? bet * 9.5 : 0.0);
            }
            case "crash" -> {
                double target = parseDecimalBounded(choice, 1.10, 5.0, "Choose a cash-out target from 1.10 to 5.00.");
                double u = Math.max(0.000001, r.nextDouble());
                double crash = Math.min(20.0, Math.max(1.0, 0.99 / u));
                yield new Result(String.format(Locale.US, "Crash point: %.2fx (target %.2fx).", crash, target), crash >= target ? bet * target * 0.96 : 0.0);
            }
            case "classic" -> {
                requireChoice(choice, List.of("heads", "tails"), "Choose heads or tails.");
                boolean heads = r.nextBoolean();
                yield new Result("The coin landed " + (heads ? "heads" : "tails") + ".", choice.equals(heads ? "heads" : "tails") ? bet * 1.90 : 0.0);
            }
            case "blackjack" -> {
                int playerTotal = r.nextInt(2, 12) + r.nextInt(2, 12);
                while (playerTotal < 17) playerTotal += r.nextInt(2, 12);
                int dealerTotal = r.nextInt(2, 12) + r.nextInt(2, 12);
                while (dealerTotal < 17) dealerTotal += r.nextInt(2, 12);
                double payout = playerTotal > 21 ? 0.0 : dealerTotal > 21 || playerTotal > dealerTotal ? bet * 2.10 : playerTotal == dealerTotal ? bet : 0.0;
                yield new Result("Your total: " + playerTotal + " | Dealer: " + dealerTotal + ".", payout);
            }
            case "rockpaperscissors", "rps" -> {
                requireChoice(choice, List.of("rock", "paper", "scissors"), "Choose rock, paper or scissors.");
                String cpu = List.of("rock", "paper", "scissors").get(r.nextInt(3));
                boolean draw = choice.equals(cpu);
                boolean win = choice.equals("rock") && cpu.equals("scissors") || choice.equals("paper") && cpu.equals("rock") || choice.equals("scissors") && cpu.equals("paper");
                // 1/3 win at 1.85x gross + 1/3 push at 1.00x = 95% theoretical RTP.
                yield new Result("You: " + choice + " | Arcade: " + cpu + ".", win ? bet * 1.85 : draw ? bet : 0.0);
            }
            case "tictactoe" -> {
                // This command-mode quick match resolves against a random CPU result. The graphical board belongs to Phase 2.
                int roll = r.nextInt(100);
                double payout = roll < 30 ? bet * 2.70 : roll < 45 ? bet : 0.0;
                yield new Result("Tic-Tac-Toe quick match: " + (payout > bet ? "you won" : payout == bet ? "draw" : "arcade won") + ".", payout);
            }
            case "poker" -> {
                int playerHand = r.nextInt(1, 101), dealerHand = r.nextInt(1, 101);
                // A random 1..100 comparison is roughly 49.5% player wins; 1.90x gross
                // keeps the simplified quick-match close to a 5% theoretical edge.
                double payout = playerHand > dealerHand ? bet * 1.90 : playerHand == dealerHand ? bet : 0.0;
                yield new Result("Hand strength: you " + playerHand + " / dealer " + dealerHand + ".", payout);
            }
            default -> null;
        };
    }

    private void showOdds(Player player) {
        player.sendMessage(color("&6&lGASMP ARCADE ODDS &8- &7Virtual Shards only"));
        player.sendMessage(color("&7Roulette: 18/37 red or black, pays 1.95x gross (~94.9% RTP)."));
        player.sendMessage(color("&7Slots: triples 7.00x, pairs 1.40x gross."));
        player.sendMessage(color("&7Races: 1/5, pays 4.75x. Lottery: 1/10, pays 9.50x."));
        player.sendMessage(color("&7Darts high/low: 50%, 1.90x gross; bull: 3%, 32.00x gross."));
        player.sendMessage(color("&7RPS win: 1/3 at 1.85x; tie returns the bet (95% theoretical RTP). Poker quick-match: about 49.5%, 1.90x."));
        player.sendMessage(color("&7Pyramid: 1/3 at 2.85x. Crash target: dynamic 96% payout factor. Blackjack is a simplified auto-hit simulation."));
        player.sendMessage(color("&eAll listed games are enabled. Poker and Tic-Tac-Toe remain simplified quick-match score modes."));
        player.sendMessage(color("&eUse /casino challenge <player> <game> <shards> for player-versus-player Arcade duels."));
        player.sendMessage(color("&cNo real-money deposit, purchase or cash-out is supported."));
    }

    private void help(Player player) {
        player.sendMessage(color("&6&lGASMP ARCADE"));
        player.sendMessage(color("&e/casino menu &7- open the animated arcade GUI and choose a game"));
        player.sendMessage(color("&e/casino roulette <bet> <red|black>"));
        player.sendMessage(color("&e/casino drums <bet> &7- 3 reels"));
        player.sendMessage(color("&e/casino darts <bet> <high|low|bull>"));
        player.sendMessage(color("&e/casino races <bet> <lane 1-5>"));
        player.sendMessage(color("&e/casino pyramid <bet> <door 1-3>"));
        player.sendMessage(color("&e/casino stairs <bet> <steps 1-5>"));
        player.sendMessage(color("&e/casino lottery <bet> <number 1-10>"));
        player.sendMessage(color("&e/casino crash <bet> <target 1.10-5.00>"));
        player.sendMessage(color("&e/casino classic <bet> <heads|tails>"));
        player.sendMessage(color("&e/casino blackjack <bet>"));
        player.sendMessage(color("&e/casino rps <bet> <rock|paper|scissors>"));
        player.sendMessage(color("&e/casino tictactoe <bet> &7- simplified quick-match simulation, not a board game"));
        player.sendMessage(color("&e/casino poker <bet> &7- simplified hand-strength quick match, not full poker"));
        player.sendMessage(color("&e/casino odds &7- view selected payout rules"));
        player.sendMessage(color("&e/casino challenge <player> <game> <shards> &7- challenge another player"));
        player.sendMessage(color("&e/casino accept|decline &7- respond to a challenge"));
        player.sendMessage(color("&e/casino duel &7- reopen your current multiplayer match"));
        player.sendMessage(color("&dShards are virtual server currency. No real-money deposits or cash-out."));
    }

    private void requireChoice(String choice, List<String> allowed, String message) {
        if (!allowed.contains(choice)) throw new IllegalArgumentException(message);
    }
    private int parseBounded(String value, int min, int max, String error) {
        try { int n = Integer.parseInt(value); if (n < min || n > max) throw new NumberFormatException(); return n; }
        catch (NumberFormatException ex) { throw new IllegalArgumentException(error); }
    }
    private double parseDecimalBounded(String value, double min, double max, String error) {
        try { double n = Double.parseDouble(value); if (!Double.isFinite(n) || n < min || n > max) throw new NumberFormatException(); return n; }
        catch (NumberFormatException ex) { throw new IllegalArgumentException(error); }
    }
    private String money(double value) { return String.format(Locale.US, "%.0f", value); }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("roulette","darts","drums","races","pyramid","stairs","lottery","crash","classic","blackjack","rps","tictactoe","poker","challenge","accept","decline","duel","odds","help");
        if (args.length == 3) return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "roulette" -> List.of("red", "black"); case "darts" -> List.of("high", "low", "bull");
            case "races" -> List.of("1", "2", "3", "4", "5"); case "pyramid" -> List.of("1", "2", "3");
            case "stairs" -> List.of("1", "2", "3", "4", "5"); case "lottery" -> List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
            case "crash" -> List.of("1.25", "1.50", "2.00", "3.00", "5.00");
            case "classic" -> List.of("heads", "tails"); case "rps" -> List.of("rock", "paper", "scissors"); default -> List.of();
        };
        return List.of();
    }
}
