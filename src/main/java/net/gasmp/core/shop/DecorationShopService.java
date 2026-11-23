package net.gasmp.core.shop;

import net.gasmp.core.GASMPCore;
import net.gasmp.core.data.PlayerDataStore;
import net.gasmp.core.gui.ScreenUi;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Shards-funded Furniture and Cosmetics catalogue backed by RP Engine custom items. */
public final class DecorationShopService implements Listener {
    private static final int[] PRODUCT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private record Product(String id, String category, String contentId, String name,
                           long price, String description) { }
    private enum Stage { CATEGORIES, CATALOGUE }
    private static final class Holder implements InventoryHolder {
        private final Stage stage;
        private final String category;
        private final int page;
        private final Map<Integer, String> products = new HashMap<>();
        private Inventory inventory;
        Holder(Stage stage, String category, int page) {
            this.stage = stage; this.category = category; this.page = page;
        }
        void setInventory(Inventory inventory) { this.inventory = inventory; }
        void productAt(int slot, String id) { products.put(slot, id); }
        String productAt(int slot) { return products.get(slot); }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final GASMPCore plugin;
    private final Map<String, Product> products = new LinkedHashMap<>();
    private final Set<UUID> purchasing = ConcurrentHashMap.newKeySet();
    private YamlConfiguration config;

    public DecorationShopService(GASMPCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "decoration-shop.yml");
        if (!file.exists()) plugin.saveResource("decoration-shop.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        products.clear();
        ConfigurationSection section = config.getConfigurationSection("products");
        if (section == null) {
            plugin.getLogger().warning("decoration-shop.yml has no products section.");
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) continue;
            String category = entry.getString("category", "").toLowerCase(Locale.ROOT);
            String contentId = entry.getString("content", "").trim();
            String name = entry.getString("name", id);
            long price = entry.getLong("price", -1L);
            String description = entry.getString("description", "GASMP collectible");
            if ((!category.equals("furniture") && !category.equals("cosmetics"))
                    || contentId.isBlank() || price < 0L) {
                plugin.getLogger().warning("Ignoring invalid decoration shop product: " + id);
                continue;
            }
            products.put(id, new Product(id, category, contentId, name, price, description));
        }
        plugin.getLogger().info("Loaded " + products.size() + " Shards decoration-shop products.");
    }

    public void openCategories(Player player) {
        Holder holder = new Holder(Stage.CATEGORIES, null, 0);
        Inventory inventory = create(holder, 54, ScreenUi.title("decorations", "&8GASMP • Decorations"));
        set(inventory, 20, Material.FLOWER_POT, "&b&lFURNITURE",
                "&7Furniture, props and plushies",
                "&7" + count("furniture") + " items",
                "&eClick to browse");
        set(inventory, 24, Material.ELYTRA, "&d&lCOSMETICS",
                "&7Wings, Feral Fantasy and collectible cosmetics",
                "&7" + count("cosmetics") + " items",
                "&eClick to browse");
        set(inventory, 31, Material.SHULKER_BOX, "&5&lCOSMETIC SHULKER",
                "&7Buy a key in the Cosmetics catalogue.",
                "&7Open it for a random cosmetic drop.");
        set(inventory, 49, Material.ARROW, "&f&lBack", "&7Return to the GASMP main menu");
        player.openInventory(inventory);
    }

    private int count(String category) {
        return (int) products.values().stream().filter(p -> p.category().equals(category)).count();
    }

