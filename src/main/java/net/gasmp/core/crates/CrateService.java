package net.gasmp.core.crates;

import net.gasmp.core.GASMPCore;
import net.gasmp.core.data.PlayerDataStore;
import net.gasmp.core.gui.ScreenUi;
import net.gasmp.core.staff.StaffAccess;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Sound;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class CrateService implements Listener, CommandExecutor, TabCompleter {
    private final GASMPCore plugin;
    private final StaffAccess staff;
    private final NamespacedKey crateKey;
    private YamlConfiguration config;
    private final Set<UUID> opening = new HashSet<>();
    private final Map<UUID, BukkitTask> animationTasks = new HashMap<>();

    public CrateService(GASMPCore plugin, StaffAccess staff) {
        this.plugin = plugin; this.staff = staff;
        this.crateKey = new NamespacedKey(plugin, "crate_key");
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "crates.yml");
        if (!file.exists()) {
            plugin.saveResource("crates.yml", false);
        } else {
            migrateEquipmentCrates(file);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Upgrade old Common/Rare/Epic/Legendary cosmetic reward tables to the
     * requested Normal/Iron, Medium/Diamond and Premium/Netherite tables.
     * A backup is retained, and legacy placed chest coordinates are preserved.
     */
    private void migrateEquipmentCrates(File file) {
        YamlConfiguration previous = YamlConfiguration.loadConfiguration(file);
        if (previous.getConfigurationSection("crates.normal") != null
                && previous.getConfigurationSection("crates.medium") != null
                && previous.getConfigurationSection("crates.premium") != null) {
            return;
        }
        try {
            File backup = new File(plugin.getDataFolder(), "crates-legacy-backup.yml");
            if (!backup.exists()) {
                java.nio.file.Files.copy(file.toPath(), backup.toPath());
            }
            plugin.saveResource("crates.yml", true);
            YamlConfiguration upgraded = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection locations = previous.getConfigurationSection("locations");
            if (locations != null) {
                for (String id : locations.getKeys(false)) {
                    ConfigurationSection location = locations.getConfigurationSection(id);
                    if (location == null) continue;
                    for (String key : location.getKeys(false)) {
                        upgraded.set("locations." + id + "." + key, location.get(key));
                    }
                }
            }
            upgraded.save(file);
            plugin.getLogger().info("Migrated legacy crate rewards to Normal/Iron, Medium/Diamond and Premium/Netherite. Backup: crates-legacy-backup.yml");
        } catch (Exception error) {
            plugin.getLogger().severe("Could not migrate old crate rewards; the original crates.yml was kept where possible: " + error.getMessage());
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // RP Engine executes the custom-block action as console. Player/command-block invocations are rejected.
        if (command.getName().equalsIgnoreCase("keyall")) {
            return keyAll(sender, args);
        }
        if (command.getName().equalsIgnoreCase("gcrateopen")) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage("This is an internal crate interaction command.");
                return true;
            }
            if (args.length != 2) return true;
            Player target = Bukkit.getPlayerExact(args[0]);
            String crateId = canonicalCrateId(args[1]);
            if (target != null && config.getConfigurationSection("crates." + crateId) != null) {
                openCrate(target, crateId);
            }
            return true;
        }

        if (!(sender instanceof Player player)) { sender.sendMessage("Run this command in-game."); return true; }
        // /crates stays Owner/Developer only, even if OP has wildcard permissions.
        if (!staff.isOwnerOrDev(player)) {
            player.sendMessage(color("&cOnly Developers and the Owner can use /crates."));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { help(player); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> { reload(); player.sendMessage(color("&aCrate configuration reloaded.")); }
            case "set" -> setLocation(player, args);
            case "unset" -> unsetLocation(player, args);
            case "givekey" -> giveKey(player, args);
            case "giveblock" -> giveCustomCrateItem(player, args, "_crate_block");
            case "givemodel" -> giveCustomCrateItem(player, args, "_crate_display");
            case "list" -> list(player);
            default -> help(player);
        }
        return true;
    }

    private void giveCustomCrateItem(Player actor, String[] args, String suffix) {
        if (args.length < 3 || args.length > 4) {
            actor.sendMessage(color("&e/crates " + (suffix.equals("_crate_block") ? "giveblock" : "givemodel")
                    + " <online-player> <crate-id> [amount]"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        String id = args[2].toLowerCase(Locale.ROOT);
        if (target == null || config.getConfigurationSection("crates." + id) == null) {
            actor.sendMessage(color("&cOnline player or crate ID not found."));
            return;
        }
        int amount = 1;
        if (args.length == 4) {
            try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException ex) { amount = 0; }
        }
        if (amount < 1 || amount > 64) {
            actor.sendMessage(color("&cAmount must be between 1 and 64."));
            return;
        }
        String contentId = chestPackTier(id)
                ? (suffix.equals("_crate_block") ? "gasmp_chests:" + id + "_crate_block" : "gasmp_chests:" + id + "_chest_display")
                : "gasmp_crates:" + id + suffix;
        ItemStack custom = createEngineItem(contentId, amount);
        if (custom == null) {
            actor.sendMessage(color("&cRP Engine is not installed or the custom content ID is not loaded. Use /rp reload first."));
            return;
        }
        Map<Integer, ItemStack> left = target.getInventory().addItem(custom);
        left.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        actor.sendMessage(color("&aGave " + amount + " custom " + id + " crate item(s) to " + target.getName() + "."));
    }

    private void setLocation(Player player, String[] args) {
        if (args.length != 2) { player.sendMessage(color("&e/crates set <crate-id>")); return; }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (config.getConfigurationSection("crates." + id) == null) { player.sendMessage(color("&cUnknown crate ID.")); return; }
        Block target = player.getTargetBlockExact(6);
        if (target == null || !(target.getType() == Material.CHEST || target.getType() == Material.TRAPPED_CHEST || target.getType() == Material.BARREL)) {
            player.sendMessage(color("&cLook at a chest, trapped chest or barrel within 6 blocks.")); return;
        }
        config.set("locations." + id + ".world", target.getWorld().getName());
        config.set("locations." + id + ".x", target.getX());
        config.set("locations." + id + ".y", target.getY());
        config.set("locations." + id + ".z", target.getZ());
        save(); player.sendMessage(color("&aSet &f" + id + " &acrate location."));
    }

    private void unsetLocation(Player player, String[] args) {
        if (args.length != 2) { player.sendMessage(color("&e/crates unset <crate-id>")); return; }
        config.set("locations." + args[1].toLowerCase(Locale.ROOT), null);
        save(); player.sendMessage(color("&aCrate location removed."));
    }

    private void giveKey(Player player, String[] args) {
        if (args.length < 3 || args.length > 4) { player.sendMessage(color("&e/crates givekey <online-player> <crate-id> [amount]")); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        String id = args[2].toLowerCase(Locale.ROOT);
        if (target == null) { player.sendMessage(color("&cTarget player must be online.")); return; }
        if (config.getConfigurationSection("crates." + id) == null) { player.sendMessage(color("&cUnknown crate ID.")); return; }
        int amount = 1;
        if (args.length == 4) try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException ex) { amount = 0; }
        if (amount < 1 || amount > 64) { player.sendMessage(color("&cAmount must be between 1 and 64.")); return; }
        Map<Integer, ItemStack> left = target.getInventory().addItem(createKey(id, amount));
        left.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        player.sendMessage(color("&aGave " + amount + " " + id + " key(s) to " + target.getName() + "."));
    }

    private void list(Player player) {
        ConfigurationSection crates = config.getConfigurationSection("crates");
        player.sendMessage(color("&6&lGASMP CRATES"));
        if (crates == null || crates.getKeys(false).isEmpty()) { player.sendMessage(color("&7No crates configured.")); return; }
        for (String id : crates.getKeys(false)) {
            boolean placed = config.getConfigurationSection("locations." + id) != null;
            player.sendMessage(color("&e" + id + " &8- " + (placed ? "&aPlaced" : "&cNot placed")));
        }
    }

    /** Both custom note-block crates and legacy configured chest locations are handled here. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCrateInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("modules.crates", true)) return;
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;
        String id = crateIdAt(event.getClickedBlock());
        if (id == null) return;

        if (action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            openCrate(event.getPlayer(), id);
            return;
        }

        // Normal left-click is deliberately allowed through to RP Engine so a
        // placed crate block can be mined and drops its matching crate item.
        // Sneak + left-click is the non-destructive reward-preview gesture.
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            openPreview(event.getPlayer(), id);
        }
    }

    /** Right-clicking a display crate opens it. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCrateModelInteract(PlayerInteractEntityEvent event) {
        if (!plugin.getConfig().getBoolean("modules.crates", true) || event.getHand() != EquipmentSlot.HAND) return;
        String id = crateIdFromModelEntity(event.getRightClicked());
        if (id == null) return;
        event.setCancelled(true);
        openCrate(event.getPlayer(), id);
    }

    /** Sneak-punch previews; an ordinary punch is left to RP Engine to pick up the display model. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCrateModelPunch(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !plugin.getConfig().getBoolean("modules.crates", true)) return;
        String id = crateIdFromModelEntity(event.getEntity());
        if (id == null || !player.isSneaking()) return;
        event.setCancelled(true);
        openPreview(player, id);
    }

    private String configuredCrateAt(Block block) {
        ConfigurationSection locations = config.getConfigurationSection("locations");
        if (locations == null) return null;
        for (String id : locations.getKeys(false)) {
            ConfigurationSection location = locations.getConfigurationSection(id);
            if (location != null
                    && location.getString("world", "").equals(block.getWorld().getName())
                    && location.getInt("x") == block.getX()
                    && location.getInt("y") == block.getY()
                    && location.getInt("z") == block.getZ()) return id;
        }
        return null;
    }

    private String crateIdAt(Block block) {
        String custom = rpCrateBlockAt(block);
        if (custom != null) return custom;
        String configured = configuredCrateAt(block);
        String canonical = configured == null ? null : canonicalCrateId(configured);
        return canonical != null && config.getConfigurationSection("crates." + canonical) != null ? canonical : null;
    }

    private boolean isConfiguredCrate(Block block) { return configuredCrateAt(block) != null; }
    private boolean isProtectedCrate(Block block) { return crateIdAt(block) != null; }
    private boolean isRpEngineCrateBlock(Block block) { return rpCrateBlockAt(block) != null; }

    /** Return the tier from gasmp_crates:<tier>_crate_block, or null if it is not a configured GASMP crate. */
    private String rpCrateBlockAt(Block block) {
        Plugin engine = Bukkit.getPluginManager().getPlugin("RPEngine");
        if (engine == null || !engine.isEnabled()) return null;
        try {
            Object blocks = engine.getClass().getMethod("blocks").invoke(engine);
            Object result = blocks.getClass().getMethod("idAt", Block.class).invoke(blocks, block);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) return null;
            String raw = String.valueOf(optional.get()).toLowerCase(Locale.ROOT);
            String prefix = raw.startsWith("gasmp_chests:") ? "gasmp_chests:" : "gasmp_crates:";
            String suffix = "_crate_block";
            if (!raw.startsWith(prefix) || !raw.endsWith(suffix)) return null;
            String id = canonicalCrateId(raw.substring(prefix.length(), raw.length() - suffix.length()));
            return config.getConfigurationSection("crates." + id) == null ? null : id;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().fine("RP Engine custom-block lookup unavailable: " + ex.getMessage());
            return null;
        }
    }

    /** Resolve an RP Engine placed model's id without compiling against its optional API. */
    private String crateIdFromModelEntity(Entity entity) {
        Plugin engine = Bukkit.getPluginManager().getPlugin("RPEngine");
        if (engine == null || !engine.isEnabled() || entity == null) return null;
        try {
            Object models = engine.getClass().getMethod("models").invoke(engine);
            Object result = models.getClass().getMethod("at", Entity.class).invoke(models, entity);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) return null;
            Object placement = optional.get();
            String raw = String.valueOf(placement.getClass().getMethod("modelId").invoke(placement)).toLowerCase(Locale.ROOT);
            int colon = raw.indexOf(':');
            String slug = colon >= 0 ? raw.substring(colon + 1) : raw;
            if (slug.endsWith("_chest_display")) slug = slug.substring(0, slug.length() - "_chest_display".length());
            else if (slug.endsWith("_chest")) slug = slug.substring(0, slug.length() - "_chest".length());
            else if (slug.endsWith("_crate_display")) slug = slug.substring(0, slug.length() - "_crate_display".length());
            else if (slug.endsWith("_crate")) slug = slug.substring(0, slug.length() - "_crate".length());
            slug = canonicalCrateId(slug);
            return config.getConfigurationSection("crates." + slug) == null ? null : slug;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().fine("RP Engine model lookup unavailable: " + ex.getMessage());
            return null;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCrateBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isProtectedCrate);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCrateEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isProtectedCrate);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCratePistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isProtectedCrate)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCratePistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isProtectedCrate)) event.setCancelled(true);
    }

    private void openCrate(Player player, String id) {
        if (!plugin.getConfig().getBoolean("modules.crates", true)) return;
        if (!opening.add(player.getUniqueId())) return;
        ConfigurationSection crate = config.getConfigurationSection("crates." + id);
        if (crate == null) { opening.remove(player.getUniqueId()); return; }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        boolean keyInMainHand = isKey(mainHand, id);
        ItemStack keyItem = keyInMainHand ? mainHand : player.getInventory().getItemInOffHand();
        if (!isKey(keyItem, id)) {
            opening.remove(player.getUniqueId());
            player.sendMessage(color("&cYou need a &f" + id + " &ccrate key."));
            return;
        }

        List<ConfigurationSection> rewards = new ArrayList<>();
        ConfigurationSection rewardSection = crate.getConfigurationSection("rewards");
        long totalWeight = 0L;
        if (rewardSection != null) for (String key : rewardSection.getKeys(false)) {
            ConfigurationSection reward = rewardSection.getConfigurationSection(key);
            if (reward == null) continue;
            int weight = reward.getInt("weight", 0);
            if (weight > 0 && reward.getString("type") != null && totalWeight + weight <= Integer.MAX_VALUE) {
                rewards.add(reward);
                totalWeight += weight;
            }
        }
        if (rewards.isEmpty() || totalWeight <= 0L) {
            opening.remove(player.getUniqueId());
            player.sendMessage(color("&cThis crate has no valid rewards configured. Your key was not consumed."));
            return;
        }

        int roll = ThreadLocalRandom.current().nextInt((int) totalWeight);
        ConfigurationSection chosen = null;
        for (ConfigurationSection reward : rewards) {
            roll -= reward.getInt("weight", 0);
            if (roll < 0) { chosen = reward; break; }
        }
        if (chosen == null) {
            opening.remove(player.getUniqueId());
            player.sendMessage(color("&cThe crate roll failed. Your key was not consumed."));
            return;
        }

        // Animate first. The key remains in the player's hand until the final frame, so
        // closing the inventory or disconnecting safely cancels the opening without loss.
        animateOpening(player, id, chosen);
    }

    /** Displays the reward table and weighted odds without consuming a key. */
    private void openPreview(Player player, String crateId) {
        ConfigurationSection crate = config.getConfigurationSection("crates." + crateId);
        ConfigurationSection section = crate == null ? null : crate.getConfigurationSection("rewards");
        if (section == null) {
            player.sendMessage(color("&cThis crate has no rewards configured."));
            return;
        }
        long totalWeight = 0L;
        for (String key : section.getKeys(false)) {
            ConfigurationSection reward = section.getConfigurationSection(key);
            if (reward != null) totalWeight += Math.max(0, reward.getInt("weight", 0));
        }
        if (totalWeight <= 0L) {
            player.sendMessage(color("&cThis crate has no valid reward odds configured."));
            return;
        }

        CrateAnimationHolder holder = new CrateAnimationHolder(crateId);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                ScreenUi.title("crate_details", "&8GASMP &7• &e" + crateId.toUpperCase(Locale.ROOT) + " LOOT"));
        holder.inventory(inventory);
        ItemStack filler = named(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < 54; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == 5 || column == 0 || column == 8) inventory.setItem(slot, filler.clone());
        }

        int[] slots = {20, 21, 22, 23, 24, 25, 26};
        int index = 0;
        for (String key : section.getKeys(false)) {
            ConfigurationSection reward = section.getConfigurationSection(key);
            if (reward == null) continue;
            int weight = Math.max(0, reward.getInt("weight", 0));
            if (weight <= 0 || index >= slots.length) continue;
            ItemStack icon = rewardIcon(reward);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                String display = reward.getString("display", key.replace('_', ' ').toUpperCase(Locale.ROOT));
                meta.setDisplayName(color("&e&l" + display));
                List<String> lore = new ArrayList<>();
                String type = reward.getString("type", "item").toLowerCase(Locale.ROOT);
                lore.add(color(type.equals("key") ? "&dCrate key jackpot" : "&7Equipment reward"));
                lore.add(color("&7Chance: &a" + String.format(Locale.ROOT, "%.2f", weight * 100.0d / totalWeight) + "%"));
                if (type.equals("item")) {
                    ConfigurationSection enchants = reward.getConfigurationSection("enchantments");
                    if (enchants != null) lore.add(color("&bEnchantments: &f" + String.join(", ", enchants.getKeys(false))));
                }
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            inventory.setItem(slots[index++], icon);
        }
        inventory.setItem(31, named(Material.BOOK, "&f&lHOW TO OPEN",
                "&7Right-click while holding the matching key.",
                "&7Sneak + left-click to inspect the equipment and odds.",
                "&8Keys are consumed only after the opening animation."));
        player.openInventory(inventory);
    }

    private void animateOpening(Player player, String crateId, ConfigurationSection reward) {
        CrateAnimationHolder holder = new CrateAnimationHolder(crateId);
        Inventory inventory = Bukkit.createInventory(holder, 54, ScreenUi.title("crates", "&8GASMP &7• &d" + crateId.toUpperCase(Locale.ROOT) + " CRATE"));
        holder.inventory(inventory);
        ItemStack filler = named(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            int row = i / 9;
            int column = i % 9;
            if (row == 0 || row == 5 || column == 0 || column == 8) inventory.setItem(i, filler.clone());
        }
        inventory.setItem(22, named(Material.CHEST, "&d&lOPENING...", "&7Your reward is being rolled."));
        player.openInventory(inventory);

        Material[] reel = {Material.AMETHYST_SHARD, Material.DIAMOND, Material.EMERALD,
                Material.GOLD_INGOT, Material.NETHERITE_INGOT, Material.EXPERIENCE_BOTTLE,
                Material.PRISMARINE_CRYSTALS, Material.BLAZE_POWDER};
        final int[] ticks = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != holder) {
                BukkitTask current = animationTasks.remove(player.getUniqueId());
                if (current != null) current.cancel();
                opening.remove(player.getUniqueId());
                return;
            }
            ticks[0]++;
            for (int slot : new int[]{21, 22, 23}) {
                Material material = reel[ThreadLocalRandom.current().nextInt(reel.length)];
                inventory.setItem(slot, named(material, "&e&l" + material.name().replace('_', ' ')));
            }
            if (ticks[0] % 5 == 0) player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.55f,
                    0.7f + (ticks[0] / 60.0f));
            if (ticks[0] >= 50) {
                BukkitTask current = animationTasks.remove(player.getUniqueId());
                if (current != null) current.cancel();
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
                inventory.setItem(22, rewardPreview(reward));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != holder) {
                        opening.remove(player.getUniqueId());
                        return;
                    }
                    boolean mainHand = isKey(player.getInventory().getItemInMainHand(), crateId);
                    ItemStack keyItem = mainHand ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
                    if (!isKey(keyItem, crateId)) {
                        opening.remove(player.getUniqueId());
                        player.sendMessage(color("&cYour crate key is no longer in your hand. No reward was issued."));
                        player.closeInventory();
                        return;
                    }
                    consumeKey(player, mainHand, keyItem);
                    player.closeInventory();
                    deliver(player, reward, crateId);
                }, 12L);
            }
        }, 0L, 1L);
        animationTasks.put(player.getUniqueId(), task);
    }

    private ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        if (lore.length > 0) {
            List<String> lines = new ArrayList<>();
            for (String line : lore) lines.add(color(line));
            meta.setLore(lines);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack rewardIcon(ConfigurationSection reward) {
        String type = reward.getString("type", "item").toLowerCase(Locale.ROOT);
        if (type.equals("key")) {
            String tier = reward.getString("crate", "normal").toLowerCase(Locale.ROOT);
            return createKey(tier, 1);
        }
        if (type.equals("item")) {
            ItemStack item = equipmentReward(reward, 1);
            return item == null ? new ItemStack(Material.BARRIER) : item;
        }
        Material material = switch (type) {
            case "shards" -> Material.AMETHYST_SHARD;
            case "coins" -> Material.GOLD_INGOT;
            default -> Material.CHEST;
        };
        return new ItemStack(material);
    }

    private ItemStack rewardPreview(ConfigurationSection reward) {
        ItemStack preview = rewardIcon(reward);
        String type = reward.getString("type", "item").toLowerCase(Locale.ROOT);
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            String originalName = meta.hasDisplayName() ? meta.getDisplayName()
                    : reward.getString("display", type.equals("key") ? "Crate Key" : "Equipment");
            meta.setDisplayName(color("&6&lREWARD REVEALED"));
            List<String> lore = new ArrayList<>();
            lore.add(color("&7You won: &f" + ChatColor.stripColor(originalName)));
            lore.add(color(type.equals("key") ? "&dRare crate-key drop" : "&7Equipment loot"));
            if (type.equals("item")) {
                ConfigurationSection enchants = reward.getConfigurationSection("enchantments");
                if (enchants != null) lore.add(color("&bEnchantments: &f" + String.join(", ", enchants.getKeys(false))));
            }
            lore.add(color("&8GASMP CRATES"));
            meta.setLore(lore);
            preview.setItemMeta(meta);
        }
        return preview;
    }

    private ItemStack equipmentReward(ConfigurationSection reward, int amount) {
        Material material = Material.matchMaterial(reward.getString("material", "IRON_INGOT"));
        if (material == null || !material.isItem() || material.isAir() || amount < 1 || amount > 64) return null;
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String display = reward.getString("display", "");
        if (!display.isBlank()) meta.setDisplayName(color(display));
        List<String> lore = reward.getStringList("lore");
        if (!lore.isEmpty()) meta.setLore(lore.stream().map(this::color).toList());
        ConfigurationSection enchantments = reward.getConfigurationSection("enchantments");
        if (enchantments != null) {
            for (String name : enchantments.getKeys(false)) {
                org.bukkit.enchantments.Enchantment enchantment =
                        org.bukkit.enchantments.Enchantment.getByName(name.toUpperCase(Locale.ROOT));
                int level = enchantments.getInt(name, 1);
                if (enchantment == null || level < 1 || level > 255) {
                    plugin.getLogger().warning("Ignoring invalid enchantment " + name + " in crate reward.");
                    continue;
                }
                meta.addEnchant(enchantment, level, true);
            }
        }
        if (reward.getBoolean("unbreakable", false)) meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onCrateAnimationClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CrateAnimationHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onCrateAnimationDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CrateAnimationHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onCrateAnimationClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CrateAnimationHolder)) return;
        UUID playerId = event.getPlayer().getUniqueId();
        BukkitTask task = animationTasks.remove(playerId);
        if (task != null) {
            task.cancel();
            opening.remove(playerId);
        }
    }

    @EventHandler
    public void onCrateAnimationQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        BukkitTask task = animationTasks.remove(playerId);
        if (task != null) task.cancel();
        opening.remove(playerId);
    }

    private void consumeKey(Player player, boolean mainHand, ItemStack key) {
        ItemStack replacement = key.clone();
        int remaining = replacement.getAmount() - 1;
        if (remaining <= 0) replacement = new ItemStack(Material.AIR);
        else replacement.setAmount(remaining);
        if (mainHand) player.getInventory().setItemInMainHand(replacement);
        else player.getInventory().setItemInOffHand(replacement);
    }

    private void deliver(Player player, ConfigurationSection reward, String crateId) {
        String type = reward.getString("type", "item").toLowerCase(Locale.ROOT);
        long amount = reward.getLong("amount", 1);
        if (amount < 1L || amount > 1_000_000_000L) {
            refundKey(player, crateId, "Configured reward amount must be between 1 and 1,000,000,000.");
            plugin.getLogger().warning("Invalid amount " + amount + " in crate " + crateId);
            return;
        }
        switch (type) {
            case "item" -> {
                if (amount < 1 || amount > 64) {
                    refundKey(player, crateId, "Configured equipment amount must be between 1 and 64.");
                    return;
                }
                ItemStack rewardItem = equipmentReward(reward, (int) amount);
                if (rewardItem == null) {
                    refundKey(player, crateId, "Crate equipment definition is invalid; contact staff.");
                    return;
                }
                Map<Integer, ItemStack> left = player.getInventory().addItem(rewardItem);
                left.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                player.sendMessage(color("&6&lEQUIPMENT UNLOCKED &8» &f" + reward.getString("display", reward.getString("material", "Equipment"))));
                opening.remove(player.getUniqueId());
            }
            case "key" -> {
                String targetCrate = reward.getString("crate", "").toLowerCase(Locale.ROOT);
                if (amount > 64 || !chestPackTier(targetCrate)
                        || config.getConfigurationSection("crates." + targetCrate) == null) {
                    refundKey(player, crateId, "Crate-key reward definition is invalid.");
                    return;
                }
                giveKeyTo(player, targetCrate, (int) amount, false);
                player.sendMessage(color("&d&lKEY JACKPOT &8» &f" + targetCrate.toUpperCase(Locale.ROOT) + " KEY"));
                opening.remove(player.getUniqueId());
            }
            case "cosmetic" -> {
                String contentId = reward.getString("content", "");
                if (contentId.isBlank()) {
                    refundKey(player, crateId, "Cosmetic reward has no content ID configured.");
                    plugin.getLogger().warning("Missing cosmetic content ID in crate " + crateId);
                    return;
                }
                if (amount > 64) {
                    refundKey(player, crateId, "Configured cosmetic amount must be between 1 and 64.");
                    return;
                }
                ItemStack cosmetic = createEngineItem(contentId, (int) amount);
                if (cosmetic == null) {
                    refundKey(player, crateId, "Cosmetic '" + contentId + "' is not loaded by RP Engine.");
                    plugin.getLogger().warning("Could not create cosmetic reward " + contentId + " from crate " + crateId);
                    return;
                }
                Map<Integer, ItemStack> left = player.getInventory().addItem(cosmetic);
                left.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                ItemMeta meta = cosmetic.getItemMeta();
                String rewardName = meta != null && meta.hasDisplayName()
                        ? ChatColor.stripColor(meta.getDisplayName()) : contentId;
                player.sendMessage(color("&d&lCOSMETIC UNLOCKED &8» &f" + rewardName));
                opening.remove(player.getUniqueId());
            }
            case "coins" -> {
                if (!plugin.getEconomyBridge().isAvailable()) { refundKey(player, crateId, "Coins economy unavailable."); return; }
                try {
                    double before = plugin.getEconomyBridge().getBalance(player);
                    EconomyResponse response = plugin.getEconomyBridge().deposit(player, amount);
                    boolean paid = response != null && response.transactionSuccess();
                    if (!paid) {
                        double after = plugin.getEconomyBridge().getBalance(player);
                        paid = Double.isFinite(after) && Math.abs((after - before) - amount) <= 0.005d;
                    }
                    if (!paid) { refundKey(player, crateId, "Economy provider rejected reward."); return; }
                    player.sendMessage(color("&6&lCRATE REWARD &8» &6+" + amount + " Coins"));
                    opening.remove(player.getUniqueId());
                } catch (RuntimeException providerError) {
                    plugin.getLogger().severe("Crate Coins reward provider error for " + player.getUniqueId()
                            + " in crate " + crateId + ": " + providerError.getMessage());
                    // Do not refund a key when provider state is unknowable; that could duplicate a paid reward.
                    player.sendMessage(color("&cReward payment needs staff reconciliation. Keep this message and contact staff."));
                    opening.remove(player.getUniqueId());
                }
            }
            case "shards" -> plugin.getDataStore().changeShards(player.getUniqueId(), player.getName(), null,
                    "CRATE", amount, PlayerDataStore.ShardChange.GIVE, crateId + " crate reward")
                    .whenComplete((balance, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (error != null) refundKey(player, crateId, "Shards database error.");
                        else { player.sendMessage(color("&d&lCRATE REWARD &8» &d+" + amount + " Shards")); opening.remove(player.getUniqueId()); }
                    }));
            default -> refundKey(player, crateId, "Unknown crate reward type.");
        }
    }

    private void refundKey(Player player, String id, String reason) {
        Map<Integer, ItemStack> left = player.getInventory().addItem(createKey(id, 1));
        left.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(color("&cCrate opening cancelled: " + reason + " Your key was returned."));
        opening.remove(player.getUniqueId());
    }

    public boolean giveKeyTo(Player target, String id, int amount, boolean message) {
        if (target == null || config.getConfigurationSection("crates." + id) == null || amount < 1 || amount > 64) return false;
        ItemStack key = createKey(id, amount);
        Map<Integer, ItemStack> left = target.getInventory().addItem(key);
        left.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        if (message) target.sendMessage(color("&d&lGASMP &8» &fYou received &d" + amount + " " + id.toUpperCase(Locale.ROOT) + " crate key(s)&f."));
        return true;
    }

    /** Player-facing Shards shop purchase; no other normal-player key source is provided by GASMP-Core. */
    public void buyKey(Player player, String id) {
        if (!plugin.getConfig().getBoolean("modules.crates", true) || !plugin.getConfig().getBoolean("key-shop.enabled", true)) {
            player.sendMessage(color("&cThe crate-key shop is currently unavailable."));
            return;
        }
        long price = plugin.getConfig().getLong("key-shop.prices." + id, -1L);
        if (price < 0L || config.getConfigurationSection("crates." + id) == null) {
            player.sendMessage(color("&cThat crate key is not for sale."));
            return;
        }
        if (price == 0L) {
            giveKeyTo(player, id, 1, true);
            return;
        }
        plugin.getDataStore().changeShards(player.getUniqueId(), player.getName(), player.getUniqueId(),
                "KEY_SHOP", price, PlayerDataStore.ShardChange.TAKE, "Purchased " + id + " crate key")
                .whenComplete((balance, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (error != null) {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        player.sendMessage(color("&cKey purchase failed: " + cause.getMessage()));
                        return;
                    }
                    if (!giveKeyTo(player, id, 1, false)) {
                        plugin.getDataStore().changeShards(player.getUniqueId(), player.getName(), player.getUniqueId(),
                                "KEY_SHOP_REFUND", price, PlayerDataStore.ShardChange.GIVE,
                                "Refund: could not create " + id + " crate key");
                        player.sendMessage(color("&cThe key could not be created. Your Shards are being refunded."));
                        return;
                    }
                    player.sendMessage(color("&aPurchased a &f" + id.toUpperCase(Locale.ROOT) + " Key &afor &d" + price + " Shards&a. Balance: &d" + balance + " ✦"));
                }));
    }

    private boolean keyAll(CommandSender sender, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) && (!(sender instanceof Player player) || !staff.isOwnerOrDev(player))) {
            sender.sendMessage(color("&cOnly the Owner and Developers can use /keyall."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(color("&e/keyall <crate-id|all> <amount> [world:<world>] [silent]"));
            return true;
        }
        String requested = args[0].toLowerCase(Locale.ROOT);
        List<String> ids = new ArrayList<>();
        if (requested.equals("all")) {
            ConfigurationSection crates = config.getConfigurationSection("crates");
            if (crates != null) ids.addAll(crates.getKeys(false));
        } else if (config.getConfigurationSection("crates." + requested) != null) ids.add(requested);
        if (ids.isEmpty()) {
            sender.sendMessage(color("&cUnknown crate id. Use /crates list."));
            return true;
        }
        int amount;
        try { amount = Integer.parseInt(args[1]); }
        catch (NumberFormatException ex) { amount = 0; }
        if (amount < 1 || amount > 64) {
            sender.sendMessage(color("&cAmount must be between 1 and 64."));
            return true;
        }
        String worldFilter = null;
        boolean silent = false;
        for (int i = 2; i < args.length; i++) {
            String option = args[i];
            if (option.equalsIgnoreCase("silent")) silent = true;
            else if (option.equalsIgnoreCase("online")) { /* online is the default */ }
            else if (option.toLowerCase(Locale.ROOT).startsWith("world:")) {
                worldFilter = option.substring("world:".length());
                if (worldFilter.isBlank() || !worldFilter.matches("[A-Za-z0-9_+.-]+")) {
                    sender.sendMessage(color("&cInvalid world filter. Use world:<world-name>."));
                    return true;
                }
            } else {
                sender.sendMessage(color("&cUnknown option: " + option + " &7Options: world:<world>, silent"));
                return true;
            }
        }
        int recipients = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (worldFilter != null && !target.getWorld().getName().equalsIgnoreCase(worldFilter)) continue;
            for (String id : ids) giveKeyTo(target, id, amount, false);
            recipients++;
        }
        if (!silent) Bukkit.broadcastMessage(color("&d&lGASMP &8» &f" + amount + " key(s) for " + String.join(", ", ids) + " were distributed to &d" + recipients + " &fplayer(s)."));
        sender.sendMessage(color("&aDistributed " + amount + " key(s) for " + String.join(", ", ids) + " to " + recipients + " online player(s)."));
        return true;
    }

    private boolean chestPackTier(String id) {
        return "normal".equalsIgnoreCase(id) || "medium".equalsIgnoreCase(id) || "premium".equalsIgnoreCase(id);
    }

    private ItemStack createKey(String id, int amount) {
        String contentId = chestPackTier(id) ? "gasmp_chests:" + id + "_key" : "gasmp_crates:" + id + "_key";
        ItemStack item = createEngineItem(contentId, amount);
        if (item == null) item = new ItemStack(Material.TRIPWIRE_HOOK, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&d&l" + id.toUpperCase(Locale.ROOT) + " CRATE KEY"));
            meta.setLore(List.of(color("&7Use at the matching GASMP crate."), color("&8GASMP key")));
            meta.getPersistentDataContainer().set(crateKey, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** RP Engine create() is main-thread-only. Falls back to vanilla items if it is absent. */
    private ItemStack createEngineItem(String contentIdText, int amount) {
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
            if (created instanceof Optional<?> stackOptional && stackOptional.isPresent()) {
                Object value = stackOptional.get();
                if (value instanceof ItemStack stack) {
                    ItemStack result = stack.clone();
                    if (result.getAmount() != amount) result.setAmount(Math.min(amount, result.getMaxStackSize()));
                    return result;
                }
            }
            if (created instanceof ItemStack stack) {
                ItemStack result = stack.clone();
                if (result.getAmount() != amount) result.setAmount(Math.min(amount, result.getMaxStackSize()));
                return result;
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().fine("RP Engine item " + contentIdText + " not available: " + ex.getMessage());
        }
        return null;
    }

    private String canonicalCrateId(String rawId) {
        if (rawId == null) return "";
        return switch (rawId.toLowerCase(Locale.ROOT)) {
            case "common" -> "normal";
            case "rare" -> "medium";
            case "epic", "legendary" -> "premium";
            default -> rawId.toLowerCase(Locale.ROOT);
        };
    }

    private boolean isKey(ItemStack item, String id) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        String canonical = canonicalCrateId(id);
        String tagged = item.getItemMeta().getPersistentDataContainer().get(crateKey, PersistentDataType.STRING);
        if (canonical.equals(canonicalCrateId(tagged))) return true;
        // Also accept a genuine RP Engine key handed out through /rp give or another RP Engine shop.
        Plugin engine = Bukkit.getPluginManager().getPlugin("RPEngine");
        if (engine == null || !engine.isEnabled()) return false;
        try {
            Object items = engine.getClass().getMethod("items").invoke(engine);
            Object result = items.getClass().getMethod("idOf", ItemStack.class).invoke(items, item);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) return false;
            String actual = String.valueOf(optional.get()).toLowerCase(Locale.ROOT);
            String expected = chestPackTier(canonical) ? "gasmp_chests:" + canonical + "_key" : "gasmp_crates:" + canonical + "_key";
            if (expected.equalsIgnoreCase(actual)) return true;
            // Existing Common/Rare/Epic/Legendary keys remain usable after the tier migration.
            String legacyPrefix = "gasmp_crates:";
            if (actual.startsWith(legacyPrefix) && actual.endsWith("_key")) {
                String legacyTier = actual.substring(legacyPrefix.length(), actual.length() - "_key".length());
                return canonical.equals(canonicalCrateId(legacyTier));
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().fine("RP Engine key identity lookup unavailable: " + ex.getMessage());
            return false;
        }
    }

    private void save() { try { config.save(new File(plugin.getDataFolder(), "crates.yml")); } catch (Exception ex) { plugin.getLogger().severe("Could not save crates.yml: " + ex.getMessage()); } }
    private void help(Player player) {
        player.sendMessage(color("&6&lGASMP CRATES &8- &cDeveloper/Owner only"));
        player.sendMessage(color("&e/crates list &7- list configured crates"));
        player.sendMessage(color("&e/crates set <id> &7- set target chest/barrel"));
        player.sendMessage(color("&e/crates unset <id> &7- remove location"));
        player.sendMessage(color("&eSneak + left-click &7- preview crate loot"));
        player.sendMessage(color("&eMine/punch &7- pick up an already-placed crate"));
        player.sendMessage(color("&e/crates givekey <player> <id> [amount]"));
        player.sendMessage(color("&e/crates giveblock <player> <normal|medium|premium> [amount] &7- placeable chest crate"));
        player.sendMessage(color("&e/crates givemodel <player> <normal|medium|premium> [amount] &7- chest model display"));
        player.sendMessage(color("&e/crates reload &7- reload crate definitions"));
        player.sendMessage(color("&e/keyall <crate-id|all> <amount> [world:<world>] [silent] &7- distribute keys"));
    }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("keyall")) {
            if (args.length == 1) {
                List<String> values = new ArrayList<>();
                values.add("all");
                ConfigurationSection crates = config.getConfigurationSection("crates");
                if (crates != null) values.addAll(crates.getKeys(false));
                return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
            }
            if (args.length == 3) return List.of("online", "silent", "world:");
            if (args.length == 4) return List.of("silent");
            return List.of();
        }
        if (args.length == 1) return List.of("list", "set", "unset", "givekey", "giveblock", "givemodel", "reload");
        return List.of();
    }
}
