package net.gasmp.core.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Adds RP Engine's custom screen glyph to an inventory title.
 *
 * The actual screen art is supplied by the gasmp_ui content pack. Without RP
 * Engine (or before that pack has loaded), every menu falls back to its normal
 * readable title and remains fully usable.
 */
public final class RpEngineUi {
    private RpEngineUi() { }

    public static String title(String screenId, String fallbackTitle) {
        String fallback = ChatColor.translateAlternateColorCodes('&', fallbackTitle);
        Plugin engine = Bukkit.getPluginManager().getPlugin("RPEngine");
        if (engine == null || !engine.isEnabled() || screenId == null || screenId.isBlank()) {
            return fallback;
        }

        try {
            ClassLoader loader = engine.getClass().getClassLoader();
            Class<?> contentIdClass = Class.forName("ai.resourcepack.engine.api.ContentId", true, loader);
            Object parsed = contentIdClass.getMethod("parse", String.class)
                    .invoke(null, "gasmp_ui:" + screenId);
            if (!(parsed instanceof Optional<?> optional) || optional.isEmpty()) return fallback;

            Object icons = engine.getClass().getMethod("icons").invoke(engine);
            Class<?> iconsApi = Class.forName("ai.resourcepack.engine.api.Icons", true, loader);
            Method character = iconsApi.getMethod("character", contentIdClass);
            Object result = character.invoke(icons, optional.get());
            if (result instanceof Optional<?> icon && icon.isPresent()) {
                return ChatColor.WHITE + icon.get().toString() + ChatColor.RESET + fallback;
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            // A missing RP Engine content pack must never prevent a player opening a menu.
        }
        return fallback;
    }
}
