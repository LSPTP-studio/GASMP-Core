package net.gasmp.core.crates;

import net.gasmp.core.GASMPCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public final class CrateMenuService implements Listener, CommandExecutor {
    private static final int SIZE = 54;
    private static final int[] CRATE_SLOTS = {20, 22, 24, 29, 31, 33};
    private static final int[] DETAIL_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private final GASMPCore plugin;

    public CrateMenuService(GASMPCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This menu can only be opened in-game.");
            return true;
        }
        openCrates(player);
        return true;
    }

    public void openCrates(Player player) {
        YamlConfiguration yaml = load();
        ConfigurationSection crates = yaml.getConfigurationSection("crates");
        Holder holder = new Holder(null);
        Inventory inv = Bukkit.createInventory(holder, SIZE, color("&8GASMP &8» &6Crate Catalogue"));
        holder.inventory = inv;
        frame(inv, Material.ORANGE_STAINED_GLASS_PANE);
        inv.setItem(4, item(Material.CHEST, "&6&lGASMP CRATE CATALOGUE",
                "&7Browse available crates and inspect their rewards.",
                "&7Opening still requires the matching physical key.",
                "&7Buy crate keys with Shards in the Shard Shop.",
                "&8Odds are calculated from configured reward weights."));
        if (crates == null || crates.getKeys(false).isEmpty()) {
            inv.setItem(22, item(Material.BARRIER, "&cNo crates configured",
                    "&7Ask staff when the next crate is available."));
        } else {
            List<String> ids = new ArrayList<>(crates.getKeys(false));
            ids.sort(String.CASE_INSENSITIVE_ORDER);
            holder.crateIds.addAll(ids);
            for (int i = 0; i < Math.min(ids.size(), CRATE_SLOTS.length); i++) {
                String id = ids.get(i);
                ConfigurationSection crate = crates.getConfigurationSection(id);
                int rewardCount = crate == null || crate.getConfigurationSection("rewards") == null
                        ? 0 : crate.getConfigurationSection("rewards").getKeys(false).size();
                String displayName = crate == null ? fallbackName(id) : crate.getString("display-name", fallbackName(id));
                inv.setItem(CRATE_SLOTS[i], item(iconFor(id, crate), displayName,
                        "&7Rewards: &f" + rewardCount,
                        "&7Requires its matching physical key",
                        "&eClick to inspect rewards"));
                holder.slotToCrate.put(CRATE_SLOTS[i], id);
            }
            if (ids.size() > CRATE_SLOTS.length) {
                inv.setItem(40, item(Material.BOOK, "&eMore crates configured",
                        "&7There are " + ids.size() + " crates in total.",
                        "&7Ask staff to add more catalogue pages."));
            }
        }
        inv.setItem(49, item(Material.BARRIER, "&cClose", "&7Close this menu"));
        player.openInventory(inv);
    }

    private void openDetails(Player player, String crateId) {
        YamlConfiguration yaml = load();
        ConfigurationSection crate = yaml.getConfigurationSection("crates." + crateId);
        if (crate == null) { player.sendMessage(color("&cThat crate is no longer configured.")); openCrates(player); return; }
        ConfigurationSection rewards = crate.getConfigurationSection("rewards");
        Holder holder = new Holder(crateId);
        String displayName = crate.getString("display-name", fallbackName(crateId));
        Inventory inv = Bukkit.createInventory(holder, SIZE, color("&8GASMP &8» ") + color(displayName));
        holder.inventory = inv;
        frame(inv, Material.ORANGE_STAINED_GLASS_PANE);
        String keyName = crate.getString("key-name", "&d&l" + crateId.toUpperCase(Locale.ROOT) + " KEY");
        inv.setItem(4, item(iconFor(crateId, crate), displayName,
                "&7Reward pool: &f" + (rewards == null ? 0 : rewards.getKeys(false).size()),
                "&7Required key: " + keyName,
                "&7Buy the matching key with Shards in the Shard Shop.",
                "&7Find the physical crate block in the world.",
                "&7Each opening selects one weighted reward.",
                "&8Percentages below are derived from valid reward weights."));
        if (rewards == null || rewards.getKeys(false).isEmpty()) {
            inv.setItem(22, item(Material.BARRIER, "&cNo rewards configured"));
        } else {
            List<Reward> entries = new ArrayList<>();
            long total = 0;
            for (String key : rewards.getKeys(false)) {
                ConfigurationSection row = rewards.getConfigurationSection(key);
                if (row == null) continue;
                int weight = row.getInt("weight", 0);
                String itemId = row.getString("item", "");
                String type = row.getString("type", "").toLowerCase(Locale.ROOT);
                if (weight <= 0 || !type.equals("cosmetic")
                        || !itemId.matches("[a-z0-9._-]+:[a-z0-9._/-]+")
                        || total + weight > Integer.MAX_VALUE) continue;
                entries.add(new Reward(key, itemId, row.getString("display-name", key),
                        weight, row.getString("rarity", "common")));
                total += weight;
            }
            entries.sort(Comparator.comparingInt(Reward::weight).reversed());
            holder.totalWeight = total;
            for (int i = 0; i < Math.min(entries.size(), DETAIL_SLOTS.length); i++) {
                Reward reward = entries.get(i);
                double chance = total <= 0 ? 0 : reward.weight() * 100.0 / total;
                inv.setItem(DETAIL_SLOTS[i], rewardItem(reward, chance));
            }
        }
        inv.setItem(45, item(Material.ARROW, "&fBack to crates", "&7Return to the catalogue"));
        inv.setItem(49, item(Material.BARRIER, "&cClose"));
        player.openInventory(inv);
    }

    private ItemStack rewardItem(Reward reward, double chance) {
        String rarity = reward.rarity().toLowerCase(Locale.ROOT);
        String rarityColor = switch (rarity) {
            case "rare" -> "&b";
            case "epic" -> "&d";
            case "legendary" -> "&6";
            default -> "&7";
        };
        ItemStack icon = createRPEngineItem(reward.itemId(), 1);
        if (icon == null) icon = new ItemStack(Material.PAPER);

        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(rarityColor + "&l" + reward.displayName()));
            meta.setLore(List.of(
                    color("&7Reward type: &fWearable cosmetic"),
                    color("&7Rarity: " + rarityColor + title(rarity)),
                    String.format(Locale.US, color("&7Drop chance: &a%.2f%%"), chance),
                    color("&8Cosmetic ID: " + reward.itemId())));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    /** Resolves a custom item through RP Engine's public items() API without a compile-time dependency. */
    private ItemStack createRPEngineItem(String namespacedId, int amount) {
        Plugin engine = Bukkit.getPluginManager().getPlugin("RPEngine");
        if (engine == null || !engine.isEnabled()) return null;
        try {
            ClassLoader loader = engine.getClass().getClassLoader();
            Class<?> contentIdClass = Class.forName("ai.resourcepack.engine.api.ContentId", true, loader);
            Object parsed = contentIdClass.getMethod("parse", String.class).invoke(null, namespacedId);
            if (!(parsed instanceof Optional<?> optional) || optional.isEmpty()) return null;
            Object contentId = optional.get();
            Object items = engine.getClass().getMethod("items").invoke(engine);
            Object created = null;
            for (java.lang.reflect.Method method : items.getClass().getMethods()) {
                if (!method.getName().equals("create")) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types.length == 2 && types[0].equals(contentIdClass) && types[1] == int.class) {
                    created = method.invoke(items, contentId, amount);
                    break;
                }
                if (types.length == 1 && types[0].equals(contentIdClass)) {
                    created = method.invoke(items, contentId);
                    break;
                }
            }
            ItemStack stack = null;
            if (created instanceof Optional<?> resultOptional && resultOptional.isPresent()) {
                Object value = resultOptional.get();
                if (value instanceof ItemStack itemStack) stack = itemStack;
            } else if (created instanceof ItemStack itemStack) {
                stack = itemStack;
            }
            if (stack == null || amount < 1) return null;
            ItemStack result = stack.clone();
            if (amount > result.getMaxStackSize()) return null;
            result.setAmount(amount);
            return result;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().fine("RP Engine item " + namespacedId + " is unavailable: " + ex.getMessage());
            return null;
        }
    }

    private YamlConfiguration load() {
        File file = new File(plugin.getDataFolder(), "crates.yml");
        return YamlConfiguration.loadConfiguration(file);
    }

    private Material iconFor(String id, ConfigurationSection crate) {
        String configured = crate == null ? null : crate.getString("icon");
        if (configured != null) {
            Material material = Material.matchMaterial(configured);
            if (material != null && material.isItem() && !material.isAir()) return material;
        }
        String key = id.toLowerCase(Locale.ROOT);
        if (key.contains("legend")) return Material.DRAGON_EGG;
        if (key.contains("vote")) return Material.TRIPWIRE_HOOK;
        if (key.contains("season")) return Material.NETHER_STAR;
        return Material.CHEST;
    }

    private String fallbackName(String id) {
        return "&d&l" + id.replace('_', ' ').toUpperCase(Locale.ROOT) + " CRATE";
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 45 && holder.crateId != null) { openCrates(player); return; }
        if (holder.crateId == null) {
            String crate = holder.slotToCrate.get(slot);
            if (crate != null) openDetails(player, crate);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private void frame(Inventory inv, Material accent) {
        ItemStack dark = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int row = 0; row < 6; row++) {
            inv.setItem(row * 9, dark);
            inv.setItem(row * 9 + 8, dark);
        }
        for (int col = 0; col < 9; col++) {
            inv.setItem(col, item(col % 2 == 0 ? accent : Material.BLACK_STAINED_GLASS_PANE, " "));
            inv.setItem(45 + col, item(col % 2 == 0 ? Material.BLACK_STAINED_GLASS_PANE : accent, " "));
        }
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore.length > 0) {
                List<String> lines = new ArrayList<>();
                for (String line : lore) lines.add(color(line));
                meta.setLore(lines);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String title(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? normalized : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }

    private record Reward(String key, String itemId, String displayName, int weight, String rarity) { }

    private static final class Holder implements InventoryHolder {
        private final String crateId;
        private final List<String> crateIds = new ArrayList<>();
        private final Map<Integer, String> slotToCrate = new HashMap<>();
        private long totalWeight;
        private Inventory inventory;
        private Holder(String crateId) { this.crateId = crateId; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
