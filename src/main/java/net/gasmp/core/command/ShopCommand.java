package net.gasmp.core.command;

import net.gasmp.core.GASMPCore;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.List;

public final class ShopCommand implements CommandExecutor {
    private final GASMPCore plugin;
    public ShopCommand(GASMPCore plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("This command is for players."); return true; }
        plugin.getShopService().open(player, 0);
        return true;
    }
}

