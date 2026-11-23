package net.gasmp.core.casino;

import net.gasmp.core.GASMPCore;
import net.gasmp.core.data.PlayerDataStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-versus-one GASMP Arcade challenges. Both players stake the same amount
 * of Shards; the winner receives the two-player pot and ties/timeouts refund
 * each stake. No real-money payment or cash-out path exists.
 */
public final class CasinoDuelService implements Listener {
    private static final int[] OPTION_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18};
    private static final int[] FRAME_SLOTS = {0, 1, 2, 3, 5, 6, 7, 8, 18, 19, 20, 21, 23, 24, 25, 26};

    private static final class Invite {
        final UUID id = UUID.randomUUID();
        final UUID challenger;
        final UUID target;
        final String challengerName;
        final String targetName;
        final String game;
        final long bet;
        BukkitTask expiry;

        Invite(Player challenger, Player target, String game, long bet) {
            this.challenger = challenger.getUniqueId();
            this.target = target.getUniqueId();
            this.challengerName = challenger.getName();
            this.targetName = target.getName();
            this.game = game;
            this.bet = bet;
        }
    }

    private static final class Duel {
        final UUID id = UUID.randomUUID();
        final UUID first;
        final UUID second;
        final String firstName;
        final String secondName;
        final String game;
        final long bet;
        final Map<UUID, String> choices = new HashMap<>();
        BukkitTask timeout;
        BukkitTask animation;
        boolean resolving;
        boolean closed;
        int animationTick;

        Duel(Invite invite) {
            first = invite.challenger;
            second = invite.target;
            firstName = invite.challengerName;
            secondName = invite.targetName;
            game = invite.game;
            bet = invite.bet;
        }

        UUID other(UUID player) { return first.equals(player) ? second : first; }
        String name(UUID player) { return first.equals(player) ? firstName : secondName; }
    }

    private final GASMPCore plugin;
    private final CasinoService casino;
    private final Map<UUID, Invite> inviteByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> inviteById = new ConcurrentHashMap<>();
    private final Map<UUID, Duel> duelByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Duel> duelById = new ConcurrentHashMap<>();
    private final Set<UUID> processingPlayers = ConcurrentHashMap.newKeySet();

    public CasinoDuelService(GASMPCore plugin, CasinoService casino) {
        this.plugin = plugin;
        this.casino = casino;
    }

    public boolean isBusy(UUID playerId) {
        return inviteByPlayer.containsKey(playerId)
                || duelByPlayer.containsKey(playerId)
                || processingPlayers.contains(playerId);
    }

    public boolean handle(Player player, String[] args) {
        if (args.length == 0) return false;
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "challenge" -> challenge(player, args);
            case "accept" -> accept(player, args);
            case "decline" -> decline(player);
            case "duel" -> reopen(player);
            default -> false;
        };
    }

    private boolean challenge(Player challenger, String[] args) {
        if (args.length != 4) {
            challenger.sendMessage(color("&e/casino challenge <player> <game> <shards>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            challenger.sendMessage(color("&cThat player is not online."));
            return true;
        }
        if (target.equals(challenger)) {
            challenger.sendMessage(color("&cYou cannot challenge yourself."));
            return true;
        }
        String game = casino.canonicalGame(args[2]);
        if (!casino.isGameEnabled(game)) {
            challenger.sendMessage(color("&cThat Arcade game is disabled or unknown."));
            return true;
        }
        long bet;
        try {
            bet = Long.parseLong(args[3]);
        } catch (NumberFormatException ex) {
            challenger.sendMessage(color("&cEnter a whole-number Shard stake."));
            return true;
        }
        if (bet < casino.minimumBet() || bet > casino.maximumBet()) {
            challenger.sendMessage(color("&cStake must be between " + casino.minimumBet()
                    + " and " + casino.maximumBet() + " Shards."));
            return true;
        }
        if (isBusy(challenger.getUniqueId()) || isBusy(target.getUniqueId())) {
            challenger.sendMessage(color("&cYou or that player already has a pending invitation or Arcade match."));
            return true;
        }

        Invite invite = new Invite(challenger, target, game, bet);
        inviteByPlayer.put(challenger.getUniqueId(), invite);
        inviteByPlayer.put(target.getUniqueId(), invite);
        inviteById.put(invite.id, invite);
        invite.expiry = Bukkit.getScheduler().runTaskLater(plugin, () -> expireInvite(invite), 20L * 60L);
        openInvite(target, invite);
        challenger.sendMessage(color("&d&lGASMP ARCADE &8» &fChallenge sent to &d" + target.getName()
                + "&f: &e" + game + " &7(" + bet + " Shards each)."));
        target.sendMessage(color("&d&lGASMP ARCADE &8» &f" + challenger.getName()
                + " challenged you to &e" + game + "&f for &d" + bet + " Shards each."));
        return true;
    }

    private boolean accept(Player target, String[] args) {
        Invite invite = inviteByPlayer.get(target.getUniqueId());
        if (invite == null || !invite.target.equals(target.getUniqueId())) {
            target.sendMessage(color("&cYou have no pending Arcade challenge."));
            return true;
        }
        if (args.length == 2 && !invite.challengerName.equalsIgnoreCase(args[1])) {
            target.sendMessage(color("&cThat challenge is not from " + args[1] + "."));
            return true;
        }
        startAcceptedInvite(target, invite);
        return true;
    }

    private boolean decline(Player target) {
        Invite invite = inviteByPlayer.get(target.getUniqueId());
        if (invite == null || !invite.target.equals(target.getUniqueId())) {
            target.sendMessage(color("&cYou have no pending Arcade challenge."));
            return true;
        }
        clearInvite(invite);
        target.closeInventory();
        target.sendMessage(color("&7You declined the Arcade challenge."));
        Player challenger = Bukkit.getPlayer(invite.challenger);
        if (challenger != null) challenger.sendMessage(color("&c" + target.getName() + " declined your Arcade challenge."));
        return true;
    }

    private void startAcceptedInvite(Player target, Invite invite) {
        Player challenger = Bukkit.getPlayer(invite.challenger);
        if (challenger == null || !challenger.isOnline()) {
            clearInvite(invite);
            target.sendMessage(color("&cThe challenger went offline."));
            return;
        }
        clearInvite(invite);
        target.closeInventory();
        processingPlayers.add(invite.challenger);
        processingPlayers.add(invite.target);
        challenger.sendMessage(color("&7Locking both " + invite.bet + " Shard stakes..."));
        target.sendMessage(color("&7Locking both " + invite.bet + " Shard stakes..."));

        plugin.getDataStore().changeShards(invite.challenger, invite.challengerName, invite.challenger,
                "ARCADE_DUEL_WAGER", invite.bet, PlayerDataStore.ShardChange.TAKE,
                "GASMP Arcade duel: " + invite.game)
                .whenComplete((firstBalance, firstError) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (firstError != null) {
                        processingPlayers.remove(invite.challenger);
                        processingPlayers.remove(invite.target);
                        challenger.sendMessage(color("&cCould not reserve your stake. Check your Shards balance."));
                        target.sendMessage(color("&cThe challenge could not start."));
                        return;
                    }
                    Player targetForSecondDebit = Bukkit.getPlayer(invite.target);
                    if (targetForSecondDebit == null || !targetForSecondDebit.isOnline()) {
                        refundStake(invite.challenger, invite.challengerName, invite.bet,
                                "Refund: duel opponent disconnected before stake reservation.");
                        processingPlayers.remove(invite.challenger);
                        processingPlayers.remove(invite.target);
                        challenger.sendMessage(color("&cYour opponent went offline. Your stake is being refunded."));
                        return;
                    }
                    plugin.getDataStore().changeShards(invite.target, invite.targetName, invite.target,
                            "ARCADE_DUEL_WAGER", invite.bet, PlayerDataStore.ShardChange.TAKE,
                            "GASMP Arcade duel: " + invite.game)
                            .whenComplete((secondBalance, secondError) -> Bukkit.getScheduler().runTask(plugin, () -> {
                                if (secondError != null) {
                                    refundStake(invite.challenger, invite.challengerName, invite.bet,
                                            "Refund: opponent could not pay duel stake.");
                                    processingPlayers.remove(invite.challenger);
                                    processingPlayers.remove(invite.target);
                                    challenger.sendMessage(color("&cYour opponent could not pay the stake. Your Shards are being refunded."));
                                    Player targetForMessage = Bukkit.getPlayer(invite.target);
                                    if (targetForMessage != null) targetForMessage.sendMessage(color("&cYou do not have enough Shards to accept this duel."));
                                    return;
                                }
                                Player currentChallenger = Bukkit.getPlayer(invite.challenger);
                                Player currentTarget = Bukkit.getPlayer(invite.target);
                                if (currentChallenger == null || currentTarget == null
                                        || !currentChallenger.isOnline() || !currentTarget.isOnline()) {
                                    refundStake(invite.challenger, invite.challengerName, invite.bet,
                                            "Refund: player disconnected as duel was starting.");
                                    refundStake(invite.target, invite.targetName, invite.bet,
                                            "Refund: player disconnected as duel was starting.");
                                    processingPlayers.remove(invite.challenger);
                                    processingPlayers.remove(invite.target);
                                    return;
                                }
                                Duel duel = new Duel(invite);
                                duelById.put(duel.id, duel);
                                duelByPlayer.put(duel.first, duel);
                                duelByPlayer.put(duel.second, duel);
                                processingPlayers.remove(duel.first);
                                processingPlayers.remove(duel.second);
                                duel.timeout = Bukkit.getScheduler().runTaskLater(plugin,
                                        () -> refundDuel(duel, "The duel timed out before both players locked in."),
                                        20L * 90L);
                                openMatch(currentChallenger, duel);
                                openMatch(currentTarget, duel);
                                currentChallenger.sendMessage(color("&aDuel started! Choose your move in the Arcade menu."));
                                currentTarget.sendMessage(color("&aDuel started! Choose your move in the Arcade menu."));
                            }));
                }));
    }

    private void openInvite(Player target, Invite invite) {
        CasinoDuelHolder holder = new CasinoDuelHolder(CasinoDuelHolder.Stage.INVITE, invite.id, target.getUniqueId());
        Inventory inventory = create(holder, 27, "&8GASMP • Arcade Challenge");
        set(inventory, 4, Material.FIREWORK_ROCKET, "&6&lARCADE CHALLENGE",
                "&fFrom: &e" + invite.challengerName,
                "&fGame: &e" + invite.game,
                "&fStake: &d" + invite.bet + " Shards each",
                "&7Winner takes both stakes; tie returns both.");
        set(inventory, 11, Material.LIME_CONCRETE, "&a&lACCEPT", "&7Reserve your Shards and play.");
        set(inventory, 15, Material.RED_CONCRETE, "&c&lDECLINE", "&7Decline this challenge.");
        holder.action(11, "ACCEPT");
        holder.action(15, "DECLINE");
        target.openInventory(inventory);
    }

    private void openMatch(Player player, Duel duel) {
        if (duel.closed || !player.isOnline()) return;
        CasinoDuelHolder holder = new CasinoDuelHolder(CasinoDuelHolder.Stage.MATCH, duel.id, player.getUniqueId());
        Inventory inventory = create(holder, 27, "&8GASMP • " + duel.game.toUpperCase(Locale.ROOT) + " DUEL");
        UUID opponentId = duel.other(player.getUniqueId());
        String ownChoice = duel.choices.get(player.getUniqueId());
        String otherChoice = duel.choices.get(opponentId);
        set(inventory, 4, Material.GOLD_INGOT, "&6&l" + duel.game.toUpperCase(Locale.ROOT) + " DUEL",
                "&fOpponent: &e" + duel.name(opponentId),
                "&fStake: &d" + duel.bet + " Shards each",
                "&fYou: " + (ownChoice == null ? "&eChoose a move" : "&aLocked in"),
                "&fOpponent: " + (otherChoice == null ? "&7Choosing..." : "&aLocked in"),
                "&8Both moves are resolved together.");
        List<String> choices = choicesFor(duel.game);
        if (choices.isEmpty()) {
            set(inventory, 13, ownChoice == null ? Material.LIME_CONCRETE : Material.LIME_DYE,
                    ownChoice == null ? "&a&lLOCK IN" : "&a&lREADY",
                    ownChoice == null ? "&7Lock your entry for this game." : "&7Your entry is locked.");
            if (ownChoice == null) holder.action(13, "PICK:ready");
        } else {
            for (int i = 0; i < choices.size() && i < OPTION_SLOTS.length; i++) {
                String choice = choices.get(i);
                int slot = OPTION_SLOTS[i];
                boolean picked = choice.equals(ownChoice);
                set(inventory, slot, picked ? Material.LIME_DYE : iconFor(duel.game, choice),
                        (picked ? "&a&lSELECTED " : "&e") + choice.toUpperCase(Locale.ROOT),
                        picked ? "&7Your move is locked." : "&7Click to lock this move.");
                if (ownChoice == null) holder.action(slot, "PICK:" + choice);
            }
        }
        set(inventory, 22, Material.PLAYER_HEAD, ownChoice == null ? "&e&lWAITING FOR YOUR MOVE" : "&a&lMOVE LOCKED",
                otherChoice == null ? "&7Opponent has not locked in yet." : "&aOpponent is ready.");
        set(inventory, 26, Material.BARRIER, "&c&lCLOSE", "&7You can reopen this match with /casino duel.");
        player.openInventory(inventory);
    }

    private void beginAnimation(Duel duel) {
        if (duel.closed || duel.resolving) return;
        duel.resolving = true;
        if (duel.timeout != null) duel.timeout.cancel();
        Player first = Bukkit.getPlayer(duel.first);
        Player second = Bukkit.getPlayer(duel.second);
        if (first == null || second == null) {
            refundDuel(duel, "A player disconnected before the result could be shown.");
            return;
        }
        openAnimation(first, duel);
        openAnimation(second, duel);
        duel.animation = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (duel.closed) {
                if (duel.animation != null) duel.animation.cancel();
                return;
            }
            duel.animationTick++;
            Material[] frames = {Material.PURPLE_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE,
                    Material.AMETHYST_SHARD, Material.FIREWORK_STAR, Material.GOLD_INGOT};
            Material frame = frames[(duel.animationTick / 2) % frames.length];
            Player a = Bukkit.getPlayer(duel.first);
            Player b = Bukkit.getPlayer(duel.second);
            if (a != null && a.isOnline()) updateAnimation(a, duel, frame);
            if (b != null && b.isOnline()) updateAnimation(b, duel, frame);
            if (duel.animationTick % 5 == 0) {
                if (a != null) a.playSound(a.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 0.8f + duel.animationTick * 0.025f);
                if (b != null) b.playSound(b.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 0.8f + duel.animationTick * 0.025f);
            }
            if (duel.animationTick >= 30) {
                if (duel.animation != null) duel.animation.cancel();
                settleDuel(duel);
            }
        }, 0L, 2L);
    }

    private void openAnimation(Player player, Duel duel) {
        CasinoDuelHolder holder = new CasinoDuelHolder(CasinoDuelHolder.Stage.ANIMATION, duel.id, player.getUniqueId());
        Inventory inventory = create(holder, 27, "&8GASMP • Duel Resolving");
        set(inventory, 4, Material.CLOCK, "&6&l" + duel.game.toUpperCase(Locale.ROOT),
                "&7Both moves are locked.",
                "&7The Arcade is resolving the match...");
        set(inventory, 13, Material.AMETHYST_SHARD, "&d&lVS", "&7" + duel.firstName + " &8vs &7" + duel.secondName);
        set(inventory, 22, Material.FIREWORK_STAR, "&e&lGOOD LUCK", "&7Result reveal in progress");
        player.openInventory(inventory);
    }

    private void updateAnimation(Player player, Duel duel, Material frame) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof CasinoDuelHolder holder)
                || holder.stage() != CasinoDuelHolder.Stage.ANIMATION || !holder.referenceId().equals(duel.id)) return;
        Inventory inventory = player.getOpenInventory().getTopInventory();
        inventory.setItem(13, named(frame, "&d&lVS", "&7" + duel.firstName + " &8vs &7" + duel.secondName));
        inventory.setItem(22, named(duel.animationTick < 20 ? Material.CLOCK : Material.FIREWORK_STAR,
                duel.animationTick < 20 ? "&e&lROLLING..." : "&a&lREVEALING...",
                "&7" + Math.min(100, duel.animationTick * 100 / 30) + "%"));
    }

    private void settleDuel(Duel duel) {
        if (duel.closed) return;
        int result;
        String detail;
        String firstChoice = duel.choices.getOrDefault(duel.first, "ready");
        String secondChoice = duel.choices.getOrDefault(duel.second, "ready");
        if (duel.game.equals("rps")) {
            result = compareRps(firstChoice, secondChoice);
            detail = duel.firstName + ": " + firstChoice + " • " + duel.secondName + ": " + secondChoice;
        } else {
            double firstScore = casino.rollDuelScore(duel.game, firstChoice);
            double secondScore = casino.rollDuelScore(duel.game, secondChoice);
            result = Double.compare(firstScore, secondScore);
            detail = duel.firstName + " score: " + formatScore(firstScore)
                    + " • " + duel.secondName + " score: " + formatScore(secondScore);
        }
        finishDuel(duel, result, detail);
    }

    private void finishDuel(Duel duel, int result, String detail) {
        if (duel.closed) return;
        duel.closed = true;
        removeDuel(duel);
        if (result == 0) {
            CompletableFuture<Long> firstRefund = refundStake(duel.first, duel.firstName, duel.bet, "Tie refund: " + duel.game);
            CompletableFuture<Long> secondRefund = refundStake(duel.second, duel.secondName, duel.bet, "Tie refund: " + duel.game);
            audit(duel.first, duel.firstName, duel.game, duel.bet, duel.bet, "PUSH");
            audit(duel.second, duel.secondName, duel.game, duel.bet, duel.bet, "PUSH");
            notifyDuel(duel, "&e&lDRAW", detail + ". Both " + duel.bet + " Shard stakes were returned.");
            firstRefund.exceptionally(error -> { plugin.getLogger().severe("Duel tie refund failed for " + duel.firstName + ": " + root(error)); return null; });
            secondRefund.exceptionally(error -> { plugin.getLogger().severe("Duel tie refund failed for " + duel.secondName + ": " + root(error)); return null; });
            return;
        }
        UUID winner = result > 0 ? duel.first : duel.second;
        UUID loser = duel.other(winner);
        long pot = Math.multiplyExact(duel.bet, 2L);
        plugin.getDataStore().changeShards(winner, duel.name(winner), null,
                "ARCADE_DUEL_REWARD", pot, PlayerDataStore.ShardChange.GIVE,
                "Won GASMP Arcade duel: " + duel.game)
                .whenComplete((balance, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        refundStake(duel.first, duel.firstName, duel.bet, "Duel settlement fallback refund.");
                        refundStake(duel.second, duel.secondName, duel.bet, "Duel settlement fallback refund.");
                        plugin.getLogger().severe("Duel payout failed; both stakes are being refunded: " + root(error));
                        notifyDuel(duel, "&c&lPAYOUT ERROR", "The winner could not be paid. Both stakes are being refunded.");
                        return;
                    }
                    audit(winner, duel.name(winner), duel.game, duel.bet, pot, "WIN");
                    audit(loser, duel.name(loser), duel.game, duel.bet, 0L, "LOSS");
                    notifyDuel(duel, "&a&l" + duel.name(winner).toUpperCase(Locale.ROOT) + " WINS",
                            detail + ". Winner collected " + pot + " Shards.");
                }));
    }

    private void refundDuel(Duel duel, String reason) {
        if (duel.closed) return;
        duel.closed = true;
        removeDuel(duel);
        refundStake(duel.first, duel.firstName, duel.bet, reason);
        refundStake(duel.second, duel.secondName, duel.bet, reason);
        notifyDuel(duel, "&e&lDUEL CANCELLED", reason + " Both stakes are being returned.");
        plugin.getLogger().info("Cancelled GASMP duel " + duel.id + ": " + reason);
    }

    private void removeDuel(Duel duel) {
        if (duel.timeout != null) duel.timeout.cancel();
        if (duel.animation != null) duel.animation.cancel();
        duelById.remove(duel.id);
        duelByPlayer.remove(duel.first, duel);
        duelByPlayer.remove(duel.second, duel);
    }

    private void expireInvite(Invite invite) {
        if (!inviteById.containsKey(invite.id)) return;
        clearInvite(invite);
        Player challenger = Bukkit.getPlayer(invite.challenger);
        Player target = Bukkit.getPlayer(invite.target);
        if (challenger != null) challenger.sendMessage(color("&cYour Arcade challenge expired."));
        if (target != null) target.sendMessage(color("&cThe Arcade challenge expired."));
    }

    private void clearInvite(Invite invite) {
        if (invite.expiry != null) invite.expiry.cancel();
        inviteById.remove(invite.id);
        inviteByPlayer.remove(invite.challenger, invite);
        inviteByPlayer.remove(invite.target, invite);
    }

    private boolean reopen(Player player) {
        Invite invite = inviteByPlayer.get(player.getUniqueId());
        if (invite != null && invite.target.equals(player.getUniqueId())) {
            openInvite(player, invite);
            return true;
        }
        Duel duel = duelByPlayer.get(player.getUniqueId());
        if (duel == null) {
            player.sendMessage(color("&cYou do not have a pending challenge or active duel."));
            return true;
        }
        if (duel.resolving) openAnimation(player, duel);
        else openMatch(player, duel);
        return true;
    }

    private void notifyDuel(Duel duel, String headline, String detail) {
        for (UUID id : List.of(duel.first, duel.second)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.sendMessage(color("&6&lGASMP ARCADE &8» " + headline));
                player.sendMessage(color("&7" + detail));
            }
        }
    }

    private CompletableFuture<Long> refundStake(UUID playerId, String playerName, long amount, String detail) {
        return plugin.getDataStore().changeShards(playerId, playerName, null,
                "ARCADE_DUEL_REFUND", amount, PlayerDataStore.ShardChange.GIVE, detail);
    }

    private void audit(UUID playerId, String playerName, String game, long bet, long payout, String outcome) {
        plugin.getDataStore().recordCasinoTransaction(playerId, playerName, game, bet, payout, outcome)
                .exceptionally(error -> {
                    plugin.getLogger().warning("Arcade duel audit failed for " + playerName + ": " + root(error));
                    return null;
                });
    }

    private int compareRps(String first, String second) {
        if (first.equals(second)) return 0;
        if ((first.equals("rock") && second.equals("scissors"))
                || (first.equals("paper") && second.equals("rock"))
                || (first.equals("scissors") && second.equals("paper"))) return 1;
        return -1;
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

    private Material iconFor(String game, String choice) {
        if (game.equals("roulette")) return choice.equals("red") ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
        if (game.equals("rps")) return switch (choice) {
            case "rock" -> Material.COBBLESTONE;
            case "paper" -> Material.PAPER;
            default -> Material.SHEARS;
        };
        if (game.equals("classic")) return choice.equals("heads") ? Material.GOLD_NUGGET : Material.IRON_NUGGET;
        if (game.equals("darts")) return choice.equals("bull") ? Material.TARGET : Material.ARROW;
        if (game.equals("crash")) return Material.FIREWORK_ROCKET;
        return Material.PAPER;
    }

    private String formatScore(double score) {
        return String.format(Locale.US, "%.2f", score);
    }

    private Inventory create(CasinoDuelHolder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, color(title));
        holder.inventory(inventory);
        ItemStack filler = named(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int slot : FRAME_SLOTS) {
            if (slot < size && inventory.getItem(slot) == null) inventory.setItem(slot, filler.clone());
        }
        return inventory;
    }

    private void set(Inventory inventory, int slot, Material material, String name, String... lore) {
        inventory.setItem(slot, named(material, name, lore));
    }

    private ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore.length > 0) meta.setLore(Arrays.stream(lore).map(this::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CasinoDuelHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (!holder.viewerId().equals(player.getUniqueId())) return;
        String action = holder.action(event.getRawSlot());
        if (holder.stage() == CasinoDuelHolder.Stage.INVITE) {
            Invite invite = inviteById.get(holder.referenceId());
            if (invite == null) {
                player.sendMessage(color("&cThat challenge has expired."));
            } else if ("ACCEPT".equals(action)) {
                startAcceptedInvite(player, invite);
            } else if ("DECLINE".equals(action)) {
                decline(player);
            }
            return;
        }
        if (holder.stage() != CasinoDuelHolder.Stage.MATCH || action == null || !action.startsWith("PICK:")) return;
        Duel duel = duelById.get(holder.referenceId());
        if (duel == null || duel.closed || duel.resolving || !duelByPlayer.containsKey(player.getUniqueId())) return;
        if (duel.choices.containsKey(player.getUniqueId())) return;
        duel.choices.put(player.getUniqueId(), action.substring(5));
        Player first = Bukkit.getPlayer(duel.first);
        Player second = Bukkit.getPlayer(duel.second);
        if (duel.choices.containsKey(duel.first) && duel.choices.containsKey(duel.second)) {
            if (first != null) first.playSound(first.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.5f);
            if (second != null) second.playSound(second.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.5f);
            beginAnimation(duel);
        } else {
            if (first != null) openMatch(first, duel);
            if (second != null) openMatch(second, duel);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CasinoDuelHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Invite invite = inviteByPlayer.get(playerId);
        if (invite != null) {
            clearInvite(invite);
            UUID otherId = invite.challenger.equals(playerId) ? invite.target : invite.challenger;
            Player other = Bukkit.getPlayer(otherId);
            if (other != null) other.sendMessage(color("&cThe Arcade challenge was cancelled because a player disconnected."));
        }
        Duel duel = duelByPlayer.get(playerId);
        if (duel != null && !duel.closed) {
            refundDuel(duel, event.getPlayer().getName() + " disconnected. The match was cancelled.");
        }
    }

    private String root(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
}
