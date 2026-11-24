package net.gasmp.core.gui;

import org.bukkit.ChatColor;

/**
 * GASMP inventory-title helper for the ItemsAdder edition.
 * Menus use Bukkit inventories and ItemsAdder font images; no RP Engine
 * screen glyph API or separate screen renderer is required.
 */
public final class ScreenUi {
    private ScreenUi() { }

    public static String title(String screenId, String vanillaTitle) {
        return ChatColor.translateAlternateColorCodes('&', vanillaTitle);
    }
}
