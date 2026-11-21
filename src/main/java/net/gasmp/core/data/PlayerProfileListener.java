package net.gasmp.core.data;

import net.gasmp.core.GASMPCore;
import org.bukkit.Statistic;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerProfileListener implements Listener {
    private final GASMPCore plugin;

    public PlayerProfileListener(GASMPCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        long start = Math.max(0L, plugin.getConfig().getLong("shards.starting-balance", 0L));
        plugin.getDataStore().ensurePlayer(player.getUniqueId(), player.getName(), start)
                .exceptionally(error -> {
                    plugin.getLogger().warning("Profile init failed for " + player.getName() + ": " + error.getMessage());
                    return null;
                });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        plugin.getDataStore().saveStatistics(
                player.getUniqueId(), player.getName(),
                player.getStatistic(Statistic.PLAYER_KILLS),
                player.getStatistic(Statistic.DEATHS),
                player.getStatistic(Statistic.PLAY_ONE_MINUTE)
        ).exceptionally(error -> {
            plugin.getLogger().warning("Profile save failed for " + player.getName() + ": " + error.getMessage());
            return null;
        });
    }
}
