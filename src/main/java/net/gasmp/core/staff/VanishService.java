package net.gasmp.core.staff;

import net.gasmp.core.GASMPCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Group-based vanish: OP and wildcard permissions alone do not confer staff visibility. */
public final class VanishService implements Listener, CommandExecutor {
    private final GASMPCore plugin;
    private final StaffAccess staff;
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public VanishService(GASMPCore plugin, StaffAccess staff) {
        this.plugin = plugin;
        this.staff = staff;
    }

    private boolean isStaff(Player player) {
        return staff.hasAny(player, "owner", "admin", "manager", "dev", "builder", "mod", "helper");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is for players.");
            return true;
        }
        if (!isStaff(player)) {
            player.sendMessage(color("&cOnly GASMP staff can use vanish."));
            return true;
        }

        if (vanished.remove(player.getUniqueId())) {
            for (Player viewer : Bukkit.getOnlinePlayers()) viewer.showPlayer(plugin, player);
            player.sendMessage(color("&aVanish disabled."));
        } else {
            vanished.add(player.getUniqueId());
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(player)) continue;
                if (isStaff(viewer)) viewer.showPlayer(plugin, player);
                else viewer.hidePlayer(plugin, player);
            }
            player.sendMessage(color("&aVanish enabled."));
        }
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        for (UUID id : vanished) {
            Player hidden = Bukkit.getPlayer(id);
            if (hidden != null && !joining.equals(hidden)) {
                if (isStaff(joining)) joining.showPlayer(plugin, hidden);
                else joining.hidePlayer(plugin, hidden);
            }
        }
        if (vanished.contains(joining.getUniqueId())) {
            event.joinMessage(null);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!viewer.equals(joining)) {
                    if (isStaff(viewer)) viewer.showPlayer(plugin, joining);
                    else viewer.hidePlayer(plugin, joining);
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (vanished.remove(event.getPlayer().getUniqueId())) event.quitMessage(null);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
