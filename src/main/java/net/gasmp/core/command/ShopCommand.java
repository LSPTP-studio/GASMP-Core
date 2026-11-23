package net.gasmp.core.command;

import net.gasmp.core.GASMPCore;
import net.gasmp.core.gui.MenuPage;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.List;

public final class ShopCommand implements CommandExecutor {
    private final GASMPCore plugin;
    public ShopCommand(GASMPCore plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("This command is for players."); return true; }
        plugin.getMenuService().open(player, MenuPage.KEY_SHOP);
        return true;
    }
}

