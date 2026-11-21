package net.gasmp.core.command;

import net.gasmp.core.GASMPCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;

import java.util.List;
import java.util.Locale;

public final class LeaderboardCommand implements CommandExecutor, TabCompleter {
    private final GASMPCore plugin;
    public LeaderboardCommand(GASMPCore plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) { sender.sendMessage(color("&e/gtop <kills|deaths|playtime|shards>")); return true; }
        String stat = args[0].toLowerCase(Locale.ROOT);
        plugin.getDataStore().getLeaderboard(stat, 10).whenComplete((rows, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) { sender.sendMessage(color("&cCould not load this leaderboard.")); return; }
            sender.sendMessage(color("&d&lGASMP TOP " + stat.toUpperCase(Locale.ROOT)));
            if (rows.isEmpty()) { sender.sendMessage(color("&7No player data yet.")); return; }
            int i=1;
            for (String row : rows) {
                String[] parts = row.split("\\|", 2);
                long value = Long.parseLong(parts[1]);
                String formatted = stat.equals("playtime") ? formatTicks(value) : Long.toString(value);
                sender.sendMessage(color("&e" + i++ + ". &f" + parts[0] + " &8- &d" + formatted));
            }
        }));
        return true;
    }
    private String formatTicks(long ticks) { long mins=ticks/20/60; return (mins/60)+"h "+(mins%60)+"m"; }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("kills","deaths","playtime","shards") : List.of();
    }
}
