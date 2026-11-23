package net.gasmp.core.rank;

import net.gasmp.core.GASMPCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only player-facing rank catalogue backed directly by ranks.yml/LuckPerms definitions. */
public final class RankMenuService implements Listener, CommandExecutor {
    private static final int SIZE = 54;
    private static final int[] CARD_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final String[] TRACKS = {"staff", "creator", "supporter", "prestige"};
    private final GASMPCore plugin;

    public RankMenuService(GASMPCore plugin) { this.plugin = plugin; }

    public void open(Player player) { open(player, "supporter"); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This menu can only be opened in-game.");
            return true;
        }
        open(player);
        return true;
    }

    private void open(Player player, String track) {
        String selected = List.of(TRACKS).contains(track) ? track : "supporter";
        Holder holder = new Holder(selected);
        Inventory inv = Bukkit.createInventory(holder, SIZE, color("&8GASMP &8» &dRanks &7/ &f" + selected.toUpperCase(Locale.ROOT)));
        holder.inventory = inv;
        ItemStack edge = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int row = 0; row < 6; row++) { inv.setItem(row * 9, edge); inv.setItem(row * 9 + 8, edge); }
        for (int col = 0; col < 9; col++) {
            inv.setItem(col, item(col % 2 == 0 ? Material.PURPLE_STAINED_GLASS_PANE : Material.MAGENTA_STAINED_GLASS_PANE, " "));
            inv.setItem(45 + col, item(col % 2 == 0 ? Material.MAGENTA_STAINED_GLASS_PANE : Material.PURPLE_STAINED_GLASS_PANE, " "));
        }
        inv.setItem(4, item(Material.NETHER_STAR, "&d&lGASMP RANKS",
                "&7Browse the official rank catalogue.",
                "&7Groups and prefixes are managed by LuckPerms.",
                "&8Ranks are informational here; purchases are not enabled."));
        Material[] tabIcons = {Material.SHIELD, Material.FIREWORK_ROCKET, Material.DIAMOND, Material.DRAGON_EGG};
        int[] tabSlots = {28, 30, 32, 34};
        for (int i = 0; i < TRACKS.length; i++) {
            String name = TRACKS[i];
            inv.setItem(tabSlots[i], item(tabIcons[i], (name.equals(selected) ? "&a&l» " : "&f") + title(name),
                    "&7View " + name + " ranks", name.equals(selected) ? "&aCurrently selected" : "&eClick to browse"));
        }
        List<RankDefinition> ranks = plugin.getRankService().getRanks().stream()
                .filter(rank -> rank.track().equalsIgnoreCase(selected))
                .sorted((a, b) -> Integer.compare(b.weight(), a.weight())).toList();
        for (int i = 0; i < Math.min(ranks.size(), CARD_SLOTS.length); i++) {
            RankDefinition rank = ranks.get(i);
            inv.setItem(CARD_SLOTS[i], item(rank.iconMaterial(), renderImage(player, rank) + color(rank.prefix()) + color("&l" + rank.displayName()),
                    "&8Rank details",
                    "&7Track: &f" + title(rank.track()),
                    "&7LuckPerms group: &d" + rank.group(),
                    "&7Weight: &f" + rank.weight(),
                    "&7Prefix: " + color(rank.prefix()),
                    "&eClick for details"));
            holder.rankKeys.put(CARD_SLOTS[i], rank.key());
        }
        inv.setItem(49, item(Material.BARRIER, "&c&lClose", "&7Close rank catalogue"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 28 || slot == 30 || slot == 32 || slot == 34) {
            int index = (slot - 28) / 2;
            if (index >= 0 && index < TRACKS.length) open(player, TRACKS[index]);
            return;
        }
        String key = holder.rankKeys.get(slot);
        if (key == null) return;
        RankDefinition rank = plugin.getRankService().getRank(key);
        if (rank == null) return;
        player.sendMessage(color("&d&lGASMP RANKS &8» ") + color(rank.prefix()) + color("&l" + rank.displayName()));
        player.sendMessage(color("&7LuckPerms group: &f" + rank.group() + " &8| &7Track: &f" + rank.track() + " &8| &7Weight: &f" + rank.weight()));
        player.sendMessage(color("&7Prefix preview: ") + color(rank.prefix()) + color("&fYour name"));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
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

    private String renderImage(Player player, RankDefinition rank) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return "";
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method method = api.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            String parsed = (String) method.invoke(null, player, plugin.getRankService().imagePlaceholder(rank));
            if (parsed != null && !parsed.contains("%rpengine_icon_")) return parsed + " ";
        } catch (ReflectiveOperationException ignored) {
            // Keep the vanilla material icon when PlaceholderAPI/RP Engine is unavailable.
        }
        return "";
    }

    private String title(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
    private String color(String value) { return ChatColor.translateAlternateColorCodes('&', value); }

    private static final class Holder implements InventoryHolder {
        private final String track;
        private final java.util.Map<Integer, String> rankKeys = new java.util.HashMap<>();
        private Inventory inventory;
        private Holder(String track) { this.track = track; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
