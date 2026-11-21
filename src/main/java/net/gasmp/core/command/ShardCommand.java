package net.gasmp.core.command;

import net.gasmp.core.GASMPCore;
import net.gasmp.core.data.PlayerDataStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ShardCommand implements CommandExecutor, TabCompleter {
    private final GASMPCore plugin;

    public ShardCommand(GASMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getConfig().getBoolean("modules.shards", true)) {
            sender.sendMessage(color("&cThe Shards system is disabled."));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("balance")) {
            Player target;
            if (args.length > 1) {
                if (!sender.hasPermission("gasmpcore.shards.admin")) {
                    sender.sendMessage(color("&cYou cannot view another player's balance."));
                    return true;
                }
                target = Bukkit.getPlayerExact(args[1]);
            } else if (sender instanceof Player player) {
                target = player;
            } else {
                sender.sendMessage(color("&eUsage: /shards balance <online-player>"));
                return true;
            }

            if (target == null) {
                sender.sendMessage(color("&cThat player is not online."));
                return true;
            }

            Player selected = target;
            plugin.getDataStore().getShards(selected.getUniqueId()).whenComplete((balance, error) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (error != null) {
                            sender.sendMessage(color("&cCould not load the Shards balance."));
                            return;
                        }
                        sender.sendMessage(color("&d&lGASMP &8» &f" + selected.getName()
                                + " has &d" + balance + " ✦ Shards&f."));
                    }));
            return true;
        }

        if (!sender.hasPermission("gasmpcore.shards.admin")) {
            sender.sendMessage(color("&cYou do not have permission."));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(color("&eUsage: /shards <give|take|set> <online-player> <amount> [reason]"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(color("&cThat player must be online."));
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
            if (amount < 0) throw new NumberFormatException("negative");
        } catch (NumberFormatException ex) {
            sender.sendMessage(color("&cAmount must be a non-negative whole number."));
            return true;
        }

        PlayerDataStore.ShardChange operation;
        try {
            operation = PlayerDataStore.ShardChange.valueOf(args[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(color("&eUsage: /shards <give|take|set> <online-player> <amount> [reason]"));
            return true;
        }

        String reason = args.length > 3
                ? String.join(" ", Arrays.copyOfRange(args, 3, args.length))
                : "Admin adjustment";
        UUID actorId = sender instanceof Player player ? player.getUniqueId() : null;
        Player selected = target;

        plugin.getDataStore().changeShards(
                selected.getUniqueId(), selected.getName(), actorId,
                operation.name(), amount, operation, reason
        ).whenComplete((balance, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                sender.sendMessage(color("&cShards update failed: &7" + cause.getMessage()));
                return;
            }
            sender.sendMessage(color("&aUpdated " + selected.getName() + "'s Shards. New balance: &d"
                    + balance + " ✦&a."));
            selected.sendMessage(color("&d&lGASMP &8» &fYour Shards balance is now &d"
                    + balance + " ✦&f."));
        }));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("balance", "give", "take", "set").stream()
                    .filter(v -> v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && sender.hasPermission("gasmpcore.shards.admin")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
