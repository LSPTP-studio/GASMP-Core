package net.gasmp.core.crates;

import net.gasmp.core.GASMPCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Optional, reflection-only bridges to the RP Engine GoKarts and Paintball add-ons.
 * Finished races and games award configured crate keys once per result cycle.
 */
public final class KeyRewardBridge {
    private final GASMPCore plugin;
    private final CrateService crates;
    private final Set<Object> seenRaces = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Object> seenGames = Collections.newSetFromMap(new IdentityHashMap<>());
    private BukkitTask task;

    public KeyRewardBridge(GASMPCore plugin, CrateService crates) {
        this.plugin = plugin;
        this.crates = crates;
    }

    public void start() {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        seenRaces.clear();
        seenGames.clear();
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("modules.crates", true)) return;
        pollGoKarts();
        pollPaintball();
    }

    private void pollGoKarts() {
        if (!plugin.getConfig().getBoolean("key-rewards.gokarts.enabled", true)) return;
        Plugin addon = Bukkit.getPluginManager().getPlugin("RPEGoKarts");
        if (addon == null || !addon.isEnabled()) return;
        try {
            Object manager = invoke(addon, "races");
            Object rawRaces = invoke(manager, "races");
            if (!(rawRaces instanceof Collection<?> races)) return;
            for (Object race : races) {
                Object phase = invoke(race, "phase");
                if (!"RESULTS".equalsIgnoreCase(String.valueOf(phase))) {
                    seenRaces.remove(race);
                    continue;
                }
                Object rawResults = invoke(race, "lastResults");
                if (!(rawResults instanceof List<?> results) || results.isEmpty() || !seenRaces.add(race)) continue;
                for (Object result : results) {
                    if (Boolean.TRUE.equals(invoke(result, "bot"))) continue;
                    int place = ((Number) invoke(result, "place")).intValue();
                    String path = "key-rewards.gokarts.placements." + place;
                    String crateId = plugin.getConfig().getString(path + ".crate", "");
                    int amount = plugin.getConfig().getInt(path + ".amount", 0);
                    Object rawUuid = invoke(result, "id");
                    if (!(rawUuid instanceof UUID uuid) || crateId.isBlank() || amount <= 0) continue;
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) crates.giveKeyTo(player, crateId.toLowerCase(Locale.ROOT), Math.min(64, amount), true);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().fine("GoKarts key-reward bridge unavailable: " + ex.getMessage());
        }
    }

    private void pollPaintball() {
        if (!plugin.getConfig().getBoolean("key-rewards.paintball.enabled", true)) return;
        Plugin addon = Bukkit.getPluginManager().getPlugin("RPEPaintball");
        if (addon == null || !addon.isEnabled()) return;
        try {
            Object manager = invoke(addon, "games");
            Object rawGames = invoke(manager, "games");
            if (!(rawGames instanceof Collection<?> games)) return;
            for (Object game : games) {
                Object phase = invoke(game, "phase");
                if (!"ENDED".equalsIgnoreCase(String.valueOf(phase))) {
                    seenGames.remove(game);
                    continue;
                }
                if (!Boolean.TRUE.equals(invoke(game, "played")) || !seenGames.add(game)) continue;
                Object winner = invoke(game, "winner");
                if (winner == null) continue;
                Object rawRoster = invoke(game, "roster", winner);
                if (!(rawRoster instanceof Collection<?> roster)) continue;
                String crateId = plugin.getConfig().getString("key-rewards.paintball.winner.crate", "common");
                int amount = Math.max(0, Math.min(64, plugin.getConfig().getInt("key-rewards.paintball.winner.amount", 1)));
                if (crateId.isBlank() || amount <= 0) continue;
                for (Object member : roster) {
                    Object rawPlayer = invoke(member, "player");
                    if (rawPlayer instanceof Player player && player.isOnline()) {
                        crates.giveKeyTo(player, crateId.toLowerCase(Locale.ROOT), amount, true);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().fine("Paintball key-reward bridge unavailable: " + ex.getMessage());
        }
    }

    private static Object invoke(Object receiver, String methodName, Object... args) throws ReflectiveOperationException {
        if (receiver == null) throw new NoSuchMethodException("null receiver for " + methodName);
        Method selected = null;
        for (Method method : receiver.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) continue;
            Class<?>[] parameters = method.getParameterTypes();
            boolean compatible = true;
            for (int i = 0; i < parameters.length; i++) {
                if (args[i] != null && !parameters[i].isAssignableFrom(args[i].getClass())) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                selected = method;
                break;
            }
        }
        if (selected == null) throw new NoSuchMethodException(receiver.getClass().getName() + "." + methodName);
        return selected.invoke(receiver, args);
    }
}
