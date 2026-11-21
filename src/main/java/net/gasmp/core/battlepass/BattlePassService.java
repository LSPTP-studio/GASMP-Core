package net.gasmp.core.battlepass;

import net.gasmp.core.GASMPCore;
import net.gasmp.core.data.PlayerDataStore;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BattlePassService implements Listener, CommandExecutor, TabCompleter {
    private final GASMPCore plugin;
    private YamlConfiguration config;
    private String seasonId;
    private int xpPerLevel;
    private int maxLevel;
    private final Set<UUID> rewardBusy = ConcurrentHashMap.newKeySet();

    public BattlePassService(GASMPCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "battlepass.yml");
        if (!file.exists()) plugin.saveResource("battlepass.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        seasonId = config.getString("season.id", "season-1");
        xpPerLevel = Math.max(1, config.getInt("progression.xp-per-level", 100));
        maxLevel = Math.max(1, Math.min(100, config.getInt("progression.max-level", 50)));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Battle Pass is a player command."); return true; }
        if (!plugin.getConfig().getBoolean("modules.battle-pass", true)) {
            player.sendMessage(color("&cThe Battle Pass is disabled.")); return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("claim")) {
            int level;
            try { level = Integer.parseInt(args[1]); }
            catch (NumberFormatException ex) { player.sendMessage(color("&cInvalid level.")); return true; }
            claim(player, level); return true;
        }
        showProgress(player);
        return true;
    }

    private void showProgress(Player player) {
        plugin.getDataStore().getBattlePassXp(player.getUniqueId(), seasonId).thenAccept(xp ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    int level = Math.min(maxLevel, (int) (xp / xpPerLevel));
                    long into = xp % xpPerLevel;
                    player.sendMessage(color("&d&lGASMP BATTLE PASS &8- &f" + config.getString("season.name", "Season One")));
                    player.sendMessage(color("&7Season: &f" + seasonId + " &8| &7Level: &d" + level + "/" + maxLevel));
                    player.sendMessage(color("&7XP: &f" + xp + " &8(" + into + "/" + xpPerLevel + " to next level)"));
                    player.sendMessage(color("&e/battlepass claim <level> &7- claim an unlocked milestone reward"));
                })).exceptionally(error -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) player.sendMessage(color("&cCould not load Battle Pass progress."));
                    });
                    return null;
                });
    }

    public void award(Player player, long amount, String source) {
        if (amount <= 0 || !plugin.getConfig().getBoolean("modules.battle-pass", true)) return;
        plugin.getDataStore().addBattlePassXp(player.getUniqueId(), player.getName(), seasonId, amount)
                .thenAccept(total -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    int level = Math.min(maxLevel, (int) (total / xpPerLevel));
                    int previous = Math.min(maxLevel, (int) (Math.max(0, total - amount) / xpPerLevel));
                    if (level > previous) {
                        player.sendMessage(color("&d&lBATTLE PASS LEVEL UP! &fYou reached level &d" + level + "&f."));
                        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
                    } else {
                        player.sendMessage(color("&d+" + amount + " Battle Pass XP &8(&7" + source + "&8)"));
                    }
                })).exceptionally(error -> { plugin.getLogger().warning("Battle Pass XP save failed: " + error.getMessage()); return null; });
    }

    private void claim(Player player, int level) {
        if (level < 1 || level > maxLevel) { player.sendMessage(color("&cLevel must be between 1 and " + maxLevel + ".")); return; }
        if (!rewardBusy.add(player.getUniqueId())) { player.sendMessage(color("&cA reward action is already processing.")); return; }
        ConfigurationSection reward = config.getConfigurationSection("rewards." + level);
        if (reward == null) { rewardBusy.remove(player.getUniqueId()); player.sendMessage(color("&cThere is no reward configured for that level.")); return; }

        plugin.getDataStore().getBattlePassXp(player.getUniqueId(), seasonId).thenCompose(xp -> {
            if (xp / xpPerLevel < level) return java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalStateException("You have not unlocked this level yet."));
            return plugin.getDataStore().claimBattlePassLevel(player.getUniqueId(), seasonId, level);
        }).whenComplete((claimed, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                UUID playerId = player.getUniqueId();
                rewardBusy.remove(playerId);
                // If the SQL claim succeeded after the player disconnected, undo that
                // claim so it is not permanently consumed without a reward being delivered.
                if (error == null && Boolean.TRUE.equals(claimed)) {
                    plugin.getDataStore().unclaimBattlePassLevel(playerId, seasonId, level)
                            .exceptionally(rollbackError -> {
                                plugin.getLogger().severe("Could not restore an offline Battle Pass claim for "
                                        + playerId + " level " + level + ": " + rollbackError.getMessage());
                                return null;
                            });
                }
                return;
            }
            if (error != null) {
                rewardBusy.remove(player.getUniqueId());
                Throwable cause = error.getCause() == null ? error : error.getCause();
                player.sendMessage(color("&cReward unavailable: " + cause.getMessage()));
                return;
            }
            if (!Boolean.TRUE.equals(claimed)) {
                rewardBusy.remove(player.getUniqueId()); player.sendMessage(color("&cYou already claimed this reward.")); return;
            }
            deliverReward(player, level, reward);
        }));
    }

    private void deliverReward(Player player, int level, ConfigurationSection reward) {
        String type = reward.getString("type", "shards").toLowerCase(Locale.ROOT);
        long amount = Math.max(1, reward.getLong("amount", 1));
        switch (type) {
            case "shards" -> plugin.getDataStore().changeShards(player.getUniqueId(), player.getName(), null,
                    "BATTLEPASS", amount, PlayerDataStore.ShardChange.GIVE, "Battle Pass level " + level)
                    .whenComplete((balance, error) -> finishReward(player, level, error, "&d+" + amount + " Shards"));
            case "coins" -> deliverCoinsReward(player, level, amount);
            case "item" -> {
                Material material = Material.matchMaterial(reward.getString("material", "DIAMOND"));
                int itemAmount = reward.getInt("amount", 1);
                if (material == null || !material.isItem() || material.isAir() || itemAmount < 1 || itemAmount > 64) {
                    rollbackClaim(player, level, "Reward item is invalid or has an amount outside 1-64.");
                    return;
                }
                if (!player.isOnline()) { rollbackClaim(player, level, "You disconnected before the reward could be delivered."); return; }
                ItemStack[] before = player.getInventory().getStorageContents();
                ItemStack[] snapshot = new ItemStack[before.length];
                for (int i = 0; i < before.length; i++) snapshot[i] = before[i] == null ? null : before[i].clone();
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(material, itemAmount));
                if (!leftover.isEmpty()) {
                    player.getInventory().setStorageContents(snapshot);
                    rollbackClaim(player, level, "Your inventory is full. Free space and claim again.");
                    return;
                }
                finishReward(player, level, null, "&f+" + itemAmount + " " + material.name());
            }
            default -> rollbackClaim(player, level, "Unknown reward type: " + type);
        }
    }


    private void deliverCoinsReward(Player player, int level, long amount) {
        if (!plugin.getEconomyBridge().isAvailable()) {
            rollbackClaim(player, level, "Vault economy is unavailable.");
            return;
        }

        final double before;
        try {
            before = plugin.getEconomyBridge().getBalance(player);
            if (!Double.isFinite(before)) throw new IllegalStateException("Economy provider returned a non-finite balance.");
        } catch (RuntimeException balanceError) {
            rollbackClaim(player, level, "Could not verify Coins balance; claim restored.");
            plugin.getLogger().warning("Battle Pass reward balance lookup failed for " + player.getUniqueId()
                    + ": " + balanceError.getMessage());
            return;
        }

        EconomyResponse response = null;
        RuntimeException providerError = null;
        try {
            response = plugin.getEconomyBridge().deposit(player, amount);
        } catch (RuntimeException error) {
            providerError = error;
        }

        if (response != null && response.transactionSuccess()) {
            finishReward(player, level, null, "&6+" + amount + " Coins");
            return;
        }

        // A Vault provider can throw or return null after changing the balance. Reconcile
        // before deciding whether to restore the claim; never reopen a claim if it may
        // already have paid, because that would allow a duplicate reward on retry.
        try {
            double after = plugin.getEconomyBridge().getBalance(player);
            if (Double.isFinite(after) && Math.abs((after - before) - amount) <= 0.005d) {
                finishReward(player, level, null, "&6+" + amount + " Coins");
                return;
            }
            if (Double.isFinite(after) && Math.abs(after - before) <= 0.005d) {
                rollbackClaim(player, level, "Coins provider did not credit this reward; your claim was restored.");
                if (providerError != null) plugin.getLogger().warning("Battle Pass Coins deposit failed for "
                        + player.getUniqueId() + ": " + providerError.getMessage());
                return;
            }
            rewardBusy.remove(player.getUniqueId());
            player.sendMessage(color("&cYour Coins reward has an unclear provider result. Your claim is held to prevent duplicate payment; contact staff."));
            plugin.getLogger().severe("Ambiguous Battle Pass Coins reward for " + player.getUniqueId()
                    + " level " + level + ". Before=" + before + ", after=" + after
                    + (providerError == null ? "" : ", provider error=" + providerError.getMessage()));
        } catch (RuntimeException reconcileError) {
            rewardBusy.remove(player.getUniqueId());
            player.sendMessage(color("&cYour Coins reward could not be reconciled. Your claim is held to prevent duplicate payment; contact staff."));
            plugin.getLogger().severe("Could not reconcile Battle Pass Coins reward for " + player.getUniqueId()
                    + " level " + level + ": " + reconcileError.getMessage());
        }
    }

    private void finishReward(Player player, int level, Throwable error, String label) {
        if (error != null) { rollbackClaim(player, level, "Reward delivery failed."); return; }
        // This method may be called by a database completion callback; all Bukkit player
        // interaction must happen on the server thread.
        Bukkit.getScheduler().runTask(plugin, () -> {
            rewardBusy.remove(player.getUniqueId());
            if (player.isOnline()) player.sendMessage(color("&d&lBATTLE PASS &8» &aClaimed level " + level + ": " + label));
        });
    }

    private void rollbackClaim(Player player, int level, String reason) {
        plugin.getDataStore().unclaimBattlePassLevel(player.getUniqueId(), seasonId, level)
                .whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    rewardBusy.remove(player.getUniqueId());
                    if (player.isOnline()) {
                        player.sendMessage(color("&c" + reason + (error == null ? " Your claim was restored." : " Contact staff; claim rollback failed.")));
                    }
                }));
    }

    @EventHandler(ignoreCancelled = true)
    public void onOreBreak(BlockBreakEvent event) {
        if (event.getBlock().getType().name().endsWith("_ORE") || event.getBlock().getType().name().startsWith("DEEPSLATE_")
                && event.getBlock().getType().name().endsWith("_ORE")) award(event.getPlayer(), 2, "mining");
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        award(killer, event.getEntity() instanceof Player ? 20 : 5,
                event.getEntity() instanceof Player ? "player elimination" : "mob elimination");
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("claim");
        if (args.length == 2 && args[0].equalsIgnoreCase("claim")) {
            List<String> values = new ArrayList<>();
            ConfigurationSection rewards = config.getConfigurationSection("rewards");
            if (rewards != null) for (String key : rewards.getKeys(false)) if (key.startsWith(args[1])) values.add(key);
            return values;
        }
        return List.of();
    }

    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
}
