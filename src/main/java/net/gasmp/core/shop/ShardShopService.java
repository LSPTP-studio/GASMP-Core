package net.gasmp.core.shop;

import net.gasmp.core.GASMPCore;
import net.gasmp.core.data.PlayerDataStore;
import net.gasmp.core.gui.MenuPage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Separate Shards storefront. Shards are debited transactionally by PlayerDataStore;
 * item delivery uses the existing GASMP item-mail queue if the buyer disconnects.
 */
public final class ShardShopService implements Listener, CommandExecutor, TabCompleter {
    private record Entry(String key, String category, Material material, int amount, long price,
                         String displayName, List<String> lore, String permission, String customId) { }
    private record Category(String key, String label, Material icon) { }
    private enum Stage { LIST, CONFIRM }

    private static final int[] CATEGORY_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] CONTENT_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final class Holder implements InventoryHolder {
        private final Stage stage;
        private final int page;
        private final String category;
        private final String itemKey;
        private final Map<Integer, String> entries = new HashMap<>();
        private final Map<Integer, String> categories = new HashMap<>();
        private Inventory inventory;

        private Holder(Stage stage, int page, String category, String itemKey) {
            this.stage = stage;
            this.page = page;
            this.category = category;
            this.itemKey = itemKey;
        }

        private void entryAt(int slot, String key) { entries.put(slot, key); }
        private String entryAt(int slot) { return entries.get(slot); }
        private void categoryAt(int slot, String key) { categories.put(slot, key); }
        private String categoryAt(int slot) { return categories.get(slot); }
        private void inventory(Inventory inventory) { this.inventory = inventory; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final GASMPCore plugin;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Set<UUID> purchasing = ConcurrentHashMap.newKeySet();
    private YamlConfiguration config;

    public ShardShopService(GASMPCore plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "shard-shop.yml");
        if (!file.exists()) plugin.saveResource("shard-shop.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        entries.clear();

        ConfigurationSection section = config.getConfigurationSection("items");
        if (section == null) {
            plugin.getLogger().warning("shard-shop.yml has no items section; the Shard Shop will be empty.");
            return;
        }

        for (String rawKey : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(rawKey);
            if (item == null) continue;
            String key = rawKey.toLowerCase(Locale.ROOT);
            Material material = Material.matchMaterial(item.getString("material", "PAPER"));
            int amount = item.getInt("amount", 1);
            long price = item.getLong("price", 0L);
            if (material == null || !material.isItem() || material.isAir()
                    || amount < 1 || amount > 64 || price < 1L) {
                plugin.getLogger().warning("Ignoring invalid Shard Shop entry: " + rawKey);
                continue;
            }
            String category = normalizeCategory(item.getString("category", "utility"));
            String displayName = item.getString("display-name", "&f" + material.name());
            String permission = item.getString("permission", "").trim();
            String customId = item.getString("custom-id", "").trim();
            entries.put(key, new Entry(key, category, material, amount, price, displayName,
                    List.copyOf(item.getStringList("lore")), permission, customId));
        }
        plugin.getLogger().info("Loaded " + entries.size() + " valid Shard Shop entries.");
    }

    public void open(Player player, int page) {
        open(player, page, "all");
    }

    public void open(Player player, int page, String selectedCategory) {
        if (!plugin.getConfig().getBoolean("modules.shards", true)
                || !config.getBoolean("settings.enabled", true)) {
            player.sendMessage(color("&cThe GASMP Shard Shop is currently disabled."));
            return;
        }

        String category = normalizeCategory(selectedCategory);
        List<Entry> visible = visibleEntries(player, category);
        List<Category> categories = categoriesFor(player);
        int maxPage = Math.max(0, (visible.size() - 1) / CONTENT_SLOTS.length);
        int safePage = Math.max(0, Math.min(page, maxPage));

        Holder holder = new Holder(Stage.LIST, safePage, category, null);
        Inventory inventory = create(holder, 54, "&5&lGASMP &8• &dShard Shop");
        set(inventory, 4, Material.AMETHYST_SHARD, "&d&lSHARD SHOP",
                "&7A separate storefront paid only with Shards.",
                "&7Items: &f" + visible.size(),
                "&7Category: &f" + categoryLabel(category));

        for (int i = 0; i < Math.min(categories.size(), CATEGORY_SLOTS.length); i++) {
            Category option = categories.get(i);
            int slot = CATEGORY_SLOTS[i];
            boolean selected = option.key().equals(category);
            set(inventory, slot, selected ? Material.LIME_STAINED_GLASS_PANE : option.icon(),
                    (selected ? "&a&l" : "&d&l") + option.label() + (selected ? " ✓" : ""),
                    "&7Show " + option.label().toLowerCase(Locale.ROOT) + " items.",
                    "&eClick to filter");
            holder.categoryAt(slot, option.key());
        }

        int start = safePage * CONTENT_SLOTS.length;
        int end = Math.min(start + CONTENT_SLOTS.length, visible.size());
        int contentIndex = 0;
        for (int i = start; i < end; i++) {
            Entry entry = visible.get(i);
            int slot = CONTENT_SLOTS[contentIndex++];
            ItemStack stack = makeItem(entry);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() && meta.getLore() != null
                        ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(color("&8"));
                lore.add(color("&7Category: &f" + categoryLabel(entry.category())));
                lore.add(color("&7Price: &d" + entry.price() + " ✦ Shards"));
                lore.add(color("&aClick to review purchase"));
                meta.setLore(lore);
                stack.setItemMeta(meta);
            }
            inventory.setItem(slot, stack);
            holder.entryAt(slot, entry.key());
        }

        if (safePage > 0) set(inventory, 45, Material.ARROW, "&fPrevious page");
        set(inventory, 47, Material.BOOK, "&7Back to Shards menu");
        set(inventory, 49, Material.BARRIER, "&cClose");
        if (safePage < maxPage) set(inventory, 53, Material.ARROW, "&fNext page");
        player.openInventory(inventory);
        refreshBalance(player, holder);
    }

    private void openConfirm(Player player, Entry entry, String category, int page) {
        Holder holder = new Holder(Stage.CONFIRM, page, category, entry.key());
        Inventory inventory = create(holder, 27, "&8Confirm Shard Purchase");
        ItemStack preview = makeItem(entry);
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(color("&8"));
            lore.add(color("&7Total cost: &d" + entry.price() + " ✦ Shards"));
            lore.add(color("&7This purchase cannot be refunded after delivery."));
            meta.setLore(lore);
            preview.setItemMeta(meta);
        }
        inventory.setItem(13, preview);
        set(inventory, 11, Material.LIME_CONCRETE, "&a&lCONFIRM",
                "&7Spend &d" + entry.price() + " Shards&7.",
                "&7The item will be delivered to your inventory or mailbox.");
        set(inventory, 15, Material.RED_CONCRETE, "&c&lCANCEL", "&7Return to the catalogue.");
        set(inventory, 22, Material.BARRIER, "&cClose");
        player.openInventory(inventory);
    }

    private void purchase(Player player, String itemKey) {
        if (!purchasing.add(player.getUniqueId())) {
            player.sendMessage(color("&cYour previous Shard purchase is still processing."));
            return;
        }

        Entry entry = entries.get(itemKey);
        if (entry == null) {
            purchasing.remove(player.getUniqueId());
            player.sendMessage(color("&cThat item is no longer in the catalogue."));
            return;
        }
        if (!entry.permission().isBlank() && !player.hasPermission(entry.permission())) {
            purchasing.remove(player.getUniqueId());
            player.sendMessage(color("&cYou do not have permission to purchase this item."));
            return;
        }

        ItemStack reward = makeItem(entry);
        if (!canFit(player, reward)) {
            purchasing.remove(player.getUniqueId());
            player.sendMessage(color("&cYour inventory is full. Make space before purchasing."));
            return;
        }

        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        String encodedItem;
        try {
            encodedItem = encodeItem(reward);
        } catch (Exception exception) {
            purchasing.remove(playerId);
            plugin.getLogger().severe("Could not serialize Shard Shop item " + itemKey + ": " + exception.getMessage());
            player.sendMessage(color("&cThe item could not be prepared. You have not been charged."));
            return;
        }

        plugin.getDataStore().changeShards(playerId, playerName, null,
                "SHARD_SHOP_PURCHASE", entry.price(), PlayerDataStore.ShardChange.TAKE,
                "Shard Shop purchase: " + itemKey).whenComplete((balance, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        purchasing.remove(playerId);
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        player.sendMessage(color("&cPurchase failed: &7" + safeMessage(cause)));
                        return;
                    }

                    if (player.isOnline()) {
                        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
                        leftovers.values().forEach(item ->
                                player.getWorld().dropItemNaturally(player.getLocation(), item));
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.25f);
                        player.sendMessage(color("&aPurchased &f" + entry.displayName()
                                + " &afor &d" + entry.price() + " ✦ Shards&a. New balance: &d" + balance + " ✦"));
                        purchasing.remove(playerId);
                        return;
                    }

                    // If the player disconnects during the async SQLite debit, queue the item
                    // in the same durable mailbox already used by the auction house.
                    plugin.getDataStore().addItemMail(playerId, encodedItem,
                            "GASMP Shard Shop purchase: " + itemKey).whenComplete((mailId, mailError) ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                purchasing.remove(playerId);
                                if (mailError == null) {
                                    plugin.getLogger().info("Queued Shard Shop item mail #" + mailId + " for " + playerName);
                                    return;
                                }
                                plugin.getLogger().severe("Could not queue Shard Shop item mail for " + playerName
                                        + ": " + safeMessage(mailError));
                                plugin.getDataStore().changeShards(playerId, playerName, null,
                                        "SHARD_SHOP_REFUND", entry.price(), PlayerDataStore.ShardChange.GIVE,
                                        "Refund for failed Shard Shop item-mail delivery");
                            }));
                }));
    }

    private boolean canFit(Player player, ItemStack reward) {
        Inventory simulation = Bukkit.createInventory(null, 36);
        ItemStack[] contents = player.getInventory().getStorageContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) copy[i] = contents[i] == null ? null : contents[i].clone();
        simulation.setStorageContents(copy);
        return simulation.addItem(reward.clone()).isEmpty();
    }

    private void refreshBalance(Player player, Holder holder) {
        plugin.getDataStore().getShards(player.getUniqueId()).whenComplete((balance, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != holder) return;
                    if (error != null) {
                        plugin.getLogger().warning("Could not load Shard Shop balance for " + player.getName()
                                + ": " + safeMessage(error));
                        return;
                    }
                    set(holder.getInventory(), 4, Material.AMETHYST_SHARD, "&d&lSHARD SHOP",
                            "&7Your balance: &d" + balance + " ✦ Shards",
                            "&7Items: &f" + visibleEntries(player, holder.category).size(),
                            "&7Category: &f" + categoryLabel(holder.category));
                }));
    }

    private List<Entry> visibleEntries(Player player, String category) {
        return entries.values().stream()
                .filter(entry -> category.equals("all") || entry.category().equals(category))
                .filter(entry -> entry.permission().isBlank() || player.hasPermission(entry.permission()))
                .filter(entry -> entry.customId().isBlank() || isRpEngineEnabled())
                .toList();
    }

    private List<Category> categoriesFor(Player player) {
        List<Category> result = new ArrayList<>();
        result.add(new Category("all", "All Items", Material.COMPASS));
        entries.values().stream()
                .filter(entry -> entry.permission().isBlank() || player.hasPermission(entry.permission()))
                .filter(entry -> entry.customId().isBlank() || isRpEngineEnabled())
                .map(Entry::category).distinct().sorted()
                .limit(CATEGORY_SLOTS.length - 1L)
                .forEach(key -> result.add(new Category(key, categoryLabel(key), categoryIcon(key))));
        return result;
    }

    private Material categoryIcon(String category) {
        return switch (category) {
            case "furniture" -> Material.ARMOR_STAND;
            case "cosmetics" -> Material.FIREWORK_ROCKET;
            case "utility" -> Material.ENDER_PEARL;
            case "supplies" -> Material.EXPERIENCE_BOTTLE;
            default -> Material.CHEST;
        };
    }

    private String normalizeCategory(String category) {
        return category == null || category.isBlank()
                ? "all" : category.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private String categoryLabel(String category) {
        if (category == null || category.equals("all")) return "All Items";
        StringBuilder label = new StringBuilder();
        for (String word : category.replace('_', ' ').replace('-', ' ').split("\\s+")) {
            if (word.isBlank()) continue;
            if (!label.isEmpty()) label.append(' ');
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }

    private ItemStack makeItem(Entry entry) {
        ItemStack item = null;
        if (!entry.customId().isBlank()) item = createEngineItem(entry.customId(), entry.amount());
        if (item == null) item = new ItemStack(entry.material(), entry.amount());

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(entry.displayName()));
            List<String> lore = new ArrayList<>(entry.lore().stream().map(this::color).toList());
            if (!entry.customId().isBlank() && !isRpEngineEnabled()) {
                lore.add(color("&cRequires RP Engine to place the 3D furniture model."));
            }
            meta.setLore(lore.isEmpty() ? null : lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isRpEngineEnabled() {
        org.bukkit.plugin.Plugin engine = Bukkit.getPluginManager().getPlugin("RPEngine");
        return engine != null && engine.isEnabled();
    }

    /** Mirrors the crate integration and keeps RP Engine optional at compile time. */
    private ItemStack createEngineItem(String contentIdText, int amount) {
        org.bukkit.plugin.Plugin engine = Bukkit.getPluginManager().getPlugin("RPEngine");
        if (engine == null || !engine.isEnabled()) return null;
        try {
            ClassLoader loader = engine.getClass().getClassLoader();
            Class<?> contentIdClass = Class.forName("ai.resourcepack.engine.api.ContentId", true, loader);
            Object parsed = contentIdClass.getMethod("parse", String.class).invoke(null, contentIdText);
            if (!(parsed instanceof java.util.Optional<?> optional) || optional.isEmpty()) return null;
            Object items = engine.getClass().getMethod("items").invoke(engine);
            Object created = null;
            for (java.lang.reflect.Method method : items.getClass().getMethods()) {
                if (!method.getName().equals("create")) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types.length == 2 && types[0].equals(contentIdClass) && types[1] == int.class) {
                    created = method.invoke(items, optional.get(), amount);
                    break;
                }
                if (types.length == 1 && types[0].equals(contentIdClass)) {
                    created = method.invoke(items, optional.get());
                    break;
                }
            }
            if (created instanceof java.util.Optional<?> stackOptional && stackOptional.isPresent()) {
                created = stackOptional.get();
            }
            if (created instanceof ItemStack stack) {
                ItemStack result = stack.clone();
                result.setAmount(Math.min(amount, result.getMaxStackSize()));
                return result;
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            plugin.getLogger().fine("RP Engine furniture item " + contentIdText
                    + " is unavailable: " + safeMessage(error));
        }
        return null;
    }

    private Inventory create(Holder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, color(title));
        holder.inventory(inventory);
        ItemStack filler = item(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == size / 9 - 1 || column == 0 || column == 8) {
                inventory.setItem(slot, filler.clone());
            }
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
            if (lore.length > 0) meta.setLore(Arrays.stream(lore).map(this::color).toList());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String encodeItem(ItemStack item) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item", item);
        return Base64.getEncoder().encodeToString(yaml.saveToString().getBytes(StandardCharsets.UTF_8));
    }

    private String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("modules.shards", true)
                && config.getBoolean("settings.enabled", true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("The Shard Shop is a player command.");
            return true;
        }
        if (!enabled()) {
            player.sendMessage(color("&cThe GASMP Shard Shop is currently disabled."));
            return true;
        }
        open(player, 0);
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;

        if (holder.stage == Stage.CONFIRM) {
            if (slot == 22 || slot == 35) { player.closeInventory(); return; }
            if (slot == 15) { open(player, holder.page, holder.category); return; }
            if (slot == 11) {
                String key = holder.itemKey;
                player.closeInventory();
                if (key != null) purchase(player, key);
            }
            return;
        }

        String category = holder.categoryAt(slot);
        if (category != null) { open(player, 0, category); return; }
        if (slot == 47) { plugin.getMenuService().open(player, MenuPage.SHARDS); return; }
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 45) { open(player, holder.page - 1, holder.category); return; }
        if (slot == 53) { open(player, holder.page + 1, holder.category); return; }

        String key = holder.entryAt(slot);
        Entry entry = key == null ? null : entries.get(key);
        if (entry != null) openConfirm(player, entry, holder.category, holder.page);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of() : List.of();
    }
}
