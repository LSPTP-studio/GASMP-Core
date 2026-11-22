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
import net.gasmp.core.combat.CombatService;
import net.gasmp.core.crates.CrateService;
import net.gasmp.core.crates.CrateMenuService;
import net.gasmp.core.data.PlayerDataStore;
import net.gasmp.core.data.PlayerProfileListener;
import net.gasmp.core.economy.VaultEconomyBridge;
import net.gasmp.core.gui.MenuService;
import net.gasmp.core.hologram.HologramService;
import net.gasmp.core.maintenance.MaintenanceService;
import net.gasmp.core.moderation.ModerationService;
import net.gasmp.core.rank.RankAdminMenu;
import net.gasmp.core.rank.RankMenuService;
import net.gasmp.core.rank.RankService;
import net.gasmp.core.rtp.RtpService;
import net.gasmp.core.shardshop.ShardShopService;
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
    private ShardShopService shardShopService;
    private CombatService combatService;
    private RtpService rtpService;
    private AuctionService auctionService;
    private BattlePassService battlePassService;
    private CasinoService casinoService;
    private CrateService crateService;
    private TeamService teamService;
    private MaintenanceService maintenanceService;
    private ModerationService moderationService;
    private HologramService hologramService;
    private ChatGameService chatGameService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String resource : new String[]{
                "ranks.yml", "rtp.yml", "casino.yml", "battlepass.yml",
                "crates.yml", "chat-games.yml", "maintenance.yml", "holograms.yml", "shard-shop.yml"
        }) saveIfMissing(resource);

        dataStore = new PlayerDataStore(new File(getDataFolder(), getConfig().getString("storage.sqlite-file", "data.db")));
        try {
            dataStore.initialize().join();
        } catch (CompletionException exception) {
            getLogger().severe("SQLite initialization failed: " + rootMessage(exception));
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

        shardShopService = new ShardShopService(this);
        getServer().getPluginManager().registerEvents(shardShopService, this);
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
        CrateMenuService crateMenuService = new CrateMenuService(this);
        getServer().getPluginManager().registerEvents(crateMenuService, this);
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
        bind("shardshop", shardShopService);
        bind("rtp", new RtpCommand(this));
        bind("bounty", bountyService);
        bind("gvanish", vanishService);
        bind("ah", auctionService);
        bind("casino", casinoService);
        bind("battlepass", battlePassService);
        bind("crates", crateService);
        bind("crate", crateMenuService);
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
        RankMenuService playerRankMenu = new RankMenuService(this);
        getServer().getPluginManager().registerEvents(playerRankMenu, this);
        bind("ranks", playerRankMenu);
        RankAdminMenu rankMenu = new RankAdminMenu(this, rankService);
        getServer().getPluginManager().registerEvents(rankMenu, this);
        bind("gasmpadmin", rankMenu);
        rankService.initialize();

        if (getConfig().getBoolean("modules.chat-games", true)) chatGameService.startScheduler();

        getLogger().info("GASMP Core v" + getPluginMeta().getVersion() + " enabled.");
        getLogger().info("Phase 1 active: profiles, Shards, Vault economy integrations, RTP, combat logging, bounties, auction house, battle pass, crates, teams, moderation, maintenance, holograms, tool skills, chat games, casino (disabled by default), leaderboards, performance snapshot and staff utilities.");
        if (!BukkitCompatibleVault()) getLogger().warning("Vault economy unavailable; Coins features are disabled until a provider registers Economy.class.");
    }

    private boolean BukkitCompatibleVault() {
        return economyBridge != null && economyBridge.isAvailable();
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
    public ShardShopService getShardShopService() { return shardShopService; }
    public CasinoService getCasinoService() { return casinoService; }
    public CombatService getCombatService() { return combatService; }
    public RtpService getRtpService() { return rtpService; }

    public void reloadCore() {
        reloadConfig();
        if (shardShopService != null) shardShopService.reload();
        if (rankService != null) {
            rankService.loadConfiguration();
            rankService.initialize();
        }
        if (economyBridge != null) economyBridge.refresh();
        if (auctionService != null) { /* auction behavior reads values from live config */ }
        if (casinoService != null) casinoService.reload();
        if (battlePassService != null) battlePassService.reload();
        if (crateService != null) crateService.reload();
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
