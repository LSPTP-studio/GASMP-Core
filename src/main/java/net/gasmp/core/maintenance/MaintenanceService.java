package net.gasmp.core.maintenance;

import net.gasmp.core.GASMPCore;
import net.gasmp.core.staff.StaffAccess;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Locale;

public final class MaintenanceService implements Listener, CommandExecutor, TabCompleter {
    private final GASMPCore plugin;
    private final StaffAccess staff;
    private final File file;
    private YamlConfiguration config;

    public MaintenanceService(GASMPCore plugin, StaffAccess staff) {
        this.plugin = plugin; this.staff = staff;
        this.file = new File(plugin.getDataFolder(), "maintenance.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            config = new YamlConfiguration();
            config.set("enabled", false);
            config.set("kick-message", "&cGASMP is under maintenance. Please try again soon.");
            config.set("motd", "&dGASMP &8• &cMaintenance in progress");
            save();
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled() { return config.getBoolean("enabled", false); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
                sender.sendMessage("Maintenance: " + isEnabled()); return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reload(); sender.sendMessage("Maintenance configuration reloaded."); return true;
            }
            if (args.length != 1 || !(args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off"))) {
                sender.sendMessage("Usage: /gmaintenance <on|off|status|reload>"); return true;
            }
            config.set("enabled", args[0].equalsIgnoreCase("on")); save();
            sender.sendMessage("Maintenance mode " + (isEnabled() ? "enabled" : "disabled") + "."); return true;
        }
        if (!staff.hasAny(player, "owner", "dev", "admin", "manager")) {
            player.sendMessage(color("&cOnly Owner, Developer, Admin or Manager can manage maintenance.")); return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            player.sendMessage(color("&dGASMP Maintenance: " + (isEnabled() ? "&cENABLED" : "&aDISABLED"))); return true;
        }
        if (args[0].equalsIgnoreCase("on")) {
            config.set("enabled", true); save();
            Bukkit.broadcast(color("&c&lGASMP is entering maintenance mode. New players will be temporarily blocked."));
        } else if (args[0].equalsIgnoreCase("off")) {
            config.set("enabled", false); save();
            Bukkit.broadcast(color("&a&lGASMP maintenance mode has ended. Welcome back!"));
        } else if (args[0].equalsIgnoreCase("reload")) {
            reload(); player.sendMessage(color("&aMaintenance config reloaded."));
        } else {
            player.sendMessage(color("&e/gmaintenance <on|off|status|reload>"));
        }
        return true;
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        if (!isEnabled()) return;
        Player player = event.getPlayer();
        if (staff.hasAny(player, "owner", "dev", "admin", "manager") || player.hasPermission("gasmpcore.maintenance.bypass")) return;
        String message = color(config.getString("kick-message", "&cGASMP is under maintenance."));
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, message);
    }

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        if (!isEnabled()) return;
        event.setMotd(color(config.getString("motd", "&dGASMP &8• &cMaintenance in progress")));
    }

    private void save() {
        try { config.save(file); }
        catch (Exception ex) { plugin.getLogger().severe("Could not save maintenance.yml: " + ex.getMessage()); }
    }
    private String color(String value) { return ChatColor.translateAlternateColorCodes('&', value); }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("on", "off", "status", "reload");
        return List.of();
    }
}
