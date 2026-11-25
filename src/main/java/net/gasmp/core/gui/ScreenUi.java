package net.gasmp.core.gui;

import java.util.Locale;
import org.bukkit.ChatColor;

/**
 * Shared title renderer for GASMP inventories.
 * Glyph aliases resolve to ItemsAdder font images in the gasmp namespace.
 * The client must accept/download the generated ItemsAdder resource pack.
 */
public final class ScreenUi {
    private ScreenUi() {
    }

    public static String title(String screenId, String vanillaTitle) {
        String id = screenId == null ? "" : screenId.toLowerCase(Locale.ROOT);
        String glyph = switch (id) {
            case "crates", "crate", "crate_details" -> "ui_crate";
            case "shard_shop", "shop" -> "ui_shop";
            case "statistics", "stats" -> "ui_stats";
            case "decorations", "decoration_shop" -> "ui_decor";
            case "auction", "auction_house" -> "ui_auction";
            case "rtp", "world_select" -> "ui_rtp";
            case "cosmetics" -> "ui_cosmetics";
            case "shards" -> "ui_shards";
            case "casino" -> "ui_arcade";
            case "profile" -> "ui_profile";
            case "settings" -> "ui_settings";
            case "adventure" -> "ui_rtp";
            default -> "ui_main";
        };
        return ChatColor.WHITE + ":offset_-8::" + glyph + ":";
    }
}