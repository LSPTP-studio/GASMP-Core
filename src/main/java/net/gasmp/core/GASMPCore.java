package net.gasmp.core;

import net.gasmp.core.auction.AuctionService;
import net.gasmp.core.battlepass.BattlePassService;
import net.gasmp.core.bounty.BountyService;
import net.gasmp.core.casino.CasinoService;
import net.gasmp.core.chatgame.ChatGameService;
import net.gasmp.core.command.CoreCommand;
import net.gasmp.core.command.LeaderboardCommand;
import net.gasmp.core.command.PerformanceCommand;
import net.gasmp.core.command.RtpCommand;
import net.gasmp.core.command.ShardCommand;
import net.gasmp.core.command.ShopCommand;
import net.gasmp.core.combat.CombatService;
import net.gasmp.core.crates.CrateService;
import net.gasmp.core.data.PlayerDataStore;
import net.gasmp.core.data.PlayerProfileListener;
import net.gasmp.core.economy.VaultEconomyBridge;
import net.gasmp.core.gui.MenuService;
import net.gasmp.core.hologram.HologramService;
import net.gasmp.core.maintenance.MaintenanceService;
import net.gasmp.core.moderation.ModerationService;
import net.gasmp.core.rank.RankAdminMenu;
import net.gasmp.core.rank.RankService;
import net.gasmp.core.rtp.RtpService;
import net.gasmp.core.shop.ShopService;
import net.gasmp.core.shop.DecorationShopService;
import net.gasmp.core.integration.ItemsAdderBridge;
import net.gasmp.core.skills.ToolSkillsService;
import net.gasmp.core.staff.CommandRestrictionService;
import net.gasmp.core.staff.StaffAccess;
import net.gasmp.core.staff.VanishService;
import net.gasmp.core.team.TeamService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.CompletionException;

public final class GASMPCore extends JavaPlugin {
    private MenuService menuService;
    private PlayerDataStore dataStore;
    private RankService rankService;
    private VaultEconomyBridge economyBridge;
    private ShopService shopService;
    private DecorationShopService decorationShopService;
    private CombatService combatService;
    private RtpService rtpService;
    private AuctionService auctionService;
    private BattlePassService battlePassService;
    private CasinoService casinoService;
    private CrateService crateService;
    private ItemsAdderBridge itemsAdderBridge;
    private TeamService teamService;
    private MaintenanceService maintenanceService;
    private ModerationService moderationService;
    private HologramService hologramService;
    private ChatGameService chatGameService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateUserSettings();
        for (String resource : new String[]{
                "ranks.yml", "shop.yml", "rtp.yml", "casino.yml", "battlepass.yml",
                "crates.yml", "decoration-shop.yml",
                "chat-games.yml", "maintenance.yml", "holograms.yml"
        }) saveIfMissing(resource);

