package net.gasmp.core.crates;

import net.gasmp.core.GASMPCore;
import net.gasmp.core.data.PlayerDataStore;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
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
        if (!file.exists()) plugin.saveResource("crates.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // RP Engine executes the custom-block action as console. Player/command-block invocations are rejected.
        if (command.getName().equalsIgnoreCase("gcrateopen")) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage("This is an internal crate interaction command.");
                return true;
            }
            if (args.length != 2) return true;
            Player target = Bukkit.getPlayerExact(args[0]);
            String crateId = args[1].toLowerCase(Locale.ROOT);
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
        ItemStack custom = createEngineItem("gasmp_crates:" + id + suffix, amount);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrateInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("modules.crates", true)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        ConfigurationSection locations = config.getConfigurationSection("locations");
        if (locations == null) return;
        for (String id : locations.getKeys(false)) {
            ConfigurationSection location = locations.getConfigurationSection(id);
            if (location == null) continue;
            if (location.getString("world", "").equals(block.getWorld().getName())
                    && location.getInt("x") == block.getX() && location.getInt("y") == block.getY()
                    && location.getInt("z") == block.getZ()) {
                event.setCancelled(true);
                openCrate(player, id);
                return;
            }
        }
    }

    private boolean isConfiguredCrate(Block block) {
        ConfigurationSection locations = config.getConfigurationSection("locations");
        if (locations == null) return false;
        for (String id : locations.getKeys(false)) {
            ConfigurationSection location = locations.getConfigurationSection(id);
            if (location == null) continue;
            if (location.getString("world", "").equals(block.getWorld().getName())
                    && location.getInt("x") == block.getX()
                    && location.getInt("y") == block.getY()
                    && location.getInt("z") == block.getZ()) return true;
        }
        return false;
    }

    private boolean isProtectedCrate(Block block) {
        return isConfiguredCrate(block) || isRpEngineCrateBlock(block);
    }

    /** Uses only RP Engine's documented public API, accessed reflectively so it remains optional. */
    private boolean isRpEngineCrateBlock(Block block) {
        Plugin engine = Bukkit.getPluginManager().getPlugin("RPEngine");
        if (engine == null || !engine.isEnabled()) return false;
        try {
            Object blocks = engine.getClass().getMethod("blocks").invoke(engine);
            Object result = blocks.getClass().getMethod("idAt", Block.class).invoke(blocks, block);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) return false;
            String id = String.valueOf(optional.get()).toLowerCase(Locale.ROOT);
            return id.startsWith("gasmp_crates:") && id.endsWith("_crate_block");
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().fine("RP Engine custom-block lookup unavailable: " + ex.getMessage());
            return false;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrateBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        boolean configuredLocation = isConfiguredCrate(block);
        boolean customBlock = isRpEngineCrateBlock(block);
        if (!configuredLocation && !customBlock) return;

        // Only Owner/Developer can remove a protected crate. RP Engine then handles the custom block drop.
        if (staff.isOwnerOrDev(event.getPlayer())) {
            if (configuredLocation) {
                ConfigurationSection locations = config.getConfigurationSection("locations");
                if (locations != null) {
                    for (String id : new ArrayList<>(locations.getKeys(false))) {
                        ConfigurationSection location = locations.getConfigurationSection(id);
                        if (location != null
                                && location.getString("world", "").equals(block.getWorld().getName())
                                && location.getInt("x") == block.getX() && location.getInt("y") == block.getY()
                                && location.getInt("z") == block.getZ()) {
                            config.set("locations." + id, null);
                        }
                    }
                    save();
                }
            }
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(color("&cThis is a protected GASMP crate. Only Developers and the Owner can remove it."));
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

    private void animateOpening(Player player, String crateId, ConfigurationSection reward) {
        CrateAnimationHolder holder = new CrateAnimationHolder(crateId);
        Inventory inventory = Bukkit.createInventory(holder, 27, color("&8GASMP &7• &d" + crateId.toUpperCase(Locale.ROOT) + " CRATE"));
        holder.inventory(inventory);
        ItemStack filler = named(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) inventory.setItem(i, filler.clone());
        }
        inventory.setItem(13, named(Material.CHEST, "&d&lOPENING...", "&7Your reward is being rolled."));
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
            for (int slot : new int[]{11, 13, 15}) {
                Material material = reel[ThreadLocalRandom.current().nextInt(reel.length)];
                inventory.setItem(slot, named(material, "&e&l" + material.name().replace('_', ' ')));
            }
            if (ticks[0] % 5 == 0) player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.55f,
                    0.7f + (ticks[0] / 60.0f));
            if (ticks[0] >= 50) {
                BukkitTask current = animationTasks.remove(player.getUniqueId());
                if (current != null) current.cancel();
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
                inventory.setItem(13, rewardPreview(reward));
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

    private ItemStack rewardPreview(ConfigurationSection reward) {
        String type = reward.getString("type", "item").toLowerCase(Locale.ROOT);
        Material material = switch (type) {
            case "coins" -> Material.GOLD_INGOT;
            case "shards" -> Material.AMETHYST_SHARD;
            default -> Material.matchMaterial(reward.getString("material", "DIAMOND"));
        };
        if (material == null || !material.isItem() || material.isAir()) material = Material.CHEST;
        long amount = reward.getLong("amount", 1);
        return named(material, "&6&lREWARD REVEALED", "&7Type: &f" + type.toUpperCase(Locale.ROOT),
                "&7Amount: &e" + amount, "&8GASMP CRATES");
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
                Material material = Material.matchMaterial(reward.getString("material", "DIAMOND"));
                if (material == null || !material.isItem() || material.isAir()) {
                    refundKey(player, crateId, "Crate reward item is invalid; contact staff."); return;
                }
                if (amount < 1 || amount > 64) {
                    refundKey(player, crateId, "Configured item reward amount must be between 1 and 64.");
                    plugin.getLogger().warning("Invalid item reward amount " + amount + " in crate " + crateId);
                    return;
                }
                ItemStack rewardItem = new ItemStack(material, (int) amount);
                Map<Integer, ItemStack> left = player.getInventory().addItem(rewardItem);
                left.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                player.sendMessage(color("&6&lCRATE REWARD &8» &f" + amount + "x " + material.name()));
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

    private ItemStack createKey(String id, int amount) {
        ItemStack item = createEngineItem("gasmp_crates:" + id + "_key", amount);
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

    private boolean isKey(ItemStack item, String id) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return id.equals(item.getItemMeta().getPersistentDataContainer().get(crateKey, PersistentDataType.STRING));
    }

    private void save() { try { config.save(new File(plugin.getDataFolder(), "crates.yml")); } catch (Exception ex) { plugin.getLogger().severe("Could not save crates.yml: " + ex.getMessage()); } }
    private void help(Player player) {
        player.sendMessage(color("&6&lGASMP CRATES &8- &cDeveloper/Owner only"));
        player.sendMessage(color("&e/crates list &7- list configured crates"));
        player.sendMessage(color("&e/crates set <id> &7- set target chest/barrel"));
        player.sendMessage(color("&e/crates unset <id> &7- remove location"));
        player.sendMessage(color("&e/crates givekey <player> <id> [amount]"));
        player.sendMessage(color("&e/crates giveblock <player> <id> [amount] &7- custom protected block item"));
        player.sendMessage(color("&e/crates givemodel <player> <id> [amount] &7- animated placed model"));
        player.sendMessage(color("&e/crates reload &7- reload crate definitions"));
    }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("list", "set", "unset", "givekey", "giveblock", "givemodel", "reload");
        return List.of();
    }
}
