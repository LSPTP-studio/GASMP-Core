package net.gasmp.core.command;

import net.gasmp.core.GASMPCore;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class CoreCommand implements CommandExecutor, TabCompleter {
    private final GASMPCore plugin;
    public CoreCommand(GASMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("gasmp")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
                return true;
            }
            plugin.getMenuService().openMain(player);
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "GASMP CORE");
            sender.sendMessage(ChatColor.GRAY + "Version: " + ChatColor.WHITE + plugin.getPluginMeta().getVersion());
            sender.sendMessage(ChatColor.GRAY + "Online players: " + ChatColor.WHITE + plugin.getServer().getOnlinePlayers().size());
            sender.sendMessage(ChatColor.GRAY + "SQLite profiles / Shards: " + ChatColor.GREEN + "Enabled");
            sender.sendMessage(ChatColor.GRAY + "LuckPerms rank sync: " + (plugin.getRankService().isReady()
                    ? ChatColor.GREEN + "Ready" : ChatColor.YELLOW + "Unavailable / syncing"));
            sender.sendMessage(ChatColor.GRAY + "Vault Coins provider: " + (plugin.getEconomyBridge().isAvailable()
                    ? ChatColor.GREEN + plugin.getEconomyBridge().currencyNamePlural()
                    : ChatColor.YELLOW + "Not detected"));
            sender.sendMessage(ChatColor.GRAY + "State: " + ChatColor.GREEN + "Running");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("gasmpcore.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission.");
                return true;
            }
            plugin.reloadCore();
            sender.sendMessage(ChatColor.GREEN + "[GASMP] Configuration reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("menu")) {
            if (!sender.hasPermission("gasmpcore.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission.");
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "This command must be run by a player.");
                return true;
            }
            plugin.getMenuService().openMain(player);
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Usage: /gasmpcore <status|reload|menu>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("status", "reload", "menu").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