    private void openCatalogue(Player player, String category, int requestedPage) {
        List<Product> visible = products.values().stream()
                .filter(p -> p.category().equals(category))
                .sorted(Comparator.comparing(Product::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int maxPage = Math.max(0, (visible.size() - 1) / PRODUCT_SLOTS.length);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        String label = category.equals("furniture") ? "Furniture" : "Cosmetics";
        Holder holder = new Holder(Stage.CATALOGUE, category, page);
        Inventory inventory = create(holder, 54,
                ScreenUi.title("decorations", "&8GASMP • " + label));
        int start = page * PRODUCT_SLOTS.length;
        int end = Math.min(visible.size(), start + PRODUCT_SLOTS.length);
        for (int index = start; index < end; index++) {
            Product product = visible.get(index);
            int slot = PRODUCT_SLOTS[index - start];
            ItemStack icon = createEngineItem(product.contentId());
            if (icon == null) icon = new ItemStack(category.equals("furniture") ? Material.ARMOR_STAND : Material.PAPER);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color("&f&l" + product.name()));
                List<String> lore = new ArrayList<>();
                lore.add(color("&7" + product.description()));
                lore.add(color("&8"));
                lore.add(color("&7Price: &d" + product.price() + " ✦ Shards"));
                lore.add(color("&eClick to purchase"));
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            inventory.setItem(slot, icon);
            holder.productAt(slot, product.id());
        }
        set(inventory, 45, Material.ARROW, "&f&lBack", "&7Return to categories");
        if (page > 0) set(inventory, 47, Material.SPECTRAL_ARROW, "&ePrevious page");
        set(inventory, 49, Material.BARRIER, "&cClose");
        if (page < maxPage) set(inventory, 51, Material.SPECTRAL_ARROW, "&eNext page");
        set(inventory, 53, Material.AMETHYST_SHARD, "&d" + (page + 1) + " / " + (maxPage + 1),
                "&7Your purchases use GASMP Shards.");
        player.openInventory(inventory);
    }

    private void purchase(Player player, Product product, String category, int page) {
        if (!purchasing.add(player.getUniqueId())) {
            player.sendMessage(color("&cYour previous decoration purchase is still processing."));
            return;
        }
        ItemStack reward = createEngineItem(product.contentId());
        if (reward == null) {
            purchasing.remove(player.getUniqueId());
            player.sendMessage(color("&cThis RP Engine item is not loaded: &f" + product.contentId()));
            player.sendMessage(color("&7Ask staff to run /rp reload and verify the cosmetic content pack."));
            return;
        }
        plugin.getDataStore().changeShards(player.getUniqueId(), player.getName(), player.getUniqueId(),
                "DECORATION_SHOP", product.price(), PlayerDataStore.ShardChange.TAKE,
                "Purchased " + product.id())
                .whenComplete((balance, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        if (error == null) plugin.getDataStore().changeShards(player.getUniqueId(),
                                player.getName(), null, "DECORATION_REFUND", product.price(),
                                PlayerDataStore.ShardChange.GIVE, "Refund: player went offline during purchase.");
                        purchasing.remove(player.getUniqueId());
                        return;
                    }
                    if (error != null) {
                        purchasing.remove(player.getUniqueId());
                        Throwable cause = root(error);
                        player.sendMessage(color("&cPurchase failed: &f" + safeMessage(cause)));
                        return;
                    }
                    Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
                    leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                    purchasing.remove(player.getUniqueId());
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
                    player.sendMessage(color("&aPurchased &f" + product.name() + " &afor &d"
                            + product.price() + " Shards&a. Balance: &d" + balance + " ✦"));
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder current
                            && current.stage == Stage.CATALOGUE && current.category.equals(category)) {
                        openCatalogue(player, category, page);
                    }
                }));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        if (holder.stage == Stage.CATEGORIES) {
            if (slot == 20) openCatalogue(player, "furniture", 0);
            else if (slot == 24) openCatalogue(player, "cosmetics", 0);
            else if (slot == 49) plugin.getMenuService().openMain(player);
            return;
        }

        if (slot == 45) { openCategories(player); return; }
        if (slot == 47 && holder.page > 0) { openCatalogue(player, holder.category, holder.page - 1); return; }
        int maxPage = Math.max(0, (count(holder.category) - 1) / PRODUCT_SLOTS.length);
        if (slot == 51 && holder.page < maxPage) { openCatalogue(player, holder.category, holder.page + 1); return; }
        if (slot == 49) { player.closeInventory(); return; }

        String productId = holder.productAt(slot);
        Product product = productId == null ? null : products.get(productId);
        if (product != null) purchase(player, product, holder.category, holder.page);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private Inventory create(Holder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inventory);
        ItemStack filler = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); filler.setItemMeta(meta); }
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9, column = slot % 9;
            if (row == 0 || row == 5 || column == 0 || column == 8) inventory.setItem(slot, filler.clone());
        }
        return inventory;
    }

    private void set(Inventory inventory, int slot, Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore.length > 0) meta.setLore(Arrays.stream(lore).map(this::color).toList());
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    private ItemStack createEngineItem(String contentIdText) {
        return plugin.getCrateService().createContentItem(contentIdText, 1);
    }
    private Throwable root(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current;
    }
    private String safeMessage(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
    private String color(String input) { return ChatColor.translateAlternateColorCodes('&', input); }
}
