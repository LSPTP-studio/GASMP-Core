package net.gasmp.core.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Adapter for any economy plugin that registers the Vault Economy service.
 * The provider remains owned by the third-party economy plugin.
 */
public final class VaultEconomyBridge {
    private Economy provider;

    public void refresh() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            provider = null;
            return;
        }
        RegisteredServiceProvider<Economy> registration =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        provider = registration == null ? null : registration.getProvider();
    }

    public boolean isAvailable() {
        refresh();
        return provider != null;
    }

    public String currencyNameSingular() {
        refresh();
        return provider == null ? "Coin" : provider.currencyNameSingular();
    }

    public String currencyNamePlural() {
        refresh();
        return provider == null ? "Coins" : provider.currencyNamePlural();
    }

    public double getBalance(OfflinePlayer player) {
        requireProvider();
        return provider.getBalance(player);
    }

    public boolean has(OfflinePlayer player, double amount) {
        requireProvider();
        return amount >= 0.0 && provider.has(player, amount);
    }

    public EconomyResponse withdraw(OfflinePlayer player, double amount) {
        requireProvider();
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("Withdrawal amount must be finite and non-negative.");
        }
        return provider.withdrawPlayer(player, amount);
    }

    public EconomyResponse deposit(OfflinePlayer player, double amount) {
        requireProvider();
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("Deposit amount must be finite and non-negative.");
        }
        return provider.depositPlayer(player, amount);
    }

    private void requireProvider() {
        // Economy providers may register after Vault/this plugin is enabled or may be replaced
        // during maintenance. Resolve the current Vault service at the point of use.
        refresh();
        if (provider == null) {
            throw new IllegalStateException("No Vault Economy provider is currently registered.");
        }
    }
}
