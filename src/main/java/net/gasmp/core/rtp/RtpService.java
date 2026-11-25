package net.gasmp.core.rtp;

import net.gasmp.core.GASMPCore;
import net.gasmp.core.combat.CombatService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class RtpService implements Listener {
    private static final class Request {
        private final UUID token = UUID.randomUUID();
        private BukkitTask warmupTask;
    }

    private final GASMPCore plugin;
    private final CombatService combat;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    // Access is kept on the server thread; asynchronous callbacks are marshalled back before touching it.
    private final Map<UUID, Request> requests = new HashMap<>();

    public RtpService(GASMPCore plugin, CombatService combat) {
        this.plugin = plugin;
        this.combat = combat;
        File file = new File(plugin.getDataFolder(), "rtp.yml");
        if (!file.exists()) plugin.saveResource("rtp.yml", false);
    }

    public void start(Player player) {
        start(player, null);
    }

    public void start(Player player, String selectedWorld) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> start(player));
            return;
        }
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "rtp.yml"));
        if (!cfg.getBoolean("settings.enabled", true)) {
            player.sendMessage(color("&cRTP is disabled."));
            return;
        }
        if (combat.isTagged(player.getUniqueId())) {
            player.sendMessage(color("&cYou cannot use RTP while combat-tagged."));
            return;
        }
        if (requests.containsKey(player.getUniqueId())) {
            player.sendMessage(color("&cYou already have an RTP request running."));
            return;
        }

        long now = System.currentTimeMillis();
        long until = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (until > now) {
            player.sendMessage(color("&cRTP cooldown: &f" + ((until - now + 999) / 1000) + "s"));
            return;
        }

        String worldName = selectedWorld == null || selectedWorld.isBlank()\n                ? cfg.getString("worlds.overworld", cfg.getString("settings.world", "world"))\n                : selectedWorld.trim();
        World world = Bukkit.getWorld(worldName);
        if (world == null || cfg.getStringList("settings.blocked-worlds").contains(player.getWorld().getName())) {
            player.sendMessage(color("&cRTP is unavailable in this world."));
            return;
        }

        Request request = new Request();
        requests.put(player.getUniqueId(), request);
        int warmup = Math.max(0, cfg.getInt("settings.warmup-seconds", 3));
        player.sendMessage(color("&bRTP &8» &fFinding a safe location in &b" + warmup + "s&f. Don't move."));
        request.warmupTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isCurrent(player, request)) return;
            request.warmupTask = null;
            if (!player.isOnline()) {
                requests.remove(player.getUniqueId(), request);
                return;
            }
            if (combat.isTagged(player.getUniqueId())) {
                requests.remove(player.getUniqueId(), request);
                player.sendMessage(color("&cRTP cancelled: you are in combat."));
                return;
            }
            tryCandidate(player, world, request,
                    cfg.getInt("settings.min-radius", 750),
                    cfg.getInt("settings.max-radius", 6000),
                    Math.max(1, cfg.getInt("settings.max-attempts", 18)),
                    cfg.getBoolean("settings.avoid-danger", true),
                    cfg.getBoolean("settings.avoid-water", true),
                    Math.max(0, cfg.getInt("settings.cooldown-seconds", 300)));
        }, warmup * 20L);
    }

    private void tryCandidate(Player player, World world, Request request,
                              int minRadius, int maxRadius, int remaining,
                              boolean avoidDanger, boolean avoidWater, int cooldownSeconds) {
        if (!isCurrent(player, request)) return;
        if (!player.isOnline()) {
            requests.remove(player.getUniqueId(), request);
            return;
        }
        if (combat.isTagged(player.getUniqueId())) {
            requests.remove(player.getUniqueId(), request);
            player.sendMessage(color("&cRTP cancelled: you entered combat while a location was loading."));
            return;
        }
        if (remaining <= 0) {
            requests.remove(player.getUniqueId(), request);
            player.sendMessage(color("&cCould not find a safe RTP location. Try again later."));
            return;
        }

        int low = Math.max(1, Math.min(minRadius, maxRadius));
        int high = Math.max(low + 1, Math.max(minRadius, maxRadius));
        double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
        double radius = ThreadLocalRandom.current().nextDouble(low, high);
        Location spawn = world.getSpawnLocation();
        int x = spawn.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
        int z = spawn.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
        int cx = x >> 4;
        int cz = z >> 4;

        world.getChunkAtAsync(cx, cz, true).whenComplete((chunk, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!isCurrent(player, request)) return;
                    if (!player.isOnline()) {
                        requests.remove(player.getUniqueId(), request);
                        return;
                    }
                    if (combat.isTagged(player.getUniqueId())) {
                        requests.remove(player.getUniqueId(), request);
                        player.sendMessage(color("&cRTP cancelled: you entered combat while a location was loading."));
                        return;
                    }
                    if (error != null || chunk == null) {
                        tryCandidate(player, world, request, minRadius, maxRadius, remaining - 1,
                                avoidDanger, avoidWater, cooldownSeconds);
                        return;
                    }

                    Location safe = findSafeLocation(world, chunk, x, z, avoidDanger, avoidWater);
                    if (safe == null || !world.getWorldBorder().isInside(safe)) {
                        tryCandidate(player, world, request, minRadius, maxRadius, remaining - 1,
                                avoidDanger, avoidWater, cooldownSeconds);
                        return;
                    }

                    safe.setYaw(player.getLocation().getYaw());
                    safe.setPitch(0.0f);
                    // Remove this request before teleportAsync: the teleport itself must not look like player movement.
                    if (!requests.remove(player.getUniqueId(), request)) return;
                    player.teleportAsync(safe).whenComplete((success, teleportError) ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (!player.isOnline()) return;
                                if (teleportError != null || !Boolean.TRUE.equals(success)) {
                                    player.sendMessage(color("&cRTP teleport failed. Please try again."));
                                    return;
                                }
                                cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldownSeconds * 1000L);
                                player.sendMessage(color("&bRTP &8» &aTeleported to a safe location."));
                                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.1f);
                            }));
                }));
    }

    private Location findSafeLocation(World world, org.bukkit.Chunk chunk, int x, int z,
                                      boolean avoidDanger, boolean avoidWater) {
        int bx = x & 15;
        int bz = z & 15;
        int maxY = world.getMaxHeight() - 2;
        for (int y = maxY; y > world.getMinHeight() + 1; y--) {
            Block floor = chunk.getBlock(bx, y - 1, bz);
            Block feet = chunk.getBlock(bx, y, bz);
            Block head = chunk.getBlock(bx, y + 1, bz);
            if (!floor.getType().isSolid() || !feet.isPassable() || !head.isPassable()) continue;
            if (avoidWater && (floor.isLiquid() || feet.isLiquid() || head.isLiquid())) continue;
            if (avoidDanger && isDangerous(floor.getType())) continue;
            return new Location(world, x + 0.5, y, z + 0.5);
        }
        return null;
    }

    private boolean isDangerous(Material material) {
        return switch (material) {
            case MAGMA_BLOCK, CACTUS, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE,
                    SWEET_BERRY_BUSH, POWDER_SNOW, WITHER_ROSE, POINTED_DRIPSTONE -> true;
            default -> false;
        };
    }

    private boolean isCurrent(Player player, Request request) {
        return requests.get(player.getUniqueId()) == request;
    }

    private void cancelRequest(Player player, String reason) {
        Request request = requests.remove(player.getUniqueId());
        if (request == null) return;
        if (request.warmupTask != null) request.warmupTask.cancel();
        if (reason != null && player.isOnline()) player.sendMessage(color(reason));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        // Ignore head rotation; any position change cancels both warmup and asynchronous search.
        if (event.getFrom().getX() == event.getTo().getX()
                && event.getFrom().getY() == event.getTo().getY()
                && event.getFrom().getZ() == event.getTo().getZ()) return;
        cancelRequest(event.getPlayer(), "&cRTP cancelled because you moved.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatDamage(EntityDamageByEntityEvent event) {
        Player victim = event.getEntity() instanceof Player p ? p : null;
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
        }
        Player attacker = source instanceof Player p ? p : null;
        if (victim != null && combat.isTagged(victim.getUniqueId())) {
            cancelRequest(victim, "&cRTP cancelled because you entered combat.");
        }
        if (attacker != null && combat.isTagged(attacker.getUniqueId())) {
            cancelRequest(attacker, "&cRTP cancelled because you entered combat.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Cancel any in-flight request, but retain the cooldown for this server session
        // so reconnecting cannot bypass it.
        cancelRequest(event.getPlayer(), null);
    }

    public void cancel(Player player) {
        cancelRequest(player, null);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
