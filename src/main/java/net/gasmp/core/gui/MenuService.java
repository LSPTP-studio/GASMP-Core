package net.gasmp.core.gui;

import net.gasmp.core.GASMPCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class MenuService implements Listener {

    private static final int MENU_SIZE = 54;
    private static final int BACK_SLOT = 49;

    private final GASMPCore plugin;

    public MenuService(GASMPCore plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        if (!plugin.getConfig().getBoolean("menus.enabled", true)) {
            player.sendMessage(color("&cGASMP menus are currently disabled."));
            return;
        }
        open(player, MenuPage.MAIN);
    }

    public void open(Player player, MenuPage page) {
        if (page == MenuPage.SHOP) {
            open(player, MenuPage.KEY_SHOP);
            return;
        }
        if (page == MenuPage.DECORATIONS) {
            plugin.getDecorationShopService().openCategories(player);
            return;
        }
        // Route the GASMP Casino tile into the real Vault-backed arcade GUI.
        // The previous page only displayed disabled placeholder buttons.
        if (page == MenuPage.CASINO) {
            Bukkit.dispatchCommand(player, "casino menu");
            return;
        }
        if (!plugin.getConfig().getBoolean("menus.enabled", true)) {
            player.sendMessage(color("&cGASMP menus are currently disabled."));
            return;
        }

        MenuHolder holder = new MenuHolder(page);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, ScreenUi.title(screenFor(page), titleFor(page)));
        holder.setInventory(inventory);
        fillFrame(inventory, page);

        switch (page) {
            case MAIN -> buildMain(holder, inventory);
            case SHOP -> buildShop(holder, inventory);
            case STATISTICS -> buildStatistics(holder, inventory, player);
            case SHARDS -> buildShards(holder, inventory);
            case KEY_SHOP -> buildKeyShop(holder, inventory);
            case GAME_WORLDS -> buildGameWorlds(holder, inventory);
            case CASINO -> buildCasino(holder, inventory);
            case PROFILE -> buildProfile(holder, inventory, player);
            case SETTINGS -> buildSettings(holder, inventory);
        }

        if (page != MenuPage.MAIN) {
            set(inventory, BACK_SLOT, Material.ARROW, "&f&lBack", "&7Return to the GASMP main menu");
            holder.navigate(BACK_SLOT, page == MenuPage.KEY_SHOP ? MenuPage.SHARDS : MenuPage.MAIN);
        }

        player.openInventory(inventory);
        if (page == MenuPage.SHARDS) refreshShards(player, holder);
        if (page == MenuPage.PROFILE) refreshProfile(player, holder);
    }

    private void buildMain(MenuHolder holder, Inventory inventory) {
        set(inventory, 10, Material.ENDER_CHEST, "&d&lSHOP", "&7Equipment crate keys and rewards", "&eClick to open");
        holder.navigate(10, MenuPage.KEY_SHOP);

        set(inventory, 12, Material.WRITABLE_BOOK, "&d&lStatistics", "&7View your server statistics", "&eClick to open");
        holder.navigate(12, MenuPage.STATISTICS);

        set(inventory, 14, Material.ARMOR_STAND, "&9&lDecorations", "&7Browse cosmetic decorations", "&eClick to open");
        holder.navigate(14, MenuPage.DECORATIONS);

        set(inventory, 20, Material.ENDER_PEARL, "&b&lRandom Teleport", "&7Find a safe wilderness location", "&eClick to use /rtp");
        holder.notice(20, "&bUse /rtp to find a safe wilderness location.");
        set(inventory, 22, Material.FIREWORK_ROCKET, "&6&lGame Worlds", "&7Paintball, Go Karts and Skatepark", "&eClick to choose a game world");
        holder.navigate(22, MenuPage.GAME_WORLDS);
        set(inventory, 24, Material.TARGET, "&6&lBounties", "&7Place and claim player bounties", "&eUse /bounty");
        holder.notice(24, "&6Use /bounty place <player> <amount> or /bounty top.");

        set(inventory, 28, Material.AMETHYST_SHARD, "&d&lShards", "&7Your secondary GASMP currency", "&eClick to open");
        holder.navigate(28, MenuPage.SHARDS);

        set(inventory, 30, Material.FIREWORK_ROCKET, "&6&lCasino", "&7Arcade-style mini-games", "&eClick to view games");
        holder.navigate(30, MenuPage.CASINO);

        set(inventory, 32, Material.PLAYER_HEAD, "&a&lProfile", "&7Your player overview", "&eClick to open");
        holder.navigate(32, MenuPage.PROFILE);

        set(inventory, 34, Material.COMPARATOR, "&f&lSettings", "&7Personal menu preferences", "&eClick to open");
        holder.navigate(34, MenuPage.SETTINGS);

        set(inventory, 49, Material.BARRIER, "&c&lClose", "&7Close this menu");
    }

    private void buildShop(MenuHolder holder, Inventory inventory) {
        // The SHOP page is redirected to ShopService.open() before this builder is reached.
        // Keep a safe fallback in case a future refactor changes that routing.
        set(inventory, 22, Material.AMETHYST_SHARD, "&d&lShard Shop", "&7The main Shop button opens this catalogue.");
        holder.notice(22, "&eUse the main-menu Shop button to open the Shard Shop.");
    }

    private void buildStatistics(MenuHolder holder, Inventory inventory, Player player) {
        int kills = player.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = player.getStatistic(Statistic.DEATHS);
        long playTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long totalMinutes = playTicks / 20L / 60L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;

        set(inventory, 20, Material.IRON_SWORD, "&d&lPlayer Kills", "&f" + kills + " kills recorded");
        set(inventory, 22, Material.SKELETON_SKULL, "&d&lDeaths", "&f" + deaths + " deaths recorded");
        set(inventory, 24, Material.CLOCK, "&d&lPlaytime", "&f" + hours + "h " + minutes + "m played");
        set(inventory, 31, Material.PLAYER_HEAD, "&f&l" + player.getName(), "&7Live vanilla statistics", "&7UUID: " + player.getUniqueId());
    }

    private void buildShards(MenuHolder holder, Inventory inventory) {
        set(inventory, 22, Material.AMETHYST_SHARD, "&d&lGASMP SHARDS", "&7Loading your balance...");
        set(inventory, 31, Material.BOOK, "&f&lHOW TO EARN SHARDS",
                "&7Play the GASMP Arcade, win chat games,",
                "&7join events and complete server activities.",
                "&8Use the single Shop button in the main menu to spend them.");
    }

    private void buildKeyShop(MenuHolder holder, Inventory inventory) {
        String[] ids = {"normal", "medium", "premium"};
        Material[] icons = {Material.SHULKER_BOX, Material.ENDER_CHEST, Material.PURPLE_SHULKER_BOX};
        int[] slots = {11, 13, 15};
        String[] descriptions = {
                "&7Common cosmetic collectibles",
                "&7Wings, plushies and Feral Fantasy",
                "&7The rarest cosmetic drop pool"
        };
        for (int i = 0; i < ids.length; i++) {
            long price = plugin.getConfig().getLong("key-shop.prices." + ids[i], -1L);
            String priceText = price < 0 ? "&cNot for sale" : "&d" + price + " ✦";
            set(inventory, slots[i], icons[i], "&f&l" + ids[i].toUpperCase(java.util.Locale.ROOT) + " COSMETIC KEY",
                    descriptions[i], "&7Price: " + priceText, "&eClick to purchase");
        }
        set(inventory, 22, Material.BOOK, "&f&lKEY DROP SYSTEM",
                "&7All three crates award cosmetic collectibles.",
                "&7Higher tiers have better odds for rare cosmetics.",
                "&8Earn Shards in the GASMP Arcade.");
    }

    private void buildGameWorlds(MenuHolder holder, Inventory inventory) {
        gameWorldItem(holder, inventory, 20, Material.SNOWBALL, "Paintball Arena",
                "Teleport to the Paintball arena", "mv teleport paintball");
        gameWorldItem(holder, inventory, 22, Material.MINECART, "Go-Kart Arena",
                "Generate arena 1 and enter its course", "kart arena generate 1");
        gameWorldItem(holder, inventory, 24, Material.SLIME_BLOCK, "Skatepark",
                "Teleport to the Skatepark", "mv teleport skatepark");
    }

    private void gameWorldItem(MenuHolder holder, Inventory inventory, int slot, Material icon,
                               String title, String hint, String command) {
        set(inventory, slot, icon, "&b&l" + title, "&7" + hint, "&eClick to run /" + command);
        holder.command(slot, command);
    }

    private String screenFor(MenuPage page) {
        return switch (page) {
            case MAIN -> "main_menu";
            case SHOP, KEY_SHOP -> "shard_shop";
            case STATISTICS -> "statistics";
            case DECORATIONS -> "decorations";
            case SHARDS -> "shards";
            case GAME_WORLDS -> "adventure";
            case CASINO -> "casino";
            case PROFILE -> "profile";
            case SETTINGS -> "settings";
        };
    }

    private void refreshShards(Player player, MenuHolder holder) {
        plugin.getDataStore().getShards(player.getUniqueId()).thenAccept(balance ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (player.getOpenInventory().getTopInventory().getHolder() != holder) return;
                    holder.getInventory().setItem(22, item(Material.AMETHYST_SHARD,
                            "&d&lGASMP Shards", "&7Your current balance:",
                            "&d&l" + balance + " ✦ Shards",
                            "&8Earn and spend Shards around GASMP."));
                })
        ).exceptionally(error -> {
            plugin.getLogger().warning("Could not refresh Shards menu: " + error.getMessage());
            return null;
        });
    }

    private void buildCasino(MenuHolder holder, Inventory inventory) {
        Material icon = Material.FIREWORK_ROCKET;
        String[] names = {"Roulette", "Darts", "Drums", "Races", "Pyramid", "Stairs", "Lottery", "Crash", "Classic", "Blackjack", "Rock Paper Scissors", "Tic-Tac-Toe", "Poker"};
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24};
        for (int i = 0; i < names.length; i++) {
            set(inventory, slots[i], icon, "&6&l" + names[i], "&7Casino game", "&cGame engine is not enabled yet.");
        }
        set(inventory, 31, Material.BOOK, "&f&lCasino rules", "&7Virtual server currency only.", "&7No real-money deposits or cash-out.", "&7Published rules and odds before launch.");
        for (int slot : slots) {
            holder.notice(slot, "&eCasino games are listed for planning only; wagering is disabled.");
        }
        holder.notice(31, "&eCasino launch will require published odds, wager limits and transaction logging.");
    }

    private void buildProfile(MenuHolder holder, Inventory inventory, Player player) {
        set(inventory, 22, Material.PLAYER_HEAD, "&a&l" + player.getName(), "&7Your GASMP profile", "&7Kills: &f" + player.getStatistic(Statistic.PLAYER_KILLS), "&7Deaths: &f" + player.getStatistic(Statistic.DEATHS));
        set(inventory, 31, Material.AMETHYST_SHARD, "&d&lShards Wallet", "&7Loading your saved balance...");
    }

    private void refreshProfile(Player player, MenuHolder holder) {
        plugin.getDataStore().getShards(player.getUniqueId()).thenAccept(balance ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (player.getOpenInventory().getTopInventory().getHolder() != holder) return;
                    holder.getInventory().setItem(31, item(Material.AMETHYST_SHARD,
                            "&d&lShards Wallet", "&7Your saved GASMP balance:",
                            "&d&l" + balance + " ✦ Shards",
                            "&8Coins are managed by the server economy provider."));
                })
        ).exceptionally(error -> {
            plugin.getLogger().warning("Could not refresh player profile wallet: " + error.getMessage());
            return null;
        });
    }

    private void buildSettings(MenuHolder holder, Inventory inventory) {
        set(inventory, 22, Material.COMPARATOR, "&f&lMenu Settings", "&7Personal toggles and preferences", "&8Settings storage is not connected yet.");
        set(inventory, 31, Material.PAPER, "&7&lAccessibility", "&7Readable names and vanilla item icons", "&7are kept as the Bedrock-safe fallback.");
        holder.notice(22, "&ePersonal settings will be added in a later milestone.");
        holder.notice(31, "&aGASMP menus do not depend on custom fonts or item models.");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        int slot = event.getRawSlot();
        String command = holder.commandAt(slot);
        if (command != null) {
            player.performCommand(command);
            return;
        }
        if (holder.getPage() == MenuPage.KEY_SHOP) {
            String crateId = switch (slot) {
                case 11 -> "normal";
                case 13 -> "medium";
                case 15 -> "premium";
                default -> null;
            };
            if (crateId != null) {
                plugin.getCrateService().buyKey(player, crateId);
                return;
            }
        }
        if (holder.getPage() == MenuPage.MAIN && slot == 49) {
            player.closeInventory();
            return;
        }

        MenuPage target = holder.navigationAt(slot);
        if (target != null) {
            open(player, target);
            return;
        }

        String notice = holder.noticeAt(slot);
        if (notice != null) {
            player.sendMessage(color("&d[GASMP] " + notice));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    private void fillFrame(Inventory inventory, MenuPage page) {
        Material frame = switch (page) {
            case MAIN, STATISTICS, PROFILE -> Material.MAGENTA_STAINED_GLASS_PANE;
            case SHOP, DECORATIONS -> Material.BLUE_STAINED_GLASS_PANE;
            case SHARDS, KEY_SHOP -> Material.PURPLE_STAINED_GLASS_PANE;
            case GAME_WORLDS -> Material.BLUE_STAINED_GLASS_PANE;
            case CASINO -> Material.ORANGE_STAINED_GLASS_PANE;
            case SETTINGS -> Material.BLACK_STAINED_GLASS_PANE;
        };
        ItemStack filler = item(frame, " ");
        for (int slot = 0; slot < MENU_SIZE; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == 5 || column == 0 || column == 8) {
                inventory.setItem(slot, filler.clone());
            }
        }
    }

    private void set(Inventory inventory, int slot, Material material, String name, String... lore) {
        inventory.setItem(slot, item(material, name, lore));
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore.length > 0) {
                List<String> lines = new ArrayList<>(lore.length);
                for (String line : lore) {
                    lines.add(color(line));
                }
                meta.setLore(lines);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String titleFor(MenuPage page) {
        String custom = plugin.getConfig().getString("menus.main-menu-title", "&d&lGASMP &8• &5Menu");
        return switch (page) {
            case MAIN -> color(custom);
            case SHOP -> color("&d&lGASMP &8• &5Shard Shop");
            case STATISTICS -> color("&d&lGASMP &8• &5Statistics");
            case DECORATIONS -> color("&9&lGASMP &8• &3Decorations");
            case SHARDS -> color("&d&lGASMP &8• &5Shards");
            case KEY_SHOP -> color("&d&lGASMP &8• &5Shard Shop");
            case GAME_WORLDS -> color("&b&lGASMP &8• &3Game Worlds");
            case CASINO -> color("&6&lGASMP &8• &eCasino");
            case PROFILE -> color("&a&lGASMP &8• &2Profile");
            case SETTINGS -> color("&f&lGASMP &8• &7Settings");
        };
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
