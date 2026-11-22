package net.gasmp.core.shardshop;

import net.gasmp.core.GASMPCore;
import net.gasmp.core.data.PlayerDataStore;
import net.gasmp.core.gui.MenuPage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shards-only shop with category browsing, purchase previews and server-side debits. */
public final class ShardShopService implements Listener, CommandExecutor {
    private record Reward(Material material, int amount, String customContentId) { }
    private record Product(String key, String category, Material icon, long price,
                           String name, List<String> lore, List<Reward> rewards, String crateKeyId) { }

    private enum Stage { LIST, CONFIRM }

    private static final int[] CATEGORY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 46};
    private static final int[] CONTENT_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final class Holder implements InventoryHolder {
        private final Stage stage;
        private final int page;
        private final String productKey;
        private final String category;
        private final Map<Integer, String> productSlots = new HashMap<>();
        private final Map<Integer, String> categorySlots = new HashMap<>();
        private Inventory inventory;

        private Holder(Stage stage, int page, String productKey, String category) {
            this.stage = stage;
            this.page = page;
            this.productKey = productKey;
            this.category = category;
        }

        private void productAt(int slot, String key) { productSlots.put(slot, key); }
        private String productAt(int slot) { return productSlots.get(slot); }
        private void categoryAt(int slot, String key) { categorySlots.put(slot, key); }
        private String categoryAt(int slot) { return categorySlots.get(slot); }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final GASMPCore plugin;
    private final NamespacedKey crateKey;
    private final Map<String, Product> products = new LinkedHashMap<>();
    private final Set<UUID> purchasing = ConcurrentHashMap.newKeySet();
    private YamlConfiguration config;

    public ShardShopService(GASMPCore plugin) {
        this.plugin = plugin;
        this.crateKey = new NamespacedKey(plugin, "crate_key");
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "shard-shop.yml");
        if (!file.exists()) plugin.saveResource("shard-shop.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        products.clear();

        ConfigurationSection section = config.getConfigurationSection("items");
        if (section == null) {
            plugin.getLogger().warning("shard-shop.yml has no 'items' section.");
            return;
        }

        for (String rawKey : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(rawKey);
            if (entry == null) continue;

            Material icon = Material.matchMaterial(entry.getString("icon", "CHEST"));
            long price = entry.getLong("price", -1L);
            List<Reward> rewards = new ArrayList<>();
            boolean valid = icon != null && !icon.isAir() && icon.isItem() && price > 0L;

            for (String specification : entry.getStringList("contents")) {
                String[] parts = specification.split(":", 2);
                if (parts.length != 2) { valid = false; break; }
                Material material = Material.matchMaterial(parts[0].trim());
                int amount;
                try { amount = Integer.parseInt(parts[1].trim()); }
                catch (NumberFormatException exception) { amount = -1; }
                if (material == null || material.isAir() || !material.isItem()
                        || amount < 1 || amount > 64) {
                    valid = false;
                    break;
                }
                rewards.add(new Reward(material, amount, null));
            }

            for (String specification : entry.getStringList("custom-items")) {
                int amountSeparator = specification.lastIndexOf(':');
                if (amountSeparator <= 0 || amountSeparator == specification.length() - 1) {
                    valid = false;
                    break;
                }
                String contentId = specification.substring(0, amountSeparator).trim();
                int amount;
                try { amount = Integer.parseInt(specification.substring(amountSeparator + 1).trim()); }
                catch (NumberFormatException exception) { amount = -1; }
                if (!contentId.matches("[a-z0-9._-]+:[a-z0-9._/-]+") || amount < 1 || amount > 64) {
                    valid = false;
                    break;
                }
                rewards.add(new Reward(null, amount, contentId));
            }

            if (!valid || rewards.isEmpty()) {
                plugin.getLogger().warning("Ignoring invalid Shard Shop product: " + rawKey);
                continue;
            }

            String configuredCrateKey = entry.getString("crate-key");
            String crateKeyId = normalizeCrateKeyId(configuredCrateKey);
            if (configuredCrateKey != null && crateKeyId == null) valid = false;
            if (!valid) {
                plugin.getLogger().warning("Ignoring invalid Shard Shop product: " + rawKey);
                continue;
            }

            String key = rawKey.toLowerCase(Locale.ROOT);
            String category = normalizeCategory(entry.getString("category", "decor"));
            products.put(key, new Product(key, category, icon, price,
                    entry.getString("name", "&f" + rawKey),
                    List.copyOf(entry.getStringList("lore")), List.copyOf(rewards), crateKeyId));
        }
        plugin.getLogger().info("Loaded " + products.size() + " Shard Shop products.");
    }

    public void open(Player player, int requestedPage) {
        open(player, requestedPage, "all");
    }

    private void open(Player player, int requestedPage, String requestedCategory) {
        if (!config.getBoolean("settings.enabled", true)) {
            player.sendMessage(color("&cThe GASMP Shard Shop is disabled."));
            return;
        }

        String category = normalizeCategory(requestedCategory);
        List<Product> visible = products.values().stream()
                .filter(product -> category.equals("all") || product.category().equals(category))
                .toList();
        List<String> categories = new ArrayList<>();
        categories.add("all");
        products.values().stream().map(Product::category).distinct().sorted()
                .limit(CATEGORY_SLOTS.length - 1L).forEach(categories::add);

        int maxPage = Math.max(0, (visible.size() - 1) / CONTENT_SLOTS.length);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        Holder holder = new Holder(Stage.LIST, page, null, category);
        Inventory inventory = create(holder, 54,
                config.getString("settings.title", "&d&lGASMP &8• &5Shard Shop"));

        set(inventory, 4, Material.AMETHYST_SHARD, "&d&lSHARD SHOP",
                "&7Loading your balance...", "&7Build kits, lighting and decor.");

        for (int i = 0; i < Math.min(categories.size(), CATEGORY_SLOTS.length); i++) {
            String option = categories.get(i);
            int slot = CATEGORY_SLOTS[i];
            set(inventory, slot, categoryIcon(option),
                    (option.equals(category) ? "&a✓ " : "&d") + categoryLabel(option),
                    "&7Filter the catalogue", "&eClick to select");
            holder.categoryAt(slot, option);
        }

        int start = page * CONTENT_SLOTS.length;
        int end = Math.min(start + CONTENT_SLOTS.length, visible.size());
        for (int i = start, slotIndex = 0; i < end; i++, slotIndex++) {
            Product product = visible.get(i);
            int slot = CONTENT_SLOTS[slotIndex];
            inventory.setItem(slot, productIcon(product));
            holder.productAt(slot, product.key());
        }

        if (page > 0) set(inventory, 45, Material.ARROW, "&fPrevious page");
        set(inventory, 47, Material.BOOK, "&7Back to Economy");
        set(inventory, 49, Material.BARRIER, "&cClose");
        if (page < maxPage) set(inventory, 53, Material.ARROW, "&fNext page");
        player.openInventory(inventory);
        refreshBalance(player, holder);
    }

    private void openConfirm(Player player, Product product, String category, int page) {
        Holder holder = new Holder(Stage.CONFIRM, page, product.key(), category);
        Inventory inventory = create(holder, 27, "&8Confirm Shard Purchase");
        ItemStack preview = productIcon(product);
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(color("&8"));
            lore.add(color("&7Bundle contents:"));
            for (Reward reward : product.rewards()) {
                String rewardName = reward.material() != null
                        ? pretty(reward.material()) : prettyContentId(reward.customContentId());
                lore.add(color("&8• &f" + reward.amount() + "x " + rewardName));
            }
            lore.add(color("&8"));
            lore.add(color("&7Total: &d" + product.price() + " ✦ Shards"));
            meta.setLore(lore);
            preview.setItemMeta(meta);
        }
        set(inventory, 4, Material.AMETHYST_SHARD, "&d&lREVIEW REWARD",
                "&7Check the bundle before spending Shards.");
        inventory.setItem(13, preview);
        set(inventory, 11, Material.LIME_CONCRETE, "&a&lCONFIRM",
                "&7Spend " + product.price() + " Shards", "&7Receive this bundle.");
        set(inventory, 15, Material.RED_CONCRETE, "&c&lCANCEL", "&7Return to the shop.");
        set(inventory, 22, Material.BARRIER, "&cClose");
        player.openInventory(inventory);
    }

    private void refreshBalance(Player player, Holder holder) {
        plugin.getDataStore().getShards(player.getUniqueId()).thenAccept(balance -> {
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()
                            || player.getOpenInventory().getTopInventory().getHolder() != holder) return;
                    holder.inventory.setItem(4, item(Material.AMETHYST_SHARD,
                            "&d&lSHARD SHOP", "&7Your current balance:",
                            "&d&l" + balance + " ✦ Shards",
                            "&8Choose a kit and confirm before checkout."));
                });
            } catch (RuntimeException ignored) {
                // The plugin may be shutting down while the async balance query completes.
            }
        }).exceptionally(error -> {
            plugin.getLogger().warning("Could not load Shard Shop balance: " + rootMessage(error));
            return null;
        });
    }

    private void purchase(Player player, String key) {
        UUID playerId = player.getUniqueId();
        if (!purchasing.add(playerId)) {
            player.sendMessage(color("&cA Shard Shop purchase is already processing."));
            return;
        }

        Product product = products.get(key);
        if (product == null) {
            purchasing.remove(playerId);
            player.sendMessage(color("&cThat product is no longer available."));
            return;
        }
        ItemStack[] rewardItems = makeRewards(product);
        if (rewardItems == null) {
            purchasing.remove(playerId);
            player.sendMessage(color("&cThis custom item is unavailable. Make sure RP Engine has loaded the GASMP content pack."));
            return;
        }
        if (!hasSpace(player, rewardItems)) {
            purchasing.remove(playerId);
            player.sendMessage(color("&cMake enough inventory space before purchasing this bundle."));
            return;
        }

        String playerName = player.getName();
        player.closeInventory();
        plugin.getDataStore().changeShards(playerId, playerName, playerId,
                "PURCHASE:" + product.key(), product.price(), PlayerDataStore.ShardChange.TAKE,
                "Shard Shop purchase: " + product.key()).whenComplete((balance, error) -> {
            try {
                Bukkit.getScheduler().runTask(plugin, () ->
                        completePurchase(player, playerId, playerName, product, rewardItems, error));
            } catch (RuntimeException schedulingError) {
                // A successful debit must not be silently stranded if shutdown prevents
                // the inventory-delivery task from being scheduled.
                if (error == null) {
                    try {
                        refund(playerId, playerName, product, "Server stopped before bundle delivery.");
                    } catch (RuntimeException refundError) {
                        plugin.getLogger().severe("Could not queue shutdown refund for " + playerName
                                + ": " + refundError.getMessage());
                    }
                }
                purchasing.remove(playerId);
            }
        });
    }

    private void completePurchase(Player player, UUID playerId, String playerName,
                                  Product product, ItemStack[] rewardItems, Throwable debitError) {
        boolean delivered = false;
        try {
            if (debitError != null) {
                player.sendMessage(color("&cPurchase failed: &7" + rootMessage(debitError)));
                return;
            }
            if (!player.isOnline()) {
                refund(playerId, playerName, product, "Player disconnected before bundle delivery.");
                return;
            }
            if (!hasSpace(player, rewardItems)) {
                refund(playerId, playerName, product, "Inventory became full before delivery.");
                player.sendMessage(color("&cYour inventory changed during checkout. Shards were refunded."));
                return;
            }

            ItemStack[] before = cloneContents(player.getInventory().getStorageContents());
            try {
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(rewardItems);
                if (!leftovers.isEmpty()) {
                    player.getInventory().setStorageContents(before);
                    refund(playerId, playerName, product, "Inventory rejected one or more bundle items.");
                    player.sendMessage(color("&cDelivery failed. Shards were refunded."));
                    return;
                }
                delivered = true;
            } catch (RuntimeException deliveryError) {
                player.getInventory().setStorageContents(before);
                refund(playerId, playerName, product, "Bundle delivery threw an exception.");
                player.sendMessage(color("&cDelivery failed. Shards were refunded."));
                plugin.getLogger().warning("Shard Shop delivery failed for " + playerName + ": " + deliveryError.getMessage());
                return;
            }

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.75f, 1.25f);
            player.sendMessage(color("&aPurchased &f" + ChatColor.stripColor(color(product.name()))
                    + " &afor &d" + product.price() + " ✦ Shards."));
        } catch (RuntimeException exception) {
            if (!delivered && debitError == null) {
                refund(playerId, playerName, product, "Unexpected checkout exception.");
            }
            plugin.getLogger().warning("Shard Shop checkout error for " + playerName + ": " + exception.getMessage());
            if (player.isOnline()) {
                player.sendMessage(color(delivered
                        ? "&eYour bundle was delivered. A display notification failed."
                        : "&cCheckout failed. Contact staff if your balance changed."));
            }
        } finally {
            purchasing.remove(playerId);
        }
    }

    private void refund(UUID playerId, String playerName, Product product, String reason) {
        plugin.getDataStore().changeShards(playerId, playerName, null,
                "REFUND:" + product.key(), product.price(), PlayerDataStore.ShardChange.GIVE,
                "Shard Shop refund: " + reason).exceptionally(error -> {
            plugin.getLogger().severe("Shard Shop refund failed for " + playerName + ": " + rootMessage(error));
            return null;
        });
    }

    private boolean hasSpace(Player player, ItemStack[] rewardItems) {
        ItemStack[] simulated = cloneContents(player.getInventory().getStorageContents());
        for (ItemStack reward : rewardItems) {
            if (reward == null || reward.getType().isAir()) return false;
            int remaining = reward.getAmount();
            for (int i = 0; i < simulated.length && remaining > 0; i++) {
                ItemStack current = simulated[i];
                if (current == null || current.getType().isAir() || !current.isSimilar(reward)) continue;
                int room = Math.max(0, current.getMaxStackSize() - current.getAmount());
                int moved = Math.min(room, remaining);
                current.setAmount(current.getAmount() + moved);
                remaining -= moved;
            }
            for (int i = 0; i < simulated.length && remaining > 0; i++) {
                ItemStack current = simulated[i];
                if (current != null && !current.getType().isAir()) continue;
                int moved = Math.min(reward.getMaxStackSize(), remaining);
                ItemStack inserted = reward.clone();
                inserted.setAmount(moved);
                simulated[i] = inserted;
                remaining -= moved;
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    private ItemStack[] makeRewards(Product product) {
        ItemStack[] rewards = new ItemStack[product.rewards().size()];
        for (int i = 0; i < rewards.length; i++) {
            Reward reward = product.rewards().get(i);
            if (reward.customContentId() != null) {
                rewards[i] = createRPEngineItem(reward.customContentId(), reward.amount());
                if (rewards[i] == null) return null;
            } else {
                rewards[i] = new ItemStack(reward.material(), reward.amount());
            }
        }

        // Purchased crate keys must carry the exact persistent tag checked by CrateService.
        if (product.crateKeyId() != null) {
            if (rewards.length != 1 || rewards[0] == null || !rewards[0].hasItemMeta()) return null;
            ItemMeta meta = rewards[0].getItemMeta();
            meta.getPersistentDataContainer().set(crateKey, PersistentDataType.STRING, product.crateKeyId());
            rewards[0].setItemMeta(meta);
        }
        return rewards;
    }

    private String normalizeCrateKeyId(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9_-]+") ? normalized : null;
    }

    /** Resolves a custom item through RP Engine's public items() API without a compile-time dependency. */
    private ItemStack createRPEngineItem(String contentIdText, int amount) {
        Plugin engine = Bukkit.getPluginManager().getPlugin("RPEngine");
        if (engine == null || !engine.isEnabled()) return null;
        try {
            ClassLoader loader = engine.getClass().getClassLoader();
            Class<?> contentIdClass = Class.forName("ai.resourcepack.engine.api.ContentId", true, loader);
            Object parsed = contentIdClass.getMethod("parse", String.class).invoke(null, contentIdText);
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
            plugin.getLogger().fine("RP Engine item " + contentIdText + " is unavailable: " + ex.getMessage());
            return null;
        }
    }

    private String prettyContentId(String contentId) {
        if (contentId == null || contentId.isBlank()) return "Custom Item";
        int separator = contentId.indexOf(':');
        String path = separator >= 0 ? contentId.substring(separator + 1) : contentId;
        return categoryLabel(path.replace('_', ' ').replace('-', ' '));
    }

    private ItemStack[] cloneContents(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) copy[i] = source[i] == null ? null : source[i].clone();
        return copy;
    }

    private List<String> categories() {
        return products.values().stream().map(Product::category).distinct().sorted().toList();
    }

    private Material categoryIcon(String category) {
        return switch (category) {
            case "furniture" -> Material.ARMOR_STAND;
            case "garden" -> Material.FLOWER_POT;
            case "decor" -> Material.PAINTING;
            case "lighting" -> Material.LANTERN;
            case "building" -> Material.BRICKS;
            case "utility" -> Material.CHEST;
            case "crate_keys" -> Material.TRIPWIRE_HOOK;
            default -> Material.COMPASS;
        };
    }

    private String categoryLabel(String category) {
        if (category.equals("all")) return "All";
        StringBuilder label = new StringBuilder();
        for (String word : category.replace('-', ' ').replace('_', ' ').split("\\s+")) {
            if (word.isBlank()) continue;
            if (!label.isEmpty()) label.append(' ');
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }

    private String normalizeCategory(String value) {
        if (value == null || value.isBlank()) return "all";
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private ItemStack productIcon(Product product) {
        ItemStack icon = null;
        if (product.rewards().size() == 1) {
            Reward reward = product.rewards().getFirst();
            if (reward.customContentId() != null) {
                // Show the actual custom model in the catalogue when RP Engine is available.
                icon = createRPEngineItem(reward.customContentId(), 1);
            }
        }
        if (icon == null) icon = new ItemStack(product.icon());

        List<String> lore = new ArrayList<>();
        product.lore().forEach(line -> lore.add(color(line)));
        lore.add(color("&8"));
        lore.add(color("&7Category: &f" + categoryLabel(product.category())));
        lore.add(color("&7Price: &d" + product.price() + " ✦ Shards"));
        lore.add(color("&aClick to review purchase"));

        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(product.name()));
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private Inventory create(Holder holder, int size, String rawTitle) {
        Inventory inventory = Bukkit.createInventory(holder, size, color(rawTitle));
        holder.inventory = inventory;
        ItemStack black = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        Material[] topPalette = {
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.MAGENTA_STAINED_GLASS_PANE
        };
        for (int column = 0; column < 9; column++) {
            if (column == 0 || column == 8) {
                inventory.setItem(column, black.clone());
                inventory.setItem(size - 9 + column, black.clone());
            } else {
                inventory.setItem(column, item(topPalette[column % 2], " "));
                inventory.setItem(size - 9 + column, item(topPalette[(column + 1) % 2], " "));
            }
        }
        for (int row = 1; row < size / 9 - 1; row++) {
            inventory.setItem(row * 9, black.clone());
            inventory.setItem(row * 9 + 8, black.clone());
        }
        return inventory;
    }

    private void set(Inventory inventory, int slot, Material material, String name, String... lore) {
        inventory.setItem(slot, item(material, name, lore));
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
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

    private String pretty(Material material) {
        StringBuilder result = new StringBuilder();
        for (String word : material.name().toLowerCase(Locale.ROOT).split("_")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        if (slot == 49 || (holder.stage == Stage.CONFIRM && slot == 22)) {
            player.closeInventory();
            return;
        }

        if (holder.stage == Stage.LIST) {
            if (slot == 47) {
                plugin.getMenuService().open(player, MenuPage.SHARDS);
                return;
            }
            if (slot == 45) { open(player, holder.page - 1, holder.category); return; }
            if (slot == 53) { open(player, holder.page + 1, holder.category); return; }
            String category = holder.categoryAt(slot);
            if (category != null) { open(player, 0, category); return; }
            String key = holder.productAt(slot);
            if (key != null) {
                Product product = products.get(key);
                if (product != null) openConfirm(player, product, holder.category, holder.page);
            }
            return;
        }

        if (slot == 15) {
            open(player, holder.page, holder.category);
        } else if (slot == 11 && holder.productKey != null) {
            purchase(player, holder.productKey);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("The Shard Shop is a player command.");
            return true;
        }
        open(player, 0);
        return true;
    }
}
