package net.gasmp.core.staff;

import net.gasmp.core.GASMPCore;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;

/** Enforces the requested staff-only third-party commands independent of plugin defaults. */
public final class CommandRestrictionService implements Listener {
    private final StaffAccess staff;
    public CommandRestrictionService(GASMPCore plugin, StaffAccess staff) { this.staff = staff; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().trim();
        if (!message.startsWith("/") || message.length() < 2) return;
        String root = message.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (root.contains(":")) root = root.substring(root.indexOf(':') + 1);

        String[] allowed = switch (root) {
            case "crates", "skript", "sk" -> new String[]{"owner", "dev"};
            case "libsdisguises", "disguise", "dis", "d", "ld", "undisguise", "undis", "disguiseplayer" -> new String[]{"owner", "dev"};
            case "openinv", "oi", "inv", "open", "openender", "oe",
                    "clearinv", "clearender", "searchinv", "si", "searchender", "se",
                    "searchenchant", "searchenchants", "searchcontainer", "searchchest",
                    "anycontainer", "anychest", "ac", "silentcontainer", "silentchest", "sc" ->
                    new String[]{"owner", "admin", "dev"};
            case "headdatabase", "headdb", "hdb", "heads", "head" -> new String[]{"owner", "dev", "builder"};
            case "gvanish", "gasmpvanish", "vanish", "v", "evanish", "unvanish" ->
                    new String[]{"owner", "admin", "manager", "dev", "builder", "mod", "helper"};
            default -> null;
        };
        if (allowed == null || staff.hasAny(player, allowed)) return;
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "You do not have access to that staff command.");
    }
}
