package net.gasmp.core.integration;

import net.gasmp.core.GASMPCore;
import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stable public Bukkit API bridge for CraftEngine.
 *
 * Item IDs remain in the GASMP namespace (gasmp:<id>), keeping existing
 * crate reward and decoration-shop configuration stable across providers.
 */
public final class CraftEngineBridge {
    private final GASMPCore plugin;
    private final Set<String> missingItemsLogged = ConcurrentHashMap.newKeySet();

    public CraftEngineBridge(GASMPCore plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        Plugin craftEngine = Bukkit.getPluginManager().getPlugin("CraftEngine");
        return craftEngine != null && craftEngine.isEnabled();
    }

    public ItemStack createItem(String namespacedId) {
        if (namespacedId == null || namespacedId.isBlank() || !isAvailable()) return null;
        String id = normalize(namespacedId);
        try {
            BukkitItemDefinition definition = CraftEngineItems.byId(id);
            if (definition == null) {
                if (missingItemsLogged.add(id)) {
                    plugin.getLogger().warning("CraftEngine item is not loaded: " + id
                            + ". Check plugins/CraftEngine/resources/gasmp and run /ce reload all.");
                }
                return null;
            }
            ItemStack item = definition.buildBukkitItem();
            return item == null ? null : item.clone();
        } catch (Throwable error) {
            plugin.getLogger().warning("Could not create CraftEngine item '" + id + "': "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
            return null;
        }
    }

    public String idOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !isAvailable()) return null;
        try {
            Key id = CraftEngineItems.getCustomItemId(item);
            return id == null ? null : id.toString().toLowerCase(Locale.ROOT);
        } catch (Throwable error) {
            plugin.getLogger().fine("CraftEngine item identity lookup failed: " + error.getMessage());
            return null;
        }
    }

    public String furnitureIdAt(Entity entity) {
        if (entity == null || !isAvailable()) return null;
        try {
            BukkitFurniture furniture = CraftEngineFurniture.getLoadedFurnitureByMetaEntity(entity);
            if (furniture == null) {
                furniture = CraftEngineFurniture.getLoadedFurnitureByCollider(entity);
            }
            return furniture == null ? null : furniture.id().toString().toLowerCase(Locale.ROOT);
        } catch (Throwable error) {
            plugin.getLogger().fine("CraftEngine furniture lookup failed: " + error.getMessage());
            return null;
        }
    }

    private String normalize(String namespacedId) {
        String id = namespacedId.trim().toLowerCase(Locale.ROOT);
        return id.contains(":") ? id : "gasmp:" + id;
    }
}
