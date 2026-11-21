package net.gasmp.core.chatgame;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.gasmp.core.GASMPCore;
import net.gasmp.core.data.PlayerDataStore;
import net.gasmp.core.staff.StaffAccess;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

public final class ChatGameService implements Listener, CommandExecutor, TabCompleter {
    private record Round(String prompt, String answer, String type) { }
    private final GASMPCore plugin;
    private final StaffAccess staff;
    private final AtomicReference<Round> active = new AtomicReference<>();
    private final List<String> words = List.of("diamond", "netherite", "creeper", "elytra", "shulker", "beacon", "trident", "ancient", "obsidian", "amethyst");
    private org.bukkit.configuration.file.YamlConfiguration config;
    private BukkitTask scheduler;
    private final java.util.concurrent.atomic.AtomicReference<BukkitTask> timeout = new java.util.concurrent.atomic.AtomicReference<>();
    private boolean manuallyStopped;

    public ChatGameService(GASMPCore plugin, StaffAccess staff) {
        this.plugin = plugin; this.staff = staff; reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "chat-games.yml");
        if (!file.exists()) plugin.saveResource("chat-games.yml", false);
        config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
    }

    public void startScheduler() {
        if (scheduler != null) scheduler.cancel();
        manuallyStopped = false;
        long initial = Math.max(20L, config.getLong("interval-seconds", 600L) * 20L);
        scheduler = Bukkit.getScheduler().runTaskTimer(plugin, this::startRandomRound, initial, initial);
    }

    public void stopAll() {
        if (scheduler != null) scheduler.cancel();
        BukkitTask activeTimeout = timeout.getAndSet(null);
        if (activeTimeout != null) activeTimeout.cancel();
        scheduler = null; active.set(null); manuallyStopped = true;
    }

    private void startRandomRound() {
        if (!config.getBoolean("enabled", true) || manuallyStopped || Bukkit.getOnlinePlayers().isEmpty() || active.get() != null) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Round round;
        if (random.nextBoolean()) {
            int a = random.nextInt(5, 51), b = random.nextInt(5, 51);
            round = new Round("Solve: " + a + " + " + b, Integer.toString(a + b), "math");
        } else {
            String word = words.get(random.nextInt(words.size()));
            round = new Round("Unscramble: " + scramble(word), word, "word");
        }
        startRound(round);
    }

    public void startManualRound() {
        manuallyStopped = false;
        startRandomRound();
    }

    private void startRound(Round round) {
        if (!active.compareAndSet(null, round)) return;
        Bukkit.broadcast(color("&d&lGASMP CHAT GAME &8» &f" + round.prompt()));
        Bukkit.broadcast(color("&7First correct answer wins &8- &d" + config.getLong("reward-shards", 5) + " Shards"));
        BukkitTask previousTimeout = timeout.getAndSet(null);
        if (previousTimeout != null) previousTimeout.cancel();
        BukkitTask newTimeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active.compareAndSet(round, null)) Bukkit.broadcast(color("&cTime is up! The answer was &f" + round.answer() + "&c."));
            timeout.set(null);
        }, Math.max(10L, config.getLong("round-timeout-seconds", 45L)) * 20L);
        timeout.set(newTimeout);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Round round = active.get();
        if (round == null) return;
        String answer = event.message().plainText().trim().toLowerCase(Locale.ROOT);
        if (!answer.equals(round.answer().toLowerCase(Locale.ROOT))) return;
        BukkitTask roundTimeout = timeout.get();
        if (!active.compareAndSet(round, null)) return;
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (roundTimeout != null) roundTimeout.cancel();
            timeout.compareAndSet(roundTimeout, null);
        });
        Player winner = event.getPlayer();
        UUID winnerId = winner.getUniqueId();
        String winnerName = winner.getName();
        long reward = Math.max(1L, config.getLong("reward-shards", 5L));
        plugin.getDataStore().changeShards(winnerId, winnerName, null,
                "CHAT_GAME", reward, PlayerDataStore.ShardChange.GIVE, "Won " + round.type() + " chat game")
                .whenComplete((balance, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        plugin.getLogger().severe("Chat game reward failed for " + winnerName + ": " + error.getMessage());
                        Bukkit.broadcast(color("&d&lCHAT GAME &8» &f" + winnerName + " answered correctly, but reward delivery failed. Please contact staff."));
                    } else {
                        Bukkit.broadcast(color("&d&lCHAT GAME &8» &f" + winnerName + " &awon! &d+" + reward + " Shards"));
                    }
                }));
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || !staff.hasAny(player, "owner", "dev", "admin")) {
            sender.sendMessage(color("&cOnly Owner, Developer or Admin can manage chat games.")); return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            player.sendMessage(color("&dChat game: " + (active.get() == null ? "&7No active round" : "&aRunning"))); return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> { startManualRound(); player.sendMessage(color("&aStarted a chat game if no round was active.")); }
            case "stop" -> { stopAll(); player.sendMessage(color("&cChat games stopped. Use /gchatgame start to resume.")); }
            case "resume" -> { startScheduler(); player.sendMessage(color("&aAutomatic chat games resumed.")); }
            case "reload" -> { reload(); startScheduler(); player.sendMessage(color("&aChat game config reloaded.")); }
            default -> player.sendMessage(color("&e/gchatgame <start|stop|resume|status|reload>"));
        }
        return true;
    }

    private String scramble(String word) {
        List<Character> chars = new ArrayList<>();
        for (char c : word.toCharArray()) chars.add(c);
        String shuffled = word;
        for (int attempt = 0; attempt < 12 && shuffled.equals(word); attempt++) {
            Collections.shuffle(chars);
            StringBuilder out = new StringBuilder();
            chars.forEach(out::append);
            shuffled = out.toString();
        }
        if (shuffled.equals(word) && word.length() > 1) {
            shuffled = new StringBuilder(word).reverse().toString();
        }
        return shuffled;
    }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("start", "stop", "resume", "status", "reload") : List.of();
    }
}
