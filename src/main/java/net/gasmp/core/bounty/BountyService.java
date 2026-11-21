package net.gasmp.core.bounty;

import net.gasmp.core.GASMPCore;
import net.gasmp.core.data.PlayerDataStore;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BountyService implements Listener, CommandExecutor, TabCompleter {
    private final GASMPCore plugin;
    private final Set<UUID> claimsInProgress = ConcurrentHashMap.newKeySet();
    private final Set<UUID> placementsInProgress = ConcurrentHashMap.newKeySet();

    public BountyService(GASMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getConfig().getBoolean("modules.bounties", true)) {
            sender.sendMessage(color("&cThe bounty system is disabled."));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(color("&6&lGASMP BOUNTIES"));
            sender.sendMessage(color("&e/bounty place <online-player> <amount> &7- post Coins bounty"));
            sender.sendMessage(color("&e/bounty top &7- view current bounties"));
            return true;
        }
        if (args[0].equalsIgnoreCase("top")) {
            plugin.getDataStore().getTopBounties(10).whenComplete((list, error) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (error != null) { sender.sendMessage(color("&cCould not load bounty board.")); return; }
                        sender.sendMessage(color("&6&lGASMP BOUNTY BOARD"));
                        if (list.isEmpty()) { sender.sendMessage(color("&7There are no active bounties.")); return; }
                        int position = 1;
                        for (PlayerDataStore.BountyRecord entry : list) {
                            sender.sendMessage(color("&e" + position++ + ". &f" + entry.targetName()
                                    + " &8- &6" + String.format(Locale.US, "%.2f", entry.amount())
                                    + " " + plugin.getEconomyBridge().currencyNamePlural()));
                        }
                    }));
            return true;
        }
        if (!args[0].equalsIgnoreCase("place") || args.length < 3 || !(sender instanceof Player issuer)) {
            sender.sendMessage(color("&eUsage: /bounty place <online-player> <amount> or /bounty top"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(color("&cTarget must be online.")); return true; }
        if (target.getUniqueId().equals(issuer.getUniqueId())) { sender.sendMessage(color("&cYou cannot place a bounty on yourself.")); return true; }
        double amount;
        try { amount = Double.parseDouble(args[2]); }
        catch (NumberFormatException ex) { sender.sendMessage(color("&cEnter a valid amount.")); return true; }
        double minimum = plugin.getConfig().getDouble("bounties.minimum", 100.0);
        double maximum = plugin.getConfig().getDouble("bounties.maximum", 1000000.0);
        if (!Double.isFinite(amount) || amount < minimum || amount > maximum) {
            sender.sendMessage(color("&cBounty must be between " + minimum + " and " + maximum + ".")); return true;
        }
        if (!placementsInProgress.add(issuer.getUniqueId())) {
            sender.sendMessage(color("&cA bounty placement is already processing for you."));
            return true;
        }
        plugin.getEconomyBridge().refresh();
        if (!plugin.getEconomyBridge().isAvailable()) {
            placementsInProgress.remove(issuer.getUniqueId());
            sender.sendMessage(color("&cBounties require a Vault-compatible economy."));
            return true;
        }
        final boolean canAfford;
        try {
            canAfford = plugin.getEconomyBridge().has(issuer, amount);
        } catch (RuntimeException economyError) {
            placementsInProgress.remove(issuer.getUniqueId());
            sender.sendMessage(color("&cCould not check your Coins balance. No bounty was placed."));
            plugin.getLogger().warning("Bounty affordability check failed for " + issuer.getUniqueId()
                    + ": " + economyError.getMessage());
            return true;
        }
        if (!canAfford) {
            placementsInProgress.remove(issuer.getUniqueId());
            sender.sendMessage(color("&cYou do not have enough Coins."));
            return true;
        }

        final double balanceBeforeDebit;
        try {
            balanceBeforeDebit = plugin.getEconomyBridge().getBalance(issuer);
        } catch (RuntimeException balanceError) {
            placementsInProgress.remove(issuer.getUniqueId());
            sender.sendMessage(color("&cCould not verify your Coins balance. No bounty was placed."));
            plugin.getLogger().warning("Bounty balance lookup failed for " + issuer.getUniqueId()
                    + ": " + balanceError.getMessage());
            return true;
        }
        try {
            EconomyResponse payment = plugin.getEconomyBridge().withdraw(issuer, amount);
            if (payment == null || !payment.transactionSuccess()) {
                boolean refunded = refundObservedDebit(issuer, balanceBeforeDebit, amount);
                placementsInProgress.remove(issuer.getUniqueId());
                sender.sendMessage(color(refunded
                        ? "&cPayment response was invalid; an observed deduction was refunded."
                        : "&cPayment was not confirmed. Check your balance and contact staff before retrying."));
                return true;
            }
        } catch (RuntimeException paymentError) {
            boolean refunded = refundObservedDebit(issuer, balanceBeforeDebit, amount);
            placementsInProgress.remove(issuer.getUniqueId());
            sender.sendMessage(color(refunded
                    ? "&cPayment provider errored; detected Coins deduction refunded."
                    : "&cPayment state is unknown. Do not retry; contact staff."));
            plugin.getLogger().severe("Bounty placement payment error for " + issuer.getUniqueId()
                    + ": " + paymentError.getMessage());
            return true;
        }

        plugin.getDataStore().addBounty(target.getUniqueId(), target.getName(), amount)
                .whenComplete((nothing, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    placementsInProgress.remove(issuer.getUniqueId());
                    if (error != null) {
                        boolean refunded = refundAfterKnownDebit(issuer, balanceBeforeDebit, amount);
                        sender.sendMessage(color(refunded
                                ? "&cBounty could not be saved. Coins refunded."
                                : "&cBounty save/refund needs staff review. Do not retry until checked."));
                        plugin.getLogger().severe("Bounty write failed for " + target.getUniqueId() + ": " + error.getMessage());
                        return;
                    }
                    sender.sendMessage(color("&aPlaced a &6" + String.format(Locale.US, "%.2f", amount)
                            + " Coins &abounty on &f" + target.getName() + "&a."));
                    if (target.isOnline()) target.sendMessage(color("&cA bounty was placed on you! &6"
                            + String.format(Locale.US, "%.2f", amount) + " Coins"));
                }));
        return true;
    }

    private boolean refundObservedDebit(Player player, double balanceBefore, double amount) {
        try {
            double current = plugin.getEconomyBridge().getBalance(player);
            if (Double.isFinite(balanceBefore)) {
                double decrease = balanceBefore - current;
                if (!Double.isFinite(decrease) || Math.abs(decrease - amount) > 0.005d) return false;
            } else {
                // When withdraw threw before returning, only refund a current balance that can
                // be compared to a fresh pre-withdraw balance; otherwise the state is ambiguous.
                return false;
            }
            return depositAndVerify(player, amount, current);
        } catch (RuntimeException error) {
            plugin.getLogger().severe("Could not inspect/refund bounty payment for " + player.getUniqueId()
                    + ": " + error.getMessage());
            return false;
        }
    }

    private boolean refundAfterKnownDebit(Player player, double balanceBefore, double amount) {
        return refundObservedDebit(player, balanceBefore, amount);
    }

    private boolean depositAndVerify(org.bukkit.OfflinePlayer player, double amount, double balanceBefore) {
        try {
            EconomyResponse refund = plugin.getEconomyBridge().deposit(player, amount);
            if (refund != null && refund.transactionSuccess()) return true;
            double after = plugin.getEconomyBridge().getBalance(player);
            return Double.isFinite(after) && Math.abs((after - balanceBefore) - amount) <= 0.005d;
        } catch (RuntimeException error) {
            try {
                double after = plugin.getEconomyBridge().getBalance(player);
                return Double.isFinite(after) && Math.abs((after - balanceBefore) - amount) <= 0.005d;
            } catch (RuntimeException balanceError) {
                plugin.getLogger().severe("Bounty refund status is unknown for " + player.getUniqueId()
                        + ": " + balanceError.getMessage());
                return false;
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) return;
        plugin.getEconomyBridge().refresh();
        if (!plugin.getEconomyBridge().isAvailable()) {
            plugin.getLogger().warning("Bounty claim skipped because the Vault Economy provider is unavailable.");
            return;
        }
        if (!claimsInProgress.add(victim.getUniqueId())) return;
        plugin.getDataStore().claimBounty(victim.getUniqueId()).whenComplete((amount, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    claimsInProgress.remove(victim.getUniqueId());
                    if (error != null) {
                        plugin.getLogger().warning("Bounty claim failed: " + error.getMessage()); return;
                    }
                    if (amount == null || amount <= 0.0) return;
                    String payoutErrorMessage = null;
                    boolean paid = false;
                    boolean stateUnknown = false;
                    org.bukkit.OfflinePlayer killerAccount = Bukkit.getOfflinePlayer(killer.getUniqueId());
                    double balanceBeforePayout = Double.NaN;
                    try {
                        plugin.getEconomyBridge().refresh();
                        if (!plugin.getEconomyBridge().isAvailable()) {
                            payoutErrorMessage = "Vault Economy provider disappeared";
                        } else {
                            balanceBeforePayout = plugin.getEconomyBridge().getBalance(killerAccount);
                            EconomyResponse payout = plugin.getEconomyBridge().deposit(killerAccount, amount);
                            paid = payout != null && payout.transactionSuccess();
                            if (!paid) {
                                double after = plugin.getEconomyBridge().getBalance(killerAccount);
                                paid = Double.isFinite(after) && Math.abs((after - balanceBeforePayout) - amount) <= 0.005d;
                            }
                            if (!paid) payoutErrorMessage = "provider rejected or did not apply the payout";
                        }
                    } catch (Exception payoutError) {
                        payoutErrorMessage = payoutError.getMessage() == null
                                ? payoutError.getClass().getSimpleName() : payoutError.getMessage();
                        try {
                            double after = plugin.getEconomyBridge().getBalance(killerAccount);
                            paid = Double.isFinite(balanceBeforePayout) && Double.isFinite(after)
                                    && Math.abs((after - balanceBeforePayout) - amount) <= 0.005d;
                        } catch (Exception balanceError) {
                            stateUnknown = true;
                            plugin.getLogger().severe("Cannot inspect bounty payout state for killer "
                                    + killer.getUniqueId() + ": " + balanceError.getMessage());
                        }
                    }
                    if (!paid) {
                        if (stateUnknown) {
                            plugin.getLogger().severe("Bounty # for victim " + victim.getUniqueId()
                                    + " needs manual payout reconciliation; automatic restoration was withheld to avoid a duplicate payout.");
                            Player onlineKiller = Bukkit.getPlayer(killer.getUniqueId());
                            if (onlineKiller != null) onlineKiller.sendMessage(color("&4Your bounty payout is being reviewed by staff. Do not claim it again."));
                            return;
                        }
                        restoreBounty(victim, killer, amount, payoutErrorMessage == null ? "unknown provider failure" : payoutErrorMessage);
                        return;
                    }
                    String paidText = String.format(Locale.US, "%.2f", amount);
                    Player onlineKiller = Bukkit.getPlayer(killer.getUniqueId());
                    if (onlineKiller != null) onlineKiller.sendMessage(color("&6&lBOUNTY CLAIMED &8» &fYou received &6" + paidText + " Coins&f for killing " + victim.getName() + "!"));
                    Bukkit.broadcastMessage(color("&6&lBOUNTY &8» &f" + killer.getName() + " claimed "
                            + paidText + " Coins for eliminating " + victim.getName() + "!"));
                }));
    }

    private void restoreBounty(Player victim, Player killer, double amount, String cause) {
        plugin.getDataStore().addBounty(victim.getUniqueId(), victim.getName(), amount)
                .whenComplete((ignored, restoreError) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (restoreError != null) {
                        plugin.getLogger().severe("Bounty payout and restoration both failed for "
                                + victim.getUniqueId() + ": " + restoreError.getMessage()
                                + " (original: " + cause + ")");
                        Player onlineKiller = Bukkit.getPlayer(killer.getUniqueId());
                        if (onlineKiller != null) onlineKiller.sendMessage(color("&4Bounty payout failed and automatic restoration failed. Contact staff."));
                    } else {
                        plugin.getLogger().warning("Restored bounty after payout failure: " + cause);
                        Player onlineKiller = Bukkit.getPlayer(killer.getUniqueId());
                        if (onlineKiller != null) onlineKiller.sendMessage(color("&cBounty payout failed; the bounty has been restored."));
                    }
                }));
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("place", "top", "help").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("place") && sender instanceof Player) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.startsWith(args[1])).toList();
        }
        return List.of();
    }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
}

