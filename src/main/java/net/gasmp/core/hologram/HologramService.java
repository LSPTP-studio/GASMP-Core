package net.gasmp.core.hologram;

import net.gasmp.core.GASMPCore;
import net.gasmp.core.staff.StaffAccess;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;

public final class HologramService implements CommandExecutor, TabCompleter {
    private final GASMPCore plugin;
    private final StaffAccess staff;
    private final File file;
    private final NamespacedKey idKey;
    private org.bukkit.configuration.file.YamlConfiguration config;
    private final Map<String, Long> spawnTokens = new HashMap<>();
    private long nextSpawnToken;

    public HologramService(GASMPCore plugin, StaffAccess staff) {
        this.plugin = plugin; this.staff = staff;
        this.file = new File(plugin.getDataFolder(), "holograms.yml");
        this.idKey = new NamespacedKey(plugin, "hologram_id");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            config = new org.bukkit.configuration.file.YamlConfiguration();
            save();
        }
        config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        Set<String> configured = new HashSet<>(config.getKeys(false));
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof TextDisplay display)) continue;
                String id = display.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
                if (id != null && !configured.contains(id)) display.remove();
            }
        }
        spawnTokens.keySet().removeIf(id -> !configured.contains(id));
        for (String id : configured) spawnOrUpdate(id);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) { sender.sendMessage("Run this command in-game."); return true; }
        if (!staff.hasAny(player, "owner", "dev", "admin")) { player.sendMessage(color("&cOnly Owner, Developer or Admin can manage holograms.")); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { help(player); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(player, args);
            case "set" -> setText(player, args);
            case "delete" -> delete(player, args);
            case "list" -> list(player);
            case "reload" -> { reload(); player.sendMessage(color("&aHolograms reloaded.")); }
            default -> help(player);
        }
        return true;
    }

    private void create(org.bukkit.entity.Player player, String[] args) {
        if (args.length < 3) { player.sendMessage(color("&e/gholo create <id> <text...>")); return; }
        String id = normalize(args[1]);
        if (config.contains(id)) { player.sendMessage(color("&cThat hologram ID already exists.")); return; }
        String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        saveLocation(id, player.getLocation(), text);
        spawnOrUpdate(id);
        player.sendMessage(color("&aCreated hologram &f" + id + "&a."));
    }

    private void setText(org.bukkit.entity.Player player, String[] args) {
        if (args.length < 3) { player.sendMessage(color("&e/gholo set <id> <text...>")); return; }
        String id = normalize(args[1]);
        if (!config.contains(id)) { player.sendMessage(color("&cUnknown hologram ID.")); return; }
        config.set(id + ".text", String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        save(); spawnOrUpdate(id); player.sendMessage(color("&aUpdated hologram &f" + id + "&a."));
    }

    private void delete(org.bukkit.entity.Player player, String[] args) {
        if (args.length != 2) { player.sendMessage(color("&e/gholo delete <id>")); return; }
        String id = normalize(args[1]);
        if (!config.contains(id)) { player.sendMessage(color("&cUnknown hologram ID.")); return; }
        config.set(id, null); spawnTokens.remove(id); save(); removeEntities(id);
        player.sendMessage(color("&aDeleted hologram &f" + id + "&a."));
    }

    private void list(org.bukkit.entity.Player player) {
        player.sendMessage(color("&d&lGASMP HOLOGRAMS"));
        if (config.getKeys(false).isEmpty()) { player.sendMessage(color("&7No holograms configured.")); return; }
        for (String id : config.getKeys(false)) player.sendMessage(color("&e" + id + " &8- &f" + config.getString(id + ".text", "")));
    }

    private void saveLocation(String id, Location location, String text) {
        config.set(id + ".world", location.getWorld().getName());
        config.set(id + ".x", location.getX()); config.set(id + ".y", location.getY()); config.set(id + ".z", location.getZ());
        config.set(id + ".yaw", location.getYaw()); config.set(id + ".pitch", location.getPitch());
        config.set(id + ".text", text); save();
    }

    private void spawnOrUpdate(String id) {
        if (!config.contains(id)) return;
        long token = ++nextSpawnToken;
        spawnTokens.put(id, token);
        String worldName = config.getString(id + ".world");
        World world = Bukkit.getWorld(worldName == null ? "" : worldName);
        if (world == null) return;
        Location location = new Location(world, config.getDouble(id + ".x"), config.getDouble(id + ".y"),
                config.getDouble(id + ".z"), (float) config.getDouble(id + ".yaw"), (float) config.getDouble(id + ".pitch"));
        world.getChunkAtAsync(location).whenComplete((chunk, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!Objects.equals(spawnTokens.get(id), token) || !config.contains(id)) return;
            if (error != null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;
            TextDisplay display = findEntity(world, id, location);
            if (display == null) {
                display = world.spawn(location, TextDisplay.class);
                display.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
            }
            display.text(LegacyComponentSerializer.legacyAmpersand().deserialize(config.getString(id + ".text", "")));
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(true);
            display.setDefaultBackground(false);
            display.setPersistent(true);
            display.setInvulnerable(true);
            display.setGravity(false);
            display.setSilent(true);
            display.setViewRange(48.0f);
        }));
    }

    private TextDisplay findEntity(World world, String id, Location location) {
        for (Entity entity : world.getNearbyEntities(location, 1.5, 1.5, 1.5)) {
            if (entity instanceof TextDisplay display
                    && id.equals(display.getPersistentDataContainer().get(idKey, PersistentDataType.STRING))) return display;
        }
        return null;
    }

    private void removeEntities(String id) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof TextDisplay display
                        && id.equals(display.getPersistentDataContainer().get(idKey, PersistentDataType.STRING))) entity.remove();
            }
        }
    }

    private String normalize(String id) { return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", ""); }
    private void save() { try { config.save(file); } catch (Exception ex) { plugin.getLogger().severe("Could not save holograms.yml: " + ex.getMessage()); } }
    private void help(org.bukkit.entity.Player player) {
        player.sendMessage(color("&d&lGASMP HOLOGRAMS"));
        player.sendMessage(color("&e/gholo create <id> <text...> &7- create where you stand"));
        player.sendMessage(color("&e/gholo set <id> <text...> &7- update text"));
        player.sendMessage(color("&e/gholo delete <id> &7- remove hologram"));
        player.sendMessage(color("&e/gholo list &7- list holograms"));
    }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("create", "set", "delete", "list", "reload");
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("delete"))) return new ArrayList<>(config.getKeys(false));
        return List.of();
    }
}
