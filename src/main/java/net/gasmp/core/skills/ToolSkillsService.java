package net.gasmp.core.skills;

import net.gasmp.core.GASMPCore;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vein miner and tree capitator use Player#breakBlock for every extra block.
 * Paper fires a real BlockBreakEvent for those blocks, so region protection and
 * other cancellation plugins can veto each additional break.
 */
public final class ToolSkillsService implements Listener {
    private final GASMPCore plugin;
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    public ToolSkillsService(GASMPCore plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        if (processing.contains(player.getUniqueId())) return;
        if (plugin.getConfig().getBoolean("skills.require-sneak", true) && !player.isSneaking()) return;

        Block origin = event.getBlock();
        String name = origin.getType().name();
        if (plugin.getConfig().getBoolean("skills.vein-miner.enabled", true)
                && player.hasPermission("gasmpcore.skills.veinminer")
                && isOre(origin.getType()) && isPickaxe(player.getInventory().getItemInMainHand())) {
            int max = Math.max(1, Math.min(64, plugin.getConfig().getInt("skills.vein-miner.max-blocks", 32)));
            breakConnected(player, origin, block -> block.getType() == origin.getType(), max, false);
        } else if (plugin.getConfig().getBoolean("skills.tree-capitator.enabled", true)
                && player.hasPermission("gasmpcore.skills.treecap")
                && isLog(origin.getType()) && isAxe(player.getInventory().getItemInMainHand())) {
            int max = Math.max(1, Math.min(128, plugin.getConfig().getInt("skills.tree-capitator.max-blocks", 64)));
            breakConnected(player, origin, block -> isLog(block.getType()), max, true);
        }
    }

    private void breakConnected(Player player, Block origin, java.util.function.Predicate<Block> predicate,
                                int maxBlocks, boolean verticalTree) {
        if (!processing.add(player.getUniqueId())) return;
        try {
            Queue<Block> queue = new ArrayDeque<>();
            Set<String> seen = new HashSet<>();
            queue.add(origin);
            seen.add(key(origin));
            int broken = 0;
            while (!queue.isEmpty() && broken < maxBlocks) {
                Block current = queue.remove();
                if (current.equals(origin)) {
                    // The initiating BlockBreakEvent is already processing the origin block.
                    // Expand outward from it instead of skipping the root without visiting neighbours.
                    for (Block neighbor : neighbors(current)) {
                        if (seen.add(key(neighbor)) && predicate.test(neighbor)) queue.add(neighbor);
                    }
                    continue;
                }
                if (current.getType().isAir() || !predicate.test(current)) continue;
                if (verticalTree && Math.abs(current.getY() - origin.getY()) > 24) continue;

                // Player#breakBlock fires a real cancellable BlockBreakEvent for every extra
                // block. If protection cancels it, do not traverse through that denied block.
                if (!player.breakBlock(current)) continue;
                broken++;
                for (Block neighbor : neighbors(current)) {
                    if (seen.add(key(neighbor)) && predicate.test(neighbor)) queue.add(neighbor);
                }
            }
            if (broken > 0 && player.isOnline()) {
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        verticalTree ? "Tree Capitator: " + broken + " extra logs" : "Vein Miner: " + broken + " extra ores"));
            }
        } finally {
            processing.remove(player.getUniqueId());
        }
    }

    private List<Block> neighbors(Block block) {
        return List.of(block.getRelative(1,0,0), block.getRelative(-1,0,0),
                block.getRelative(0,1,0), block.getRelative(0,-1,0),
                block.getRelative(0,0,1), block.getRelative(0,0,-1));
    }
    private String key(Block b) { return b.getWorld().getUID() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ(); }
    private boolean isOre(Material material) {
        String n = material.name();
        return n.endsWith("_ORE") || n.equals("NETHER_GOLD_ORE") || n.equals("ANCIENT_DEBRIS");
    }
    private boolean isLog(Material material) {
        String n = material.name();
        return (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_STEM") || n.endsWith("_HYPHAE"))
                && !n.contains("STRIPPED");
    }
    private boolean isPickaxe(ItemStack item) { return item != null && item.getType().name().endsWith("_PICKAXE"); }
    private boolean isAxe(ItemStack item) { return item != null && item.getType().name().endsWith("_AXE"); }
}
