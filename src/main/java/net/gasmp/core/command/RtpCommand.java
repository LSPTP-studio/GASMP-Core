package net.gasmp.core.command;

import net.gasmp.core.GASMPCore;
import net.gasmp.core.gui.MenuPage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class RtpCommand implements CommandExecutor, TabCompleter {
    private final GASMPCore plugin;

    public RtpCommand(GASMPCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("RTP is a player command.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("now")) {
            plugin.getRtpService().start(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "gui", "menu" -> plugin.getMenuService().open(player, MenuPage.RTP);
            case "overworld", "nether", "end" -> plugin.getRtpService().start(player, args[0].toLowerCase(Locale.ROOT));
            default -> {
                player.sendMessage("§bGASMP RTP");
                player.sendMessage("§e/rtp gui §7- choose a world");
                player.sendMessage("§e/rtp now §7- use the configured default world");
                player.sendMessage("§e/rtp <overworld|nether|end> §7- select a world directly");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        return List.of("gui", "now", "overworld", "nether", "end").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .toList();
    }
}
