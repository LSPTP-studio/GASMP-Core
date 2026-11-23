package net.gasmp.core.rank;

import net.gasmp.core.GASMPCore;
import net.gasmp.core.gui.RpEngineUi;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public final class RankAdminMenu implements Listener, CommandExecutor, TabCompleter {
    private static final int[] SLOTS = {
            10,11,12,13,14,15,16, 19,20,21,22,23,24,25,
            28,29,30,31,32,33,34, 37,38,39,40,41,42,43
    };

    private final GASMPCore plugin;
    private final RankService ranks;

    public RankAdminMenu(GASMPCore plugin, RankService ranks) {
        this.plugin = plugin;
        this.ranks = ranks;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cThis command must be run in-game."));
            return true;
        }
        if (!plugin.getConfig().getBoolean("modules.rank-admin", true)) {
            player.sendMessage(color("&cRank administration is disabled."));
            return true;
        }
        openFor(player, args.length > 0 ? args[0] : null);
        return true;
    }

    private void openFor(Player actor, String targetName) {
        ranks.getAccess(actor.getUniqueId()).whenComplete((access, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!actor.isOnline()) return;
                    if (error != null) {
                        actor.sendMessage(color("&cRank system unavailable. Make sure LuckPerms is enabled."));
                        plugin.getLogger().warning("Rank panel access failed: " + error.getMessage());
                        return;
                    }
                    if (!access.authorized() || !actor.hasPermission("gasmpcore.rankadmin")) {
                        actor.sendMessage(color("&cOnly configured Developers and Owners can access this panel."));
                        return;
                    }
                    if (targetName != null) {
                        Player target = Bukkit.getPlayerExact(targetName);
                        if (target == null) {
                            actor.sendMessage(color("&cThat player is not online. Open /gasmpadmin to browse players."));
                            return;
                        }
                        openRanks(actor, target.getUniqueId(), target.getName(), 0, access.owner());
                    } else {
                        openTargets(actor, 0);
                    }
                })
        );
    }

    private void openTargets(Player actor, int page) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        RankAdminHolder holder = new RankAdminHolder(RankAdminHolder.Stage.TARGETS, null, null, null, page);
        Inventory inv = create(holder, 54, "&8GASMP &7• &dRank Administration");

        int start = page * SLOTS.length;
        int end = Math.min(start + SLOTS.length, players.size());
        int index = 0;
        for (int i = start; i < end; i++) {
            Player target = players.get(i);
            int slot = SLOTS[index++];
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.setDisplayName(color("&f" + target.getName()));
            meta.setLore(List.of(color("&7Click to manage ranks.")));
            head.setItemMeta(meta);
            inv.setItem(slot, head);
            holder.targetAt(slot, target.getUniqueId());
        }
        set(inv, 4, Material.NAME_TAG, "&d&lGASMP Rank Admin",
                "&7Select an online player.", "&7Online: &f" + players.size());
        if (page > 0) set(inv, 45, Material.ARROW, "&fPrevious page");
        if (end < players.size()) set(inv, 53, Material.ARROW, "&fNext page");
        set(inv, 49, Material.BARRIER, "&cClose");
        actor.openInventory(inv);
    }

    private void openRanks(Player actor, UUID targetUuid, String targetName, int page, boolean owner) {
        List<RankDefinition> options = ranks.getRanks().stream()
                .filter(rank -> owner || ranks.canDeveloperGrant(rank))
                .sorted(Comparator.comparingInt(RankDefinition::weight).reversed())
                .collect(Collectors.toList());

        RankAdminHolder holder = new RankAdminHolder(
                RankAdminHolder.Stage.RANKS, targetUuid, targetName, null, page);
        Inventory inv = create(holder, 54, "&8GASMP &7• &dChoose Rank");
        set(inv, 4, Material.PLAYER_HEAD, "&f" + targetName,
                "&7Choose a rank.", "&7One rank per track; other tracks remain.");

        int start = page * SLOTS.length;
        int end = Math.min(start + SLOTS.length, options.size());
        int index = 0;
        for (int i = start; i < end; i++) {
            RankDefinition rank = options.get(i);
            int slot = SLOTS[index++];
            ItemStack item = new ItemStack(rank.iconMaterial());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(color(renderImage(actor, rank) + "&f" + rank.displayName()));
            meta.setLore(List.of(
                    color("&7LuckPerms group: &f" + rank.group()),
                    color("&7Track: &f" + rank.track()),
                    color("&7Weight: &f" + rank.weight()),
                    color("&eClick to review.")
            ));
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            holder.rankAt(slot, rank.key());
        }

        set(inv, 45, Material.ARROW, "&fBack to players");
        if (page > 0) set(inv, 46, Material.ARROW, "&fPrevious page");
        if (end < options.size()) set(inv, 52, Material.ARROW, "&fNext page");
        set(inv, 49, Material.BARRIER, "&cClose");
        actor.openInventory(inv);
    }

    private void openConfirm(Player actor, UUID targetUuid, String targetName, String rankKey) {
        RankDefinition rank = ranks.getRank(rankKey);
        if (rank == null) return;

        RankAdminHolder holder = new RankAdminHolder(
                RankAdminHolder.Stage.CONFIRM, targetUuid, targetName, rankKey, 0);
        Inventory inv = create(holder, 27, "&8Confirm Rank Change");
        set(inv, 4, Material.NAME_TAG, "&f" + targetName, "&7Target player");
        ItemStack rankItem = new ItemStack(rank.iconMaterial());
        ItemMeta meta = rankItem.getItemMeta();
        meta.setDisplayName(color(renderImage(actor, rank) + "&f" + rank.displayName()));
        meta.setLore(List.of(
                color("&7Prefix: " + rank.prefix()),
                color("&7Track: &f" + rank.track()),
                color("&7Only this track will be replaced.")
        ));
        rankItem.setItemMeta(meta);
        inv.setItem(13, rankItem);
        set(inv, 11, Material.LIME_CONCRETE, "&a&lCONFIRM", "&7Save rank through LuckPerms.");
        set(inv, 15, Material.RED_CONCRETE, "&c&lCANCEL", "&7Return to ranks.");
        actor.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RankAdminHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        if (slot == 49) {
            player.closeInventory();
            return;
        }

        switch (holder.stage()) {
            case TARGETS -> {
                if (slot == 45 && holder.page() > 0) openTargets(player, holder.page() - 1);
                else if (slot == 53) openTargets(player, holder.page() + 1);
                else {
                    UUID id = holder.targetAt(slot);
                    if (id == null) return;
                    Player target = Bukkit.getPlayer(id);
                    if (target == null) {
                        player.sendMessage(color("&cThat player went offline."));
                        openTargets(player, holder.page());
                        return;
                    }
                    checkAccess(player, access -> openRanks(player, id, target.getName(), 0, access.owner()));
                }
            }
            case RANKS -> {
                if (slot == 45) openTargets(player, 0);
                else if (slot == 46 && holder.page() > 0)
                    checkAccess(player, access -> openRanks(player, holder.targetUuid(), holder.targetName(), holder.page()-1, access.owner()));
                else if (slot == 52)
                    checkAccess(player, access -> openRanks(player, holder.targetUuid(), holder.targetName(), holder.page()+1, access.owner()));
                else {
                    String rankKey = holder.rankAt(slot);
                    if (rankKey != null) openConfirm(player, holder.targetUuid(), holder.targetName(), rankKey);
                }
            }
            case CONFIRM -> {
                if (slot == 15) {
                    checkAccess(player, access -> openRanks(player, holder.targetUuid(), holder.targetName(), 0, access.owner()));
                } else if (slot == 11) {
                    player.closeInventory();
                    ranks.grantRank(player.getUniqueId(), holder.targetUuid(), holder.rankKey())
                            .whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                                if (error != null) {
                                    player.sendMessage(color("&cRank update failed: &7" + error.getMessage()));
                                    return;
                                }
                                if (!result.success()) {
                                    player.sendMessage(color("&c" + result.message()));
                                    return;
                                }
                                ranks.recordAudit(player.getUniqueId(), holder.targetUuid(), result);
                                player.sendMessage(color("&a" + holder.targetName() + " is now &f"
                                        + result.rank().displayName() + "&a."));
                                Player target = Bukkit.getPlayer(holder.targetUuid());
                                if (target != null) rankReveal(target, result.rank());
                            }));
                }
            }
        }
    }

    private void checkAccess(Player player, java.util.function.Consumer<RankService.Access> success) {
        ranks.getAccess(player.getUniqueId()).whenComplete((access, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null || access == null || !access.authorized()
                            || !player.hasPermission("gasmpcore.rankadmin")) {
                        player.closeInventory();
                        player.sendMessage(color("&cYou are no longer authorized to use this panel."));
                        return;
                    }
                    success.accept(access);
                })
        );
    }

    private void rankReveal(Player target, RankDefinition rank) {
        target.sendMessage(color("&d&lGASMP &8» &fYour rank is now " + rank.prefix() + "&f."));
        if (!plugin.getConfig().getBoolean("rank-admin.play-rank-up-cinematic", true)) return;

        ChatColor accent = switch (rank.track().toLowerCase(Locale.ROOT)) {
            case "staff" -> ChatColor.LIGHT_PURPLE;
            case "creator" -> ChatColor.AQUA;
            case "prestige" -> ChatColor.GOLD;
            default -> ChatColor.GREEN;
        };
        target.sendTitle(color("&d&lPROMOTION CONFIRMED"),
                accent + "LuckPerms rank successfully saved", 5, 35, 10);
        target.playSound(target.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.85f, 1.15f);

        final int[] frame = {0};
        final org.bukkit.scheduler.BukkitTask[] cinematic = new org.bukkit.scheduler.BukkitTask[1];
        cinematic[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!target.isOnline()) {
                cinematic[0].cancel();
                return;
            }

            double progress = frame[0] / 20.0;
            double radius = 0.45 + (progress * 0.65);
            double height = 0.25 + (progress * 1.05);
            org.bukkit.Location center = target.getLocation().add(0, 0.15, 0);
            Particle orbitParticle = rank.track().equalsIgnoreCase("prestige")
                    ? Particle.TOTEM_OF_UNDYING : Particle.END_ROD;

            for (int point = 0; point < 18; point++) {
                double angle = (Math.PI * 2.0 * point / 18.0) + (frame[0] * 0.22);
                org.bukkit.Location particleLocation = center.clone().add(
                        Math.cos(angle) * radius,
                        height + Math.sin(angle * 2.0) * 0.18,
                        Math.sin(angle) * radius);
                target.getWorld().spawnParticle(orbitParticle, particleLocation, 1, 0, 0, 0, 0);
            }

            if (frame[0] == 7) {
                target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.0f);
            }
            if (frame[0] >= 20) {
                cinematic[0].cancel();
                target.getWorld().spawnParticle(Particle.FIREWORK,
                        target.getLocation().add(0, 1.0, 0), 42, 0.65, 0.9, 0.65, 0.12);
                target.playSound(target.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                target.sendTitle(color("&6&lRANK UNLOCKED"),
                        color(rank.prefix() + rank.displayName()), 5, 65, 20);
                target.sendMessage(color("&7Your new rank has been saved through LuckPerms."));
            }
            frame[0]++;
        }, 0L, 2L);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof RankAdminHolder) {
            event.setCancelled(true);
        }
    }

    private Inventory create(RankAdminHolder holder, int size, String title) {
        String screen = holder.stage() == RankAdminHolder.Stage.CONFIRM ? "rank_confirm" : "rank_admin";
        Inventory inv = Bukkit.createInventory(holder, size, RpEngineUi.title(screen, title));
        holder.setInventory(inv);
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < size; i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == size / 9 - 1 || col == 0 || col == 8) inv.setItem(i, filler.clone());
        }
        return inv;
    }

    private void set(Inventory inv, int slot, Material material, String name, String... lore) {
        inv.setItem(slot, item(material, name, lore));
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

    private String renderImage(Player player, RankDefinition rank) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return "";
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method method = api.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            String parsed = (String) method.invoke(null, player, ranks.imagePlaceholder(rank));
            if (parsed != null && !parsed.contains("%img_")) return parsed + " ";
        } catch (ReflectiveOperationException ignored) {
            // Plain rank label and vanilla item icon remain the safe fallback.
        }
        return "";
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
