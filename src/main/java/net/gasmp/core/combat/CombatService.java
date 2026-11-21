package net.gasmp.core.combat;

import net.gasmp.core.GASMPCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatService implements Listener {
    private final GASMPCore plugin;
    private final Map<UUID, Long> taggedUntil = new ConcurrentHashMap<>();
    private final long tagMillis;

    public CombatService(GASMPCore plugin) {
        this.plugin = plugin;
        this.tagMillis = Math.max(5, plugin.getConfig().getLong("combat.tag-seconds", 15)) * 1000L;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("modules.combat", true) || event.getFinalDamage() <= 0) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) source = shooter;
        if (!(source instanceof Player attacker) || attacker.getUniqueId().equals(victim.getUniqueId())) return;
        if (attacker.hasPermission("gasmpcore.combat.bypass") || victim.hasPermission("gasmpcore.combat.bypass")) return;
        tag(attacker); tag(victim);
    }

    private void tag(Player player) {
        long now = System.currentTimeMillis();
        boolean fresh = !isTagged(player.getUniqueId());
        taggedUntil.put(player.getUniqueId(), now + tagMillis);
        if (fresh) player.sendMessage(color("&c&lCOMBAT TAG &8» &fDo not log out or use teleport commands."));
    }

    public boolean isTagged(UUID uuid) {
        Long until = taggedUntil.get(uuid);
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) { taggedUntil.remove(uuid); return false; }
        return true;
    }

    public long remainingSeconds(UUID uuid) {
        Long until = taggedUntil.get(uuid);
        return until == null ? 0 : Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isTagged(player.getUniqueId())
                && !player.hasPermission("gasmpcore.combat.bypass")
                && plugin.getConfig().getBoolean("combat.punish-on-logout", true)) {
            plugin.getDataStore().markCombatLogout(player.getUniqueId()).exceptionally(error -> {
                plugin.getLogger().severe("Could not record combat logout for " + player.getName() + ": " + error.getMessage());
                return null;
            });
        }
        taggedUntil.remove(player.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getDataStore().consumeCombatLogout(player.getUniqueId()).thenAccept(flagged -> {
            if (!flagged) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || player.isDead()) return;
                player.sendMessage(color("&cYou logged out while combat-tagged. Your combat-log penalty has been applied."));
                player.setHealth(0.0);
            });
        }).exceptionally(error -> {
            plugin.getLogger().warning("Could not check combat logout flag for " + player.getName() + ": " + error.getMessage());
            return null;
        });
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // The victim's combat session ends on death. The killer remains in combat
        // because they have just dealt lethal PvP damage and may still be attacked.
        taggedUntil.remove(event.getEntity().getUniqueId());
        Player killer = event.getEntity().getKiller();
        if (killer != null && !killer.hasPermission("gasmpcore.combat.bypass")) {
            tag(killer);
        }
    }

    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
}

