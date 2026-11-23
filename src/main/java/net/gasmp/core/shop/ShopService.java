package net.gasmp.core.shop;

import net.gasmp.core.GASMPCore;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main-thread Vault transactions with inventory snapshot rollback. SQLite audit writes
 * are asynchronous and do not block inventory/economy event handling.
 */
public final class ShopService implements Listener {
    private record Entry(String key, String category, Material material, int amount, double price,
                         String displayName, List<String> lore, String permission) { }
    private record Category(String key, String label, Material icon) { }

    // Category tabs occupy row 2; 21 listing slots remain below them.
    private static final int[] CATEGORY_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] CONTENT = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final double MONEY_EPSILON = 0.005d;

    private final GASMPCore plugin;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Set<UUID> purchasing = ConcurrentHashMap.newKeySet();
    private YamlConfiguration config;

    public ShopService(GASMPCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "shop.yml");
        if (!file.exists()) plugin.saveResource("shop.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        entries.clear();
        ConfigurationSection section = config.getConfigurationSection("items");
        if (section == null) {
            plugin.getLogger().warning("shop.yml has no 'items' section; the Coins shop will be empty.");
            return;
        }

        int maxQuantity = Math.max(1, Math.min(64, config.getInt("settings.max-quantity", 64)));
        for (String rawKey : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(rawKey);
            if (item == null) continue;
            String key = rawKey.toLowerCase(Locale.ROOT);
            Material material = Material.matchMaterial(item.getString("material", "STONE"));
            int amount = item.getInt("amount", 1);
            double price = normalizePrice(item.getDouble("price", -1.0));
            if (material == null || material.isAir() || !material.isItem()
                    || amount < 1 || amount > maxQuantity || !Double.isFinite(price) || price <= 0.0) {
                plugin.getLogger().warning("Ignoring invalid shop entry: " + rawKey);
                continue;
            }
            String category = normalizeCategory(item.getString("category", "misc"));
            String display = item.getString("display-name", "&f" + material.name());
            List<String> lore = item.getStringList("lore");
            String permission = item.getString("permission", "").trim();
            entries.put(key, new Entry(key, category, material, amount, price, display, List.copyOf(lore), permission));
        }
        plugin.getLogger().info("Loaded " + entries.size() + " valid Coins shop entries.");
    }

    public void open(Player player, int page) {
        // Legacy /shop is now the Shard Shop; GASMP no longer exposes the Coins catalogue.
        plugin.getMenuService().open(player, net.gasmp.core.gui.MenuPage.KEY_SHOP);
    }

    private void open(Player player, int page, String selectedCategory) {
        if (!config.getBoolean("settings.enabled", true)) {
            player.sendMessage(color("&cThe GASMP shop is disabled."));
            return;
        }

        String category = normalizeCategory(selectedCategory);
        List<Entry> visible = visibleEntries(player, category);
        List<Category> categories = categoriesFor(player);
        int maxPage = Math.max(0, (visible.size() - 1) / CONTENT.length);
        int safePage = Math.max(0, Math.min(page, maxPage));
        ShopHolder holder = new ShopHolder(ShopHolder.Stage.LIST, safePage, null, category);
        Inventory inventory = create(holder, 54, color(config.getString("settings.title", "&9&lGASMP Shop")));

        set(inventory, 4, Material.GOLD_INGOT, "&e&lCoins Shop",
                "&7Provider: &f" + (plugin.getEconomyBridge().isAvailable()
                        ? plugin.getEconomyBridge().currencyNamePlural() : "Unavailable"),
                "&7Category: &f" + categoryLabel(category),
                "&7Items: &f" + visible.size());

        for (int i = 0; i < Math.min(categories.size(), CATEGORY_SLOTS.length); i++) {
            Category option = categories.get(i);
            int slot = CATEGORY_SLOTS[i];
            String marker = option.key().equals(category) ? " &a✓" : "";
            set(inventory, slot, option.icon(), "&b&l" + option.label() + marker,
                    "&7Filter the catalogue", "&eClick to select");
            holder.categoryAt(slot, option.key());
        }

        int start = safePage * CONTENT.length;
        int end = Math.min(start + CONTENT.length, visible.size());
        int index = 0;
        for (int i = start; i < end; i++) {
            Entry entry = visible.get(i);
            int slot = CONTENT[index++];
            ItemStack item = makeItem(entry);
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta != null && meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(color("&8"));
            lore.add(color("&7Category: &f" + categoryLabel(entry.category())));
            lore.add(color("&ePrice: &6" + money(entry.price()) + " " + plugin.getEconomyBridge().currencyNamePlural()));
            lore.add(color("&aClick to review purchase"));
            if (meta != null) { meta.setLore(lore); item.setItemMeta(meta); }
            inventory.setItem(slot, item);
            holder.entryAt(slot, entry.key());
        }

        if (safePage > 0) set(inventory, 45, Material.ARROW, "&fPrevious page");
        set(inventory, 47, Material.BOOK, "&7Back to main menu");
        set(inventory, 49, Material.BARRIER, "&cClose");
        if (safePage < maxPage) set(inventory, 53, Material.ARROW, "&fNext page");
        player.openInventory(inventory);
    }

    private void openConfirm(Player player, Entry entry, String category, int page) {
        ShopHolder holder = new ShopHolder(ShopHolder.Stage.CONFIRM, page, entry.key(), category);
        Inventory inventory = create(holder, 27, color("&8Confirm Purchase"));
        ItemStack preview = makeItem(entry);
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(color("&8"));
            lore.add(color("&7Total: &6" + money(entry.price()) + " " + plugin.getEconomyBridge().currencyNamePlural()));
            meta.setLore(lore);
            preview.setItemMeta(meta);
        }
        inventory.setItem(13, preview);
        set(inventory, 11, Material.LIME_CONCRETE, "&a&lCONFIRM", "&7Charge and deliver this item.");
        set(inventory, 15, Material.RED_CONCRETE, "&c&lCANCEL", "&7Return to the shop.");
        set(inventory, 22, Material.BARRIER, "&cClose");
        player.openInventory(inventory);
    }

    private void purchase(Player player, String itemKey, String category, int page) {
        if (!purchasing.add(player.getUniqueId())) {
            player.sendMessage(color("&cYour previous purchase is still processing."));
            return;
        }

        ItemStack[] inventoryBefore = null;
        boolean itemInserted = false;
        boolean paymentConfirmed = false;
        try {
            if (!player.isOnline()) return;
            Entry entry = entries.get(itemKey);
            if (entry == null) {
                player.sendMessage(color("&cThis item is no longer available."));
                record(player, itemKey, "FAILED", 0.0, "Catalogue entry missing at confirmation.");
                return;
            }
            if (!entry.permission().isBlank() && !player.hasPermission(entry.permission())) {
                player.sendMessage(color("&cYou no longer have permission to purchase this item."));
                return;
            }
            plugin.getEconomyBridge().refresh();
            if (!plugin.getEconomyBridge().isAvailable()) {
                player.sendMessage(color("&cNo Vault-compatible economy provider is installed."));
                return;
            }
            if (!plugin.getEconomyBridge().has(player, entry.price())) {
                player.sendMessage(color("&cYou do not have enough "
                        + plugin.getEconomyBridge().currencyNamePlural() + "."));
                record(player, entry.key(), "INSUFFICIENT_FUNDS", entry.price(), "Vault has() returned false.");
                return;
            }

            // Capture the balance before touching inventory. If this call fails, no item has been delivered.
            double balanceBefore = plugin.getEconomyBridge().getBalance(player);
            ItemStack reward = makeItem(entry);
            inventoryBefore = cloneContents(player.getInventory().getStorageContents());
            itemInserted = true;
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
            if (!leftovers.isEmpty()) {
                player.getInventory().setStorageContents(inventoryBefore);
                itemInserted = false;
                player.sendMessage(color("&cYour inventory does not have enough space."));
                record(player, entry.key(), "NO_SPACE", entry.price(), "Inventory insertion returned leftovers; inventory restored.");
                return;
            }

            EconomyResponse response;
            try {
                response = plugin.getEconomyBridge().withdraw(player, entry.price());
            } catch (RuntimeException withdrawException) {
                player.getInventory().setStorageContents(inventoryBefore);
                itemInserted = false;
                boolean refunded = compensateAmbiguousWithdrawal(player, balanceBefore, entry.price());
                String outcome = refunded ? "WITHDRAW_EXCEPTION_REFUNDED" : "WITHDRAW_EXCEPTION_UNCONFIRMED";
                record(player, entry.key(), outcome, entry.price(), withdrawException.getClass().getSimpleName() + ": " + safeMessage(withdrawException));
                player.sendMessage(color(refunded
                        ? "&cPayment provider errored. Any detected deduction was refunded; retry shortly."
                        : "&cPayment could not be confirmed. Check your balance and contact staff before retrying."));
                return;
            }

            if (response == null || !response.transactionSuccess()) {
                player.getInventory().setStorageContents(inventoryBefore);
                itemInserted = false;
                boolean refunded = compensateAmbiguousWithdrawal(player, balanceBefore, entry.price());
                String providerReason = response == null ? "Vault provider returned null response." : response.errorMessage;
                record(player, entry.key(), refunded ? "WITHDRAW_REJECTED_REFUNDED" : "WITHDRAW_REJECTED",
                        entry.price(), providerReason);
                player.sendMessage(color(refunded
                        ? "&cPayment was declined after an observed debit; Coins were refunded."
                        : "&cPayment was declined: &7" + (response == null ? "No response from provider." : response.errorMessage)));
                return;
            }

            paymentConfirmed = true;
            itemInserted = false;
            record(player, entry.key(), "SUCCESS", entry.price(), "Vault withdrawal and item delivery completed.");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            player.sendMessage(color("&aPurchased &f" + entry.displayName() + " &afor &6"
                    + money(entry.price()) + " " + plugin.getEconomyBridge().currencyNamePlural() + "&a."));
        } catch (RuntimeException exception) {
            if (!paymentConfirmed && itemInserted && inventoryBefore != null) {
                try {
                    player.getInventory().setStorageContents(inventoryBefore);
                } catch (RuntimeException rollbackException) {
                    plugin.getLogger().severe("Could not restore inventory after shop failure for "
                            + player.getName() + ": " + safeMessage(rollbackException));
                }
            }
            plugin.getLogger().warning("Shop purchase processing failed for " + player.getName()
                    + ": " + exception.getClass().getSimpleName() + ": " + safeMessage(exception));
            player.sendMessage(color(paymentConfirmed
                    ? "&ePurchase completed, but a post-purchase notification failed. Your item and payment are kept."
                    : "&cThe shop encountered an error. Your inventory was restored where possible; contact staff if your balance changed."));
        } finally {
            purchasing.remove(player.getUniqueId());
        }
    }

    /**
     * Some broken economy providers throw after partially mutating a balance. We only
     * compensate when the observed balance drop matches the complete requested charge.
     * The provider call is synchronous on the main thread; unrelated transactions cannot
     * normally interleave with this small critical section.
     */
    private boolean compensateAmbiguousWithdrawal(Player player, double balanceBefore, double price) {
        try {
            double after = plugin.getEconomyBridge().getBalance(player);
            double decrease = balanceBefore - after;
            if (!Double.isFinite(decrease) || Math.abs(decrease - price) > MONEY_EPSILON) return false;
            EconomyResponse refund = plugin.getEconomyBridge().deposit(player, price);
            return refund != null && refund.transactionSuccess();
        } catch (RuntimeException refundException) {
            plugin.getLogger().severe("Could not reconcile an ambiguous shop withdrawal for " + player.getName()
                    + ": " + safeMessage(refundException));
            return false;
        }
    }

    private List<Entry> visibleEntries(Player player, String category) {
        return entries.values().stream()
                .filter(entry -> category.equals("all") || entry.category().equals(category))
                .filter(entry -> entry.permission().isBlank() || player.hasPermission(entry.permission()))
                .toList();
    }

    private List<Category> categoriesFor(Player player) {
        List<Category> categories = new ArrayList<>();
        categories.add(new Category("all", "All", Material.COMPASS));
        entries.values().stream()
                .filter(entry -> entry.permission().isBlank() || player.hasPermission(entry.permission()))
                .map(Entry::category).distinct().sorted()
                .limit(CATEGORY_SLOTS.length - 1L)
                .forEach(key -> categories.add(new Category(key, categoryLabel(key), categoryIcon(key))));
        return categories;
    }

    private Material categoryIcon(String category) {
        return switch (category) {
            case "blocks" -> Material.BRICKS;
            case "combat" -> Material.IRON_SWORD;
            case "tools" -> Material.DIAMOND_PICKAXE;
            case "food" -> Material.COOKED_BEEF;
            case "materials" -> Material.IRON_INGOT;
            case "special" -> Material.ENDER_CHEST;
            default -> Material.CHEST;
        };
    }

    private String categoryLabel(String category) {
        if (category == null || category.equals("all")) return "All";
        String[] words = category.replace('-', ' ').replace('_', ' ').split("\\s+");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!label.isEmpty()) label.append(' ');
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return "all";
        return category.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) copy[i] = contents[i] == null ? null : contents[i].clone();
        return copy;
    }

    private void record(Player player, String itemKey, String outcome, double price, String reason) {
        try {
            String currency = plugin.getEconomyBridge().currencyNamePlural();
            plugin.getDataStore().recordShopTransaction(
                    player.getUniqueId(), player.getName(), itemKey, price,
                    currency, outcome, reason
            ).exceptionally(error -> {
                plugin.getLogger().warning("Could not write shop transaction audit for " + player.getName()
                        + ": " + safeMessage(error));
                return null;
            });
        } catch (RuntimeException auditException) {
            plugin.getLogger().warning("Could not queue shop transaction audit for " + player.getName()
                    + ": " + safeMessage(auditException));
        }
    }

    private String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null ? "No error detail" : throwable.getMessage();
    }

    private ItemStack makeItem(Entry entry) {
        ItemStack item = new ItemStack(entry.material(), entry.amount());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(entry.displayName()));
            if (!entry.lore().isEmpty()) meta.setLore(entry.lore().stream().map(this::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private double normalizePrice(double price) {
        if (!Double.isFinite(price) || price <= 0.0) return price;
        return BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        if (slot == 49 || (holder.stage() == ShopHolder.Stage.CONFIRM && slot == 22)) {
            player.closeInventory();
            return;
        }

        if (holder.stage() == ShopHolder.Stage.LIST) {
            String category = holder.categoryAt(slot);
            if (category != null) { open(player, 0, category); return; }
            if (slot == 47) { plugin.getMenuService().openMain(player); return; }
            if (slot == 45) { open(player, holder.page() - 1, holder.category()); return; }
            if (slot == 53) { open(player, holder.page() + 1, holder.category()); return; }
            String key = holder.entryAt(slot);
            Entry entry = key == null ? null : entries.get(key);
            if (entry != null) openConfirm(player, entry, holder.category(), holder.page());
            return;
        }

        if (slot == 15) { open(player, holder.page(), holder.category()); return; }
        if (slot == 11) {
            String key = holder.itemKey();
            player.closeInventory();
            if (key != null) purchase(player, key, holder.category(), holder.page());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopHolder) event.setCancelled(true);
    }

    private Inventory create(ShopHolder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);
        ItemStack filler = item(Material.BLUE_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == size / 9 - 1 || column == 0 || column == 8) inventory.setItem(slot, filler.clone());
        }
        return inventory;
    }

    private void set(Inventory inventory, int slot, Material material, String name, String... lore) {
        inventory.setItem(slot, item(material, name, lore));
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore.length > 0) meta.setLore(Arrays.stream(lore).map(this::color).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private String money(double amount) { return String.format(Locale.US, "%.2f", amount); }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
}