        dataStore = new PlayerDataStore(new File(getDataFolder(), getConfig().getString("storage.sqlite-file", "data.db")));
        try {
            dataStore.initialize().join();
        } catch (CompletionException exception) {
            getLogger().severe("SQLite initialization failed: " + rootMessage(exception));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        itemsAdderBridge = new ItemsAdderBridge(this);
        if (!itemsAdderBridge.isAvailable()) {
            getLogger().severe("ItemsAdder is required for GASMP ItemsAdder Edition. Install ItemsAdder 4.x and restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        economyBridge = new VaultEconomyBridge();
        economyBridge.refresh();
        if (economyBridge.isAvailable()) {
            getLogger().info("Vault Economy provider: " + economyBridge.currencyNamePlural());
        } else {
            getLogger().warning("No Vault Economy provider found. Coins systems will remain unavailable until one is installed.");
        }

        // LuckPerms is a soft dependency and is normally loaded before this plugin.
        StaffAccess staffAccess = new StaffAccess();

        shopService = new ShopService(this);
        getServer().getPluginManager().registerEvents(shopService, this);
        combatService = new CombatService(this);
        getServer().getPluginManager().registerEvents(combatService, this);
        rtpService = new RtpService(this, combatService);
        getServer().getPluginManager().registerEvents(rtpService, this);

        BountyService bountyService = new BountyService(this);
        getServer().getPluginManager().registerEvents(bountyService, this);
        VanishService vanishService = new VanishService(this, staffAccess);
        getServer().getPluginManager().registerEvents(vanishService, this);

        auctionService = new AuctionService(this);
        getServer().getPluginManager().registerEvents(auctionService, this);
        auctionService.startExpiryTask();
        battlePassService = new BattlePassService(this);
        getServer().getPluginManager().registerEvents(battlePassService, this);
        casinoService = new CasinoService(this);
        getServer().getPluginManager().registerEvents(casinoService, this);
        crateService = new CrateService(this, staffAccess);
        getServer().getPluginManager().registerEvents(crateService, this);
        decorationShopService = new DecorationShopService(this);
        getServer().getPluginManager().registerEvents(decorationShopService, this);
        teamService = new TeamService(this);

        maintenanceService = new MaintenanceService(this, staffAccess);
        getServer().getPluginManager().registerEvents(maintenanceService, this);
        moderationService = new ModerationService(this, staffAccess);
        getServer().getPluginManager().registerEvents(moderationService, this);
        hologramService = new HologramService(this, staffAccess);
        ToolSkillsService toolSkillsService = new ToolSkillsService(this);
        getServer().getPluginManager().registerEvents(toolSkillsService, this);
        CommandRestrictionService restrictionService = new CommandRestrictionService(this, staffAccess);
        getServer().getPluginManager().registerEvents(restrictionService, this);
        chatGameService = new ChatGameService(this, staffAccess);
        getServer().getPluginManager().registerEvents(chatGameService, this);

        menuService = new MenuService(this);
        getServer().getPluginManager().registerEvents(menuService, this);
        getServer().getPluginManager().registerEvents(new PlayerProfileListener(this), this);

        CoreCommand coreCommand = new CoreCommand(this);
        bind("gasmp", coreCommand);
        bind("gasmpcore", coreCommand);
        bind("shards", new ShardCommand(this));
        bind("shop", new ShopCommand(this));
        bind("rtp", new RtpCommand(this));
        bind("bounty", bountyService);
        bind("gvanish", vanishService);
        bind("ah", auctionService);
        bind("casino", casinoService);
        bind("battlepass", battlePassService);
        bind("crates", crateService);
        bind("gcrateopen", crateService);
        bind("keyall", crateService);
        bind("team", teamService);
        bind("gmaintenance", maintenanceService);
        bind("gban", moderationService);
        bind("gunban", moderationService);
        bind("gmute", moderationService);
        bind("gunmute", moderationService);
        bind("gwarn", moderationService);
        bind("gkick", moderationService);
        bind("ghistory", moderationService);
        bind("gholo", hologramService);
        bind("gchatgame", chatGameService);
        bind("gtop", new LeaderboardCommand(this));
        bind("gperf", new PerformanceCommand());

        rankService = new RankService(this);
        RankAdminMenu rankMenu = new RankAdminMenu(this, rankService);
        getServer().getPluginManager().registerEvents(rankMenu, this);
        bind("gasmpadmin", rankMenu);
        rankService.initialize();

        if (getConfig().getBoolean("modules.chat-games", true)) chatGameService.startScheduler();

        getLogger().info("GASMP Core v" + getPluginMeta().getVersion() + " enabled.");
        getLogger().info("GASMP ItemsAdder Edition active: profiles, Shards, Vault Coins, decorations, custom cosmetic crates, casino, ranks, chat games, teams and staff tools.");
        if (!BukkitCompatibleVault()) getLogger().warning("Vault economy unavailable; Coins features are disabled until a provider registers Economy.class.");
    }

    private boolean BukkitCompatibleVault() {
        return economyBridge != null && economyBridge.isAvailable();
    }

    private void migrateUserSettings() {
        // GASMP now uses Shards for the Shard Shop and Arcade. Keep existing
        // server config files, but add the tier prices and enable the requested arcade.
        getConfig().set("modules.casino", true);
        getConfig().set("key-shop.enabled", true);
        getConfig().addDefault("key-shop.prices.normal", 100L);
        getConfig().addDefault("key-shop.prices.medium", 300L);
        getConfig().addDefault("key-shop.prices.premium", 800L);
        if (!getConfig().contains("key-shop.prices.normal")) getConfig().set("key-shop.prices.normal", 100L);
        if (!getConfig().contains("key-shop.prices.medium")) getConfig().set("key-shop.prices.medium", 300L);
        if (!getConfig().contains("key-shop.prices.premium")) getConfig().set("key-shop.prices.premium", 800L);
        getConfig().set("game-worlds.paintball-command", "mv teleport paintball");
        getConfig().set("game-worlds.gokarts-command", "kart arena generate 1");
        getConfig().set("game-worlds.skatepark-command", "mv teleport skatepark");
        saveConfig();
    }

    private void saveIfMissing(String name) {
        if (!new File(getDataFolder(), name).exists()) saveResource(name, false);
    }

    private void bind(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Missing command in plugin.yml: " + name);
        command.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter completer) command.setTabCompleter(completer);
    }

    @Override
    public void onDisable() {
        if (chatGameService != null) chatGameService.stopAll();
        if (auctionService != null) auctionService.shutdown();
        if (dataStore != null) dataStore.close();
        getLogger().info("GASMP Core disabled.");
    }

    public MenuService getMenuService() { return menuService; }
    public PlayerDataStore getDataStore() { return dataStore; }
    public RankService getRankService() { return rankService; }
    public VaultEconomyBridge getEconomyBridge() { return economyBridge; }
    public ShopService getShopService() { return shopService; }
    public DecorationShopService getDecorationShopService() { return decorationShopService; }
    public CrateService getCrateService() { return crateService; }
    public ItemsAdderBridge getItemsAdderBridge() { return itemsAdderBridge; }
    public CombatService getCombatService() { return combatService; }
    public RtpService getRtpService() { return rtpService; }

    public void reloadCore() {
        reloadConfig();
        if (shopService != null) shopService.reload();
        if (rankService != null) {
            rankService.loadConfiguration();
            rankService.initialize();
        }
        if (economyBridge != null) economyBridge.refresh();
        if (auctionService != null) { /* auction behavior reads values from live config */ }
        if (casinoService != null) casinoService.reload();
        if (battlePassService != null) battlePassService.reload();
        if (crateService != null) crateService.reload();
        if (decorationShopService != null) decorationShopService.reload();
        if (maintenanceService != null) maintenanceService.reload();
        if (hologramService != null) hologramService.reload();
        if (chatGameService != null) { chatGameService.reload(); chatGameService.startScheduler(); }
        getLogger().info("GASMP Core configuration reloaded.");
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
