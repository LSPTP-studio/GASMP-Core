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
        if (args.length > 0 && args[0].equalsIgnoreCase("coins")) {
            plugin.getShopService().open(player, 0);
        } else if (args.length > 0 && (args[0].equalsIgnoreCase("shards") || args[0].equalsIgnoreCase("keys"))) {
            plugin.getMenuService().open(player, MenuPage.KEY_SHOP);
        } else {
            plugin.getMenuService().open(player, MenuPage.SHOP);
        }
        return true;
    }
}

