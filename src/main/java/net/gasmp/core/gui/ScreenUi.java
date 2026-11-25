package net.gasmp.core.gui;

import org.bukkit.ChatColor;

/** Shared title renderer. GASMP uses native inventory titles and item icons. */
public final class ScreenUi {
    private ScreenUi() { }

    /**
     * Keep the supplied title readable in the vanilla inventory container.
     * Large font-image overlays are deliberately not used: they cover slots,
     * vary with client resource-pack scaling, and are not Bedrock friendly.
     */
    public static String title(String screenId, String vanillaTitle) {
        return ChatColor.translateAlternateColorCodes('&', vanillaTitle == null ? "GASMP" : vanillaTitle);
    }
}
