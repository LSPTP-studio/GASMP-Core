package net.gasmp.core.integration;

import net.gasmp.core.GASMPCore;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Reflection-only ItemsAdder 4 bridge. Keeps GASMP Core buildable without
 * shipping the premium ItemsAdder API jar, while using ItemsAdder's public
 * CustomStack, CustomFurniture and CustomBlock APIs at runtime.
 */
public final class ItemsAdderBridge {
    private final GASMPCore plugin;

    public ItemsAdderBridge(GASMPCore plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        Plugin itemsAdder = plugin();
        return itemsAdder != null && itemsAdder.isEnabled();
    }

    public boolean isDataReady() {
        if (!isAvailable()) return false;
        try {
            Class<?> entry = apiClass("dev.lone.itemsadder.api.ItemsAdder");
            Method method = entry.getMethod("areItemsLoaded");
            Object result = method.invoke(null);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // ItemsAdder 4 recommends checking load events; a successful
            // CustomStack lookup remains the authoritative per-item check.
            return true;
        }
    }

    public ItemStack createItem(String namespacedId) {
        if (namespacedId == null || namespacedId.isBlank()) return null;
        try {
            Class<?> customStack = apiClass("dev.lone.itemsadder.api.CustomStack");
            Object stack = customStack.getMethod("getInstance", String.class)
                    .invoke(null, normalize(namespacedId));
            if (stack == null) return null;
            Object item = customStack.getMethod("getItemStack").invoke(stack);
            return item instanceof ItemStack result ? result.clone() : null;
        } catch (ReflectiveOperationException | LinkageError error) {
            plugin.getLogger().warning("ItemsAdder could not create '" + namespacedId + "': " + error.getMessage());
            return null;
        }
    }

    public String idOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !isAvailable()) return null;
        try {
            Class<?> customStack = apiClass("dev.lone.itemsadder.api.CustomStack");
            Object stack = customStack.getMethod("byItemStack", ItemStack.class).invoke(null, item);
            if (stack == null) return null;
            Object id = customStack.getMethod("getNamespacedID").invoke(stack);
            return id == null ? null : String.valueOf(id).toLowerCase(java.util.Locale.ROOT);
        } catch (ReflectiveOperationException | LinkageError error) {
            plugin.getLogger().fine("ItemsAdder item identity lookup failed: " + error.getMessage());
            return null;
        }
    }

    public String furnitureIdAt(Entity entity) {
        if (entity == null || !isAvailable()) return null;
        try {
            Class<?> furniture = apiClass("dev.lone.itemsadder.api.CustomFurniture");
            Object custom = furniture.getMethod("byAlreadySpawned", Entity.class).invoke(null, entity);
            return namespacedId(custom);
        } catch (ReflectiveOperationException | LinkageError error) {
            plugin.getLogger().fine("ItemsAdder furniture entity lookup failed: " + error.getMessage());
            return null;
        }
    }

    public String furnitureIdAt(Block block) {
        if (block == null || !isAvailable()) return null;
        try {
            Class<?> furniture = apiClass("dev.lone.itemsadder.api.CustomFurniture");
            Object custom = furniture.getMethod("byAlreadySpawned", Block.class).invoke(null, block);
            if (custom != null) return namespacedId(custom);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Some IA releases do not expose the block overload; continue to custom blocks.
        }
        return customBlockIdAt(block);
    }

    public String customBlockIdAt(Block block) {
        if (block == null || !isAvailable()) return null;
        try {
            Class<?> customBlock = apiClass("dev.lone.itemsadder.api.CustomBlock");
            Object custom = customBlock.getMethod("byAlreadyPlaced", Block.class).invoke(null, block);
            return namespacedId(custom);
        } catch (ReflectiveOperationException | LinkageError error) {
            plugin.getLogger().fine("ItemsAdder custom block lookup failed: " + error.getMessage());
            return null;
        }
    }

    private String namespacedId(Object customStack) throws ReflectiveOperationException {
        if (customStack == null) return null;
        Class<?> customStackApi = apiClass("dev.lone.itemsadder.api.CustomStack");
        Object id = customStackApi.getMethod("getNamespacedID").invoke(customStack);
        return id == null ? null : String.valueOf(id).toLowerCase(java.util.Locale.ROOT);
    }

    private Plugin plugin() {
        return Bukkit.getPluginManager().getPlugin("ItemsAdder");
    }

    private Class<?> apiClass(String name) throws ClassNotFoundException {
        Plugin itemsAdder = plugin();
        if (itemsAdder == null) throw new ClassNotFoundException("ItemsAdder is not installed");
        return Class.forName(name, true, itemsAdder.getClass().getClassLoader());
    }

    private String normalize(String id) {
        String normalized = id.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.contains(":") ? normalized : "gasmp:" + normalized;
    }
}
