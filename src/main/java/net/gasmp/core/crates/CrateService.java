package net.gasmp.core.crates;

import net.gasmp.core.GASMPCore;
import net.gasmp.core.staff.StaffAccess;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
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
        // RP Engine dispatches this internal route through console when a custom crate is clicked.
        if (command.getName().equalsIgnoreCase("gcrateopen")) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sender.sendMessage("This is an internal crate interaction command.");
                return true;
            }
            if (args.length != 2) return true;
            Player target = Bukkit.getPlayerExact(args[0]);
            String crateId = resolveCrateId(args[1]);
            if (target != null && isCrateId(crateId)) openCrate(target, crateId);
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
        String subcommand = suffix.equals("_crate_block") ? "giveblock" : "givemodel";
        if (args.length < 3 || args.length > 4) {
            actor.sendMessage(color("&e/crates " + subcommand + " <online-player> <crate-id> [amount]"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        String id = resolveCrateId(args[2]);
        if (target == null) {
            actor.sendMessage(color("&cTarget player &f" + args[1] + " &cis not online. They must be online to receive crate items."));
            return;
        }
        if (!isCrateId(id)) {
            actor.sendMessage(color("&cUnknown crate tier &f" + args[2] + "&c. Available tiers: &f" + availableCrateIds()));
            actor.sendMessage(color("&7Use the tier name (common/rare/epic/legendary) or its gasmp_crates item ID."));
            return;
        }
        int amount = 1;
        if (args.length == 4) try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException ex) { amount = 0; }
        if (amount < 1 || amount > 64) {
            actor.sendMessage(color("&cAmount must be between 1 and 64."));
            return;
        }
        String contentId = "gasmp_crates:" + id + suffix;
        ItemStack custom = createRPEngineItem(contentId, amount);
        if (custom == null) {
            actor.sendMessage(color("&cRP Engine is not installed or content ID " + contentId
                    + " is unavailable. Load the GASMP RP Engine content pack first."));
            return;
        }
        Map<Integer, ItemStack> left = target.getInventory().addItem(custom);
        left.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
        actor.sendMessage(color("&aGave " + amount + " custom " + id + " crate item(s) to " + target.getName() + "."));
    }

    private void setLocation(Player player, String[] args) {
        if (args.length != 2) { player.sendMessage(color("&e/crates set <crate-id>")); return; }
        String id = resolveCrateId(args[1]);
        if (!isCrateId(id)) {
            player.sendMessage(color("&cUnknown crate tier &f" + args[1] + "&c. Available: &f" + availableCrateIds()));
            return;
        }
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
        String id = resolveCrateId(args[1]);
        if (!isCrateId(id)) {
            player.sendMessage(color("&cUnknown crate tier &f" + args[1] + "&c. Available: &f" + availableCrateIds()));
            return;
        }
        config.set("locations." + id, null);
        save(); player.sendMessage(color("&aCrate location removed for &f" + id + "&a."));
    }

    private void giveKey(Player player, String[] args) {
        if (args.length < 3 || args.length > 4) { player.sendMessage(color("&e/crates givekey <online-player> <crate-id> [amount]")); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        String id = resolveCrateId(args[2]);
        if (target == null) { player.sendMessage(color("&cTarget player must be online.")); return; }
        if (!isCrateId(id)) {
            player.sendMessage(color("&cUnknown crate tier &f" + args[2] + "&c. Available: &f" + availableCrateIds()));
            player.sendMessage(color("&7Use common, rare, epic or legendary; namespaced key IDs are also accepted."));
            return;
        }
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
            String physical = "&eUse a chest, trapped chest or barrel registered with /crates set";
            player.sendMessage(color(displayName(id) + " &8- " + physical
                    + (placed ? " &8| &aLocation registered" : "")));
        }
    }

    private String resolveCrateId(String input) {
        if (input == null) return "";
        String id = input.trim().toLowerCase(Locale.ROOT);
        int namespace = id.indexOf(':');
        if (namespace >= 0) id = id.substring(namespace + 1);
        for (String suffix : List.of("_crate_block", "_crate_display", "_crate_key", "_key")) {
            if (id.endsWith(suffix)) {
                id = id.substring(0, id.length() - suffix.length());
                break;
            }
        }
        return id;
    }

    private boolean isCrateId(String id) {
        return id != null && !id.isBlank()
                && config.getConfigurationSection("crates." + id) != null;
    }

    private String availableCrateIds() {
        ConfigurationSection crates = config.getConfigurationSection("crates");
        if (crates == null || crates.getKeys(false).isEmpty()) return "(none; check plugins/GASMP-Core/crates.yml)";
        return String.join(", ", crates.getKeys(false).stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
    }

    private String displayName(String id) {
        ConfigurationSection crate = config.getConfigurationSection("crates." + id);
        String configured = crate == null ? null : crate.getString("display-name");
        if (configured == null || configured.isBlank()) {
            return "&d&l" + id.replace('_', ' ').toUpperCase(Locale.ROOT);
        }
        return configured;
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
        // Only Owner/Developer can remove a protected crate. RP Engine handles custom-block drops.
        if (staff.isOwnerOrDev(event.getPlayer())) {
            if (configuredLocation) {
                ConfigurationSection locations = config.getConfigurationSection("locations");
                if (locations != null) {
                    for (String id : new ArrayList<>(locations.getKeys(false))) {
                        ConfigurationSection location = locations.getConfigurationSection(id);
                        if (location != null
                                && location.getString("world", "").equals(block.getWorld().getName())
                                && location.getInt("x") == block.getX() && location.getInt("y") == block.getY()
                                && location.getInt("z") == block.getZ()) config.set("locations." + id, null);
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
            player.sendMessage(color("&cYou need a key for " + displayName(id) + "&c."));
            return;
        }

        List<ConfigurationSection> rewards = new ArrayList<>();
        ConfigurationSection rewardSection = crate.getConfigurationSection("rewards");
        long totalWeight = 0L;
        if (rewardSection != null) for (String key : rewardSection.getKeys(false)) {
            ConfigurationSection reward = rewardSection.getConfigurationSection(key);
            if (reward == null) continue;
            int weight = reward.getInt("weight", 0);
            String type = reward.getString("type", "").toLowerCase(Locale.ROOT);
            String cosmeticId = reward.getString("item", "");
            if (weight > 0 && type.equals("cosmetic")
                    && cosmeticId.matches("[a-z0-9._-]+:[a-z0-9._/-]+")
                    && totalWeight + weight <= Integer.MAX_VALUE) {
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
        animateOpening(player, id, chosen, rewards);
    }

    private void animateOpening(Player player, String crateId, ConfigurationSection reward, List<ConfigurationSection> rewardPool) {
        RarityStyle rarity = rarityStyle(reward);
        CrateAnimationHolder holder = new CrateAnimationHolder(crateId);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                color("&8GASMP &7• ") + color(displayName(crateId)));
        holder.inventory(inventory);
        ItemStack filler = named(rarity.pane(), " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) inventory.setItem(i, filler.clone());
        }
        inventory.setItem(13, named(Material.CHEST,
                rarity.color().toString() + ChatColor.BOLD.toString() + "OPENING...",
                "&7The " + rarity.label() + " reward is being rolled."));
        player.openInventory(inventory);

        // Cache real RP Engine hat icons for the reel; crate animation never displays currency or gear.
        List<ItemStack> reelItems = new ArrayList<>();
        for (ConfigurationSection entry : rewardPool) {
            ItemStack hat = createRPEngineItem(entry.getString("item", ""), 1);
            if (hat == null) continue;
            ItemMeta hatMeta = hat.getItemMeta();
            if (hatMeta != null) {
                hatMeta.setDisplayName(color("&d" + entry.getString("display-name", entry.getString("item", "Cosmetic"))));
                hat.setItemMeta(hatMeta);
            }
            reelItems.add(hat);
        }
        if (reelItems.isEmpty()) reelItems.add(named(Material.PAPER, "&eCOSMETIC"));

        final int[] ticks = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != holder) {
                BukkitTask current = animationTasks.remove(player.getUniqueId());
                if (current != null) current.cancel();
                opening.remove(player.getUniqueId());
                return;
            }

            ticks[0]++;
            // The reel slows down toward the reveal. Higher rarity has a longer, more dramatic spin.
            int spinInterval = Math.max(2, 7 - (ticks[0] / 12));
            if (ticks[0] % spinInterval == 0) {
                for (int slot : new int[]{11, 13, 15}) {
                    ItemStack preview = reelItems.get(ThreadLocalRandom.current().nextInt(reelItems.size()));
                    inventory.setItem(slot, preview.clone());
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.45f,
                        Math.min(1.8f, 0.65f + (ticks[0] / 55.0f)));
            }

            if (ticks[0] >= rarity.durationTicks()) {
                BukkitTask current = animationTasks.remove(player.getUniqueId());
                if (current != null) current.cancel();

                inventory.setItem(13, rewardPreview(reward, rarity));
                player.playSound(player.getLocation(), rarity.revealSound(), 1.0f, rarity.pitch());
                player.sendTitle(color("&8CRATE OPENED"),
                        rarity.color().toString() + ChatColor.BOLD.toString() + rarity.label().toUpperCase(Locale.ROOT) + " REWARD",
                        5, 35, 10);
                player.getWorld().spawnParticle(rarity.particle(),
                        player.getLocation().add(0, 1.0, 0), rarity.particleCount(),
                        0.55, 0.7, 0.55, rarity.particleSpeed());

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != holder) {
                        opening.remove(player.getUniqueId());
                        return;
                    }
                    boolean mainHand = isKey(player.getInventory().getItemInMainHand(), crateId);
                    ItemStack keyItem = mainHand ? player.getInventory().getItemInMainHand()
                            : player.getInventory().getItemInOffHand();
                    if (!isKey(keyItem, crateId)) {
                        opening.remove(player.getUniqueId());
                        player.sendMessage(color("&cYour crate key is no longer in your hand. No reward was issued."));
                        player.closeInventory();
                        return;
                    }
                    consumeKey(player, mainHand, keyItem);
                    player.closeInventory();
                    deliver(player, reward, crateId);
                }, 18L);
            }
        }, 0L, 1L);
        animationTasks.put(player.getUniqueId(), task);
    }

    private RarityStyle rarityStyle(ConfigurationSection reward) {
        String rarity = reward.getString("rarity", "common").toLowerCase(Locale.ROOT);
        return switch (rarity) {
            case "rare" -> new RarityStyle("Rare", ChatColor.AQUA, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                    Sound.BLOCK_AMETHYST_BLOCK_CHIME, Particle.CRIT, 52, 1.15f, 26, 0.08f);
            case "epic" -> new RarityStyle("Epic", ChatColor.LIGHT_PURPLE, Material.PURPLE_STAINED_GLASS_PANE,
                    Sound.ENTITY_PLAYER_LEVELUP, Particle.END_ROD, 66, 1.2f, 42, 0.025f);
            case "legendary" -> new RarityStyle("Legendary", ChatColor.GOLD, Material.YELLOW_STAINED_GLASS_PANE,
                    Sound.UI_TOAST_CHALLENGE_COMPLETE, Particle.TOTEM_OF_UNDYING, 82, 1.0f, 64, 0.18f);
            default -> new RarityStyle("Common", ChatColor.GRAY, Material.GRAY_STAINED_GLASS_PANE,
                    Sound.BLOCK_NOTE_BLOCK_PLING, Particle.HAPPY_VILLAGER, 40, 1.0f, 14, 0.02f);
        };
    }

    private ItemStack rewardPreview(ConfigurationSection reward, RarityStyle rarity) {
        String itemId = reward.getString("item", "");
        String display = reward.getString("display-name", itemId);
        ItemStack cosmetic = createRPEngineItem(itemId, 1);
        if (cosmetic == null) {
            return named(Material.BARRIER,
                    rarity.color().toString() + ChatColor.BOLD + "COSMETIC UNAVAILABLE",
                    "&cRP Engine item is not loaded: " + itemId,
                    "&7Your key will be returned.");
        }
        ItemMeta meta = cosmetic.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(rarity.color().toString() + ChatColor.BOLD + display));
            meta.setLore(List.of(
                    color("&7Wearable cosmetic"),
                    color("&7Crate tier: " + rarity.color() + rarity.label()),
                    color("&8RP Engine: " + itemId)));
            cosmetic.setItemMeta(meta);
        }
        return cosmetic;
    }

    private record RarityStyle(String label, ChatColor color, Material pane, Sound revealSound,
                               Particle particle, int durationTicks, float pitch,
                               int particleCount, float particleSpeed) { }

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
        String itemId = reward.getString("item", "");
        String display = reward.getString("display-name", itemId);
        if (!itemId.matches("[a-z0-9._-]+:[a-z0-9._/-]+")) {
            refundKey(player, crateId, "Configured cosmetic ID is invalid.");
            plugin.getLogger().warning("Invalid cosmetic ID in crate " + crateId + ": " + itemId);
            return;
        }

        ItemStack cosmetic = createRPEngineItem(itemId, 1);
        if (cosmetic == null) {
            refundKey(player, crateId, "RP Engine cosmetic is not loaded. Install/reload the GASMP cosmetic content pack.");
            return;
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(cosmetic);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(color(displayName(crateId) + " &8» &fUnlocked wearable cosmetic: &d" + display));
        opening.remove(player.getUniqueId());
    }

    private void refundKey(Player player, String id, String reason) {
        Map<Integer, ItemStack> left = player.getInventory().addItem(createKey(id, 1));
        left.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(color("&cCrate opening cancelled: " + reason + " Your key was returned."));
        opening.remove(player.getUniqueId());
    }

    private ItemStack createKey(String id, int amount) {
        ConfigurationSection crate = config.getConfigurationSection("crates." + id);
        String keyItemId = crate == null ? "gasmp_crates:" + id + "_key"
                : crate.getString("key-item", "gasmp_crates:" + id + "_key");
        ItemStack item = createRPEngineItem(keyItemId, amount);
        if (item == null) item = new ItemStack(Material.TRIPWIRE_HOOK, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String keyName = crate == null ? null : crate.getString("key-name");
            if (keyName == null || keyName.isBlank()) keyName = "&d&l" + id.toUpperCase(Locale.ROOT) + " KEY";
            String keyLore = crate == null ? "&7Use at the matching GASMP crate." :
                    crate.getString("key-lore", "&7Unlocks the " + id + " crate.");
            meta.setDisplayName(color(keyName));
            meta.setLore(List.of(color(keyLore), color("&8GASMP • CRATE KEY")));
            meta.getPersistentDataContainer().set(crateKey, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        return item;
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

    private boolean isKey(ItemStack item, String id) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return id.equals(item.getItemMeta().getPersistentDataContainer().get(crateKey, PersistentDataType.STRING));
    }

    private void save() { try { config.save(new File(plugin.getDataFolder(), "crates.yml")); } catch (Exception ex) { plugin.getLogger().severe("Could not save crates.yml: " + ex.getMessage()); } }
    private void help(Player player) {
        player.sendMessage(color("&6&lGASMP CRATES &8- &cDeveloper/Owner only"));
        player.sendMessage(color("&e/crates list &7- list configured crate types"));
        player.sendMessage(color("&e/crates givekey <player> <id> [amount] &7- give the matching RP Engine key"));
        player.sendMessage(color("&e/crates giveblock <player> <id> [amount] &7- give custom crate block"));
        player.sendMessage(color("&e/crates givemodel <player> <id> [amount] &7- give animated crate display"));
        player.sendMessage(color("&e/crates set <id> &7- register a vanilla chest/barrel fallback"));
        player.sendMessage(color("&e/crates unset <id> &7- remove a vanilla fallback location"));
        player.sendMessage(color("&e/crates reload &7- reload crate definitions"));
    }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("list", "set", "unset", "givekey", "giveblock", "givemodel", "reload");
        if (args.length == 2 && Set.of("set", "unset").contains(args[0].toLowerCase(Locale.ROOT))) {
            return config.getConfigurationSection("crates") == null ? List.of()
                    : config.getConfigurationSection("crates").getKeys(false).stream().sorted().toList();
        }
        if (args.length == 2 && Set.of("givekey", "giveblock", "givemodel").contains(args[0].toLowerCase(Locale.ROOT))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        if (args.length == 3 && Set.of("givekey", "giveblock", "givemodel").contains(args[0].toLowerCase(Locale.ROOT))) {
            return config.getConfigurationSection("crates") == null ? List.of()
                    : config.getConfigurationSection("crates").getKeys(false).stream().sorted().toList();
        }
        return List.of();
    }
}
