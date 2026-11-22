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
        if (page == MenuPage.SHARD_SHOP) {
            plugin.getShardShopService().open(player, 0);
            return;
        }
        if (page == MenuPage.CASINO) {
            plugin.getCasinoService().openGui(player);
            return;
        }
        if (!plugin.getConfig().getBoolean("menus.enabled", true)) {
            player.sendMessage(color("&cGASMP menus are currently disabled."));
            return;
        }

        MenuHolder holder = new MenuHolder(page);
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, titleFor(page));
        holder.setInventory(inventory);
        fillFrame(inventory, page);

        switch (page) {
            case MAIN -> buildMain(holder, inventory);
            case STATISTICS -> buildStatistics(holder, inventory, player);
            case DECORATIONS -> buildDecorations(holder, inventory);
            case MUSIC -> buildMusic(holder, inventory);
            case SHARDS -> buildShards(holder, inventory);
            case SHARD_SHOP -> { }
            case UTILITY -> buildUtility(holder, inventory);
            case CASINO -> buildCasino(holder, inventory);
            case PROFILE -> buildProfile(holder, inventory, player);
            case SETTINGS -> buildSettings(holder, inventory);
        }

        if (page != MenuPage.MAIN) {
            set(inventory, BACK_SLOT, Material.ARROW, "&f&lBack", "&7Return to the GASMP main menu");
            holder.navigate(BACK_SLOT, MenuPage.MAIN);
        }

        player.openInventory(inventory);
        if (page == MenuPage.SHARDS) refreshShards(player, holder);
        if (page == MenuPage.PROFILE) refreshProfile(player, holder);
    }

    private void buildMain(MenuHolder holder, Inventory inventory) {
        set(inventory, 4, Material.NETHER_STAR, "&d&lGASMP NETWORK",
                "&7Survival  &8•  &dShards  &8•  &6Arcade",
                "&8Your server, your progression.");
        set(inventory, 11, Material.AMETHYST_CLUSTER, "&d&lShard Shop",
                "&7Furniture, decor and exclusive rewards",
                "&7Browse the GASMP rewards catalogue", "&eClick to open");
        holder.navigate(11, MenuPage.SHARD_SHOP);
        set(inventory, 13, Material.FIREWORK_ROCKET, "&6&lArcade",
                "&7Animated GASMP mini-games", "&eClick to open");
        holder.navigate(13, MenuPage.CASINO);
        set(inventory, 15, Material.COMPASS, "&b&lAdventure",
                "&7Teleport, teams, bounties and auctions", "&eClick to open");
        holder.navigate(15, MenuPage.UTILITY);
        set(inventory, 21, Material.ARMOR_STAND, "&9&lDecorations",
                "&7Furniture kits and building decor", "&eClick to browse");
        holder.navigate(21, MenuPage.DECORATIONS);
        set(inventory, 23, Material.PLAYER_HEAD, "&a&lProfile",
                "&7Your player overview and Shards wallet", "&eClick to open");
        holder.navigate(23, MenuPage.PROFILE);
        set(inventory, 25, Material.WRITABLE_BOOK, "&d&lStatistics",
                "&7Kills, deaths and playtime", "&eClick to open");
        holder.navigate(25, MenuPage.STATISTICS);
        set(inventory, 33, Material.CHEST, "&6&lCrate Catalogue",
                "&7Browse crate rewards and drop chances",
                "&7Opening requires a matching physical key", "&eClick to browse");
        holder.command(33, "crate");
        set(inventory, 31, Material.NETHER_STAR, "&d&lRanks",
                "&7Browse Staff, Creator, Supporter and Prestige ranks",
                "&7Rank groups and prefixes are managed by LuckPerms", "&eClick to browse");
        holder.command(31, "ranks");
        set(inventory, 49, Material.BARRIER, "&c&lClose", "&7Close this menu");
    }

    
    private void buildUtility(MenuHolder holder, Inventory inventory) {
        set(inventory, 11, Material.ENDER_PEARL, "&b&lRandom Teleport",
                "&7Search for a safe wilderness location", "&eClick to run /rtp");
        holder.command(11, "rtp");
        set(inventory, 13, Material.TARGET, "&6&lBounties",
                "&7Place a bounty or view the leaderboard", "&eClick to run /bounty");
        holder.command(13, "bounty");
        set(inventory, 15, Material.SHIELD, "&a&lTeams",
                "&7Create a team, invite players and manage homes", "&eClick to run /team");
        holder.command(15, "team");
        set(inventory, 21, Material.CHEST, "&e&lAuction House",
                "&7List, buy and collect player items", "&eClick to run /ah");
        holder.command(21, "ah");
        set(inventory, 23, Material.ENCHANTED_BOOK, "&d&lBattle Pass",
                "&7View season progress and claim rewards", "&eClick to run /battlepass");
        holder.command(23, "battlepass");
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

    private void buildDecorations(MenuHolder holder, Inventory inventory) {
        set(inventory, 13, Material.ARMOR_STAND, "&9&lFurniture Kits",
                "&7Starter, modern and garden build bundles",
                "&7Purchase with Shards", "&eClick to browse");
        holder.navigate(13, MenuPage.SHARD_SHOP);
        set(inventory, 22, Material.PAINTING, "&b&lWall Decor",
                "&7Frames, paintings and display pieces",
                "&7Browse the themed kits in the Shard Shop");
        holder.navigate(22, MenuPage.SHARD_SHOP);
        set(inventory, 31, Material.BOOK, "&f&lAbout Furniture",
                "&7Starter kits use vanilla building pieces.",
                "&7Animated custom furniture needs RP Engine assets.");
        holder.notice(31, "&7Vanilla kits work without a resource-pack plugin; animated custom models are a separate asset pack.");
    }

    private void buildMusic(MenuHolder holder, Inventory inventory) {
        set(inventory, 13, Material.JUKEBOX, "&5&lGASMP Music Player", "&7Retro player-inspired menu", "&8No licensed tracks have been configured.");
        set(inventory, 29, Material.LIME_DYE, "&aPlay", "&7Playback controls will be added later.");
        set(inventory, 31, Material.GRAY_DYE, "&cStop", "&7Playback controls will be added later.");
        set(inventory, 33, Material.NOTE_BLOCK, "&dTrack Library", "&7Only original or properly licensed audio.");
        for (int slot : new int[]{29, 31, 33}) {
            holder.notice(slot, "&eThe music player is a preview. No tracks or playback engine are configured.");
        }
    }

    private void buildShards(MenuHolder holder, Inventory inventory) {
        set(inventory, 22, Material.AMETHYST_SHARD, "&d&lSHARD WALLET", "&7Loading your balance...");
        set(inventory, 15, Material.AMETHYST_CLUSTER, "&d&lOpen Shard Shop",
                "&7Browse furniture, decor and rewards", "&eClick to open");
        holder.navigate(15, MenuPage.SHARD_SHOP);
        set(inventory, 31, Material.BOOK, "&f&lAbout Shards",
                "&7GASMP's progression and reward currency.",
                "&7Earn Shards through server activities.",
                "&7Spend them on furniture and cosmetics.");
        holder.notice(31, "&dShards are GASMP's reward currency, separate from Vault Coins.");
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
        holder.navigate(31, MenuPage.SHARDS);
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
        if (holder.getPage() == MenuPage.MAIN && slot == 49) {
            player.closeInventory();
            return;
        }

        MenuPage target = holder.navigationAt(slot);
        if (target != null) {
            open(player, target);
            return;
        }

        String command = holder.commandAt(slot);
        if (command != null) {
            player.closeInventory();
            player.performCommand(command);
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
        Material accent = switch (page) {
            case MAIN, PROFILE, STATISTICS -> Material.MAGENTA_STAINED_GLASS_PANE;
            case DECORATIONS -> Material.BLUE_STAINED_GLASS_PANE;
            case MUSIC, SHARDS -> Material.PURPLE_STAINED_GLASS_PANE;
            case SHARD_SHOP -> Material.PURPLE_STAINED_GLASS_PANE;
            case UTILITY -> Material.CYAN_STAINED_GLASS_PANE;
            case CASINO -> Material.ORANGE_STAINED_GLASS_PANE;
            case SETTINGS -> Material.BLACK_STAINED_GLASS_PANE;
        };
        Material secondary = page == MenuPage.MAIN || page == MenuPage.PROFILE
                ? Material.PURPLE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
        ItemStack dark = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int column = 0; column < 9; column++) {
            if (column == 0 || column == 8) {
                inventory.setItem(column, dark.clone());
                inventory.setItem(45 + column, dark.clone());
            } else {
                inventory.setItem(column, item(column % 2 == 0 ? accent : secondary, " "));
                inventory.setItem(45 + column, item(column % 2 == 0 ? secondary : accent, " "));
            }
        }
        for (int row = 1; row < 5; row++) {
            inventory.setItem(row * 9, dark.clone());
            inventory.setItem(row * 9 + 8, dark.clone());
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
            case STATISTICS -> color("&d&lGASMP &8• &5Statistics");
            case DECORATIONS -> color("&9&lGASMP &8• &3Decorations");
            case MUSIC -> color("&5&lGASMP &8• &dMusic Player");
            case SHARDS -> color("&d&lGASMP &8• &5Shard Wallet");
            case SHARD_SHOP -> color("&d&lGASMP &8• &5Shard Shop");
            case UTILITY -> color("&b&lGASMP &8• &3Adventure");
            case CASINO -> color("&6&lGASMP &8• &eCasino");
            case PROFILE -> color("&a&lGASMP &8• &2Profile");
            case SETTINGS -> color("&f&lGASMP &8• &7Settings");
        };
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
