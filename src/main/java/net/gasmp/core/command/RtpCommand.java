package net.gasmp.core.command;
import net.gasmp.core.GASMPCore;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
public final class RtpCommand implements CommandExecutor {
    private final GASMPCore plugin;
    public RtpCommand(GASMPCore plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("RTP is a player command."); return true; }
        plugin.getRtpService().start(player); return true;
    }
}

