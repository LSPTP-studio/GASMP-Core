package net.gasmp.core.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.*;

public final class PerformanceCommand implements CommandExecutor {
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L;
        long max = runtime.maxMemory() / 1024L / 1024L;
        int chunks = 0, entities = 0;
        for (World world : Bukkit.getWorlds()) { chunks += world.getLoadedChunks().length; entities += world.getEntityCount(); }
        sender.sendMessage(ChatColor.GOLD + "GASMP PERFORMANCE SNAPSHOT");
        sender.sendMessage(ChatColor.GRAY + "Online: " + ChatColor.WHITE + Bukkit.getOnlinePlayers().size());
        sender.sendMessage(ChatColor.GRAY + "Loaded chunks: " + ChatColor.WHITE + chunks);
        sender.sendMessage(ChatColor.GRAY + "Loaded entities: " + ChatColor.WHITE + entities);
        sender.sendMessage(ChatColor.GRAY + "JVM memory: " + ChatColor.WHITE + used + " / " + max + " MiB");
        sender.sendMessage(ChatColor.DARK_GRAY + "Snapshot only; use a profiler for TPS/MSPT diagnosis.");
        return true;
    }
}
