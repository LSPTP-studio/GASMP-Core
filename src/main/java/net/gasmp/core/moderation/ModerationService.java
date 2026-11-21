package net.gasmp.core.moderation;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.gasmp.core.GASMPCore;
import net.gasmp.core.data.PlayerDataStore;
import net.gasmp.core.staff.StaffAccess;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ModerationService implements Listener, CommandExecutor, TabCompleter {
    private static final Pattern DURATION = Pattern.compile("^(\\d+)([smhdw])$", Pattern.CASE_INSENSITIVE);
    private final GASMPCore plugin;
    private final StaffAccess staff;
    private final Map<UUID, Long> mutedUntil = new ConcurrentHashMap<>();

    public ModerationService(GASMPCore plugin, StaffAccess staff) { this.plugin = plugin; this.staff = staff; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = command.getName().toLowerCase(Locale.ROOT);
        if (args.length < (action.equals("gunban") || action.equals("gunmute") || action.equals("ghistory") ? 1 : 2)) {
            usage(sender, action); return true;
        }
        Player actor = sender instanceof Player p ? p : null;
        if (actor != null && !canModerate(actor, action)) {
            actor.sendMessage(color("&cYou do not have permission to use this moderation command.")); return true;
        }
        String targetName = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        UUID targetUuid = target.getUniqueId();
        int reasonStart = (action.equals("gban") || action.equals("gmute")) ? 2 : 1;
        String reason = args.length > reasonStart ? String.join(" ", Arrays.copyOfRange(args, reasonStart, args.length)) : "No reason supplied";
        long now = System.currentTimeMillis();

        switch (action) {
            case "gban" -> {
                if (args.length < 3) { usage(sender, action); return true; }
                long expiry = parseExpiry(args[1], now);
                if (expiry == Long.MIN_VALUE) { sender.sendMessage(color("&cUse a duration such as 30m, 2h, 7d, 2w or perm.")); return true; }
                Date until = expiry == 0 ? null : new Date(expiry);
                Bukkit.getBanList(BanList.Type.NAME).addBan(targetName, reason, until, sender.getName());
                Player online = Bukkit.getPlayerExact(targetName);
                if (online != null) online.kickPlayer(color("&cYou are banned from GASMP.\n&7Reason: &f" + reason));
                record(target, sender, "BAN", reason, expiry, true);
                sender.sendMessage(color("&aBanned &f" + targetName + "&a."));
            }
            case "gunban" -> {
                Bukkit.getBanList(BanList.Type.NAME).pardon(targetName);
                record(target, sender, "UNBAN", "Ban removed", 0, false);
                sender.sendMessage(color("&aUnbanned &f" + targetName + "&a."));
            }
            case "gmute" -> {
                if (args.length < 3) { usage(sender, action); return true; }
                long expiry = parseExpiry(args[1], now);
                if (expiry == Long.MIN_VALUE || expiry == 0) { sender.sendMessage(color("&cMute duration must be finite, e.g. 10m, 2h, 1d.")); return true; }
                plugin.getDataStore().recordModeration(targetUuid, targetName, actor == null ? null : actor.getUniqueId(),
                        sender.getName(), "MUTE", reason, expiry, true).whenComplete((id, error) ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (error != null) { sender.sendMessage(color("&cCould not save mute.")); return; }
                            mutedUntil.put(targetUuid, expiry);
                            Player online = Bukkit.getPlayer(targetUuid);
                            if (online != null) online.sendMessage(color("&cYou have been muted. Reason: &f" + reason));
                            sender.sendMessage(color("&aMuted &f" + targetName + "&a."));
                        }));
            }
            case "gunmute" -> plugin.getDataStore().deactivateMutes(targetUuid).whenComplete((count, error) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        mutedUntil.remove(targetUuid);
                        sender.sendMessage(color(error == null ? "&aUnmuted " + targetName + "." : "&cCould not update mute status."));
                        Player online = Bukkit.getPlayer(targetUuid);
                        if (online != null && error == null) online.sendMessage(color("&aYour mute has been removed."));
                    }));
            case "gwarn" -> {
                record(target, sender, "WARN", reason, 0, false);
                Player online = Bukkit.getPlayer(targetUuid);
                if (online != null) online.sendMessage(color("&cYou have received a warning: &f" + reason));
                sender.sendMessage(color("&aWarned &f" + targetName + "&a."));
            }
            case "gkick" -> {
                if (args.length < 2) { usage(sender, action); return true; }
                Player online = Bukkit.getPlayerExact(targetName);
                if (online == null) { sender.sendMessage(color("&cThat player is not online.")); return true; }
                online.kickPlayer(color("&cYou were kicked from GASMP.\n&7Reason: &f" + reason));
                record(target, sender, "KICK", reason, 0, false);
                sender.sendMessage(color("&aKicked &f" + targetName + "&a."));
            }
            case "ghistory" -> plugin.getDataStore().getModerationHistory(targetUuid, 10).whenComplete((rows, error) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (error != null) { sender.sendMessage(color("&cCould not load punishment history.")); return; }
                        sender.sendMessage(color("&6&lPUNISHMENT HISTORY &8- &f" + targetName));
                        if (rows.isEmpty()) { sender.sendMessage(color("&7No recorded moderation actions.")); return; }
                        for (PlayerDataStore.ModerationRecord row : rows) {
                            sender.sendMessage(color("&e#" + row.id() + " &f" + row.action() + " &8by &7" + row.actorName()
                                    + " &8- &7" + row.reason() + " &8(" + new Date(row.createdAtMs()) + ")"));
                        }
                    }));
            default -> usage(sender, action);
        }
        return true;
    }

    private boolean canModerate(Player player, String command) {
        return switch (command) {
            case "gban", "gunban", "gkick" -> staff.hasAny(player, "owner", "admin", "manager", "mod");
            case "gmute", "gunmute", "gwarn" -> staff.hasAny(player, "owner", "admin", "manager", "mod", "helper");
            case "ghistory" -> staff.hasAny(player, "owner", "admin", "manager", "mod");
            default -> false;
        };
    }

    private void record(OfflinePlayer target, CommandSender sender, String action, String reason, long expiry, boolean active) {
        UUID actorId = sender instanceof Player player ? player.getUniqueId() : null;
        plugin.getDataStore().recordModeration(target.getUniqueId(), target.getName() == null ? "Unknown" : target.getName(),
                actorId, sender.getName(), action, reason, expiry, active).exceptionally(error -> {
            plugin.getLogger().severe("Could not write moderation history: " + error.getMessage()); return -1L;
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getDataStore().getActiveMute(player.getUniqueId()).thenAccept(record ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (record == null || !player.isOnline()) return;
                    if (record.expiresAtMs() > 0 && record.expiresAtMs() <= System.currentTimeMillis()) {
                        mutedUntil.remove(player.getUniqueId());
                        plugin.getDataStore().deactivateMutes(player.getUniqueId());
                    } else mutedUntil.put(player.getUniqueId(), record.expiresAtMs());
                }));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Long expiry = mutedUntil.get(uuid);
        if (expiry == null) return;
        if (expiry <= System.currentTimeMillis()) { mutedUntil.remove(uuid); return; }
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> event.getPlayer().sendMessage(color("&cYou are muted for another " + formatRemaining(expiry) + ".")));
    }

    private long parseExpiry(String text, long now) {
        if (text.equalsIgnoreCase("perm") || text.equalsIgnoreCase("permanent")) return 0L;
        Matcher matcher = DURATION.matcher(text);
        if (!matcher.matches()) return Long.MIN_VALUE;
        try {
            long amount = Long.parseLong(matcher.group(1));
            long multiplier = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "s" -> 1000L; case "m" -> 60_000L; case "h" -> 3_600_000L;
                case "d" -> 86_400_000L; case "w" -> 604_800_000L; default -> 0L;
            };
            return Math.addExact(now, Math.multiplyExact(amount, multiplier));
        } catch (ArithmeticException | NumberFormatException ex) { return Long.MIN_VALUE; }
    }

    private String formatRemaining(long expiry) {
        long seconds = Math.max(1, (expiry - System.currentTimeMillis()) / 1000L);
        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60; seconds %= 60;
        return (days > 0 ? days + "d " : "") + (hours > 0 ? hours + "h " : "")
                + (minutes > 0 ? minutes + "m " : "") + seconds + "s";
    }

    private void usage(CommandSender sender, String action) {
        String text = switch (action) {
            case "gban" -> "/gban <player> <duration|perm> <reason>";
            case "gunban" -> "/gunban <player>";
            case "gmute" -> "/gmute <player> <duration> <reason>";
            case "gunmute" -> "/gunmute <player>";
            case "gwarn" -> "/gwarn <player> <reason>";
            case "gkick" -> "/gkick <player> <reason>";
            case "ghistory" -> "/ghistory <player>";
            default -> "/gban, /gunban, /gmute, /gunmute, /gwarn, /gkick, /ghistory";
        };
        sender.sendMessage(color("&eUsage: " + text));
    }

    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && (command.getName().equalsIgnoreCase("gban") || command.getName().equalsIgnoreCase("gmute")))
            return List.of("10m", "30m", "1h", "1d", "7d", "perm");
        return List.of();
    }
}
