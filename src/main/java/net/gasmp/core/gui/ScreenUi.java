package net.gasmp.core.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

/**
 * Adds an RP Engine custom screen glyph to a vanilla chest inventory title.
 * The screen is a backdrop; the normal Bukkit inventory still owns every slot
 * and click. Reflection keeps RP Engine optional at compile time.
 */
public final class ScreenUi {
    private ScreenUi() { }

    public static String title(String screenId, String vanillaTitle) {
        Plugin engine = Bukkit.getPluginManager().getPlugin("RPEngine");
        if (engine == null || !engine.isEnabled() || screenId == null || screenId.isBlank()) {
            return ChatColor.translateAlternateColorCodes('&', vanillaTitle);
        }
        try {
            java.lang.reflect.Method accessor = engine.getClass().getMethod("icons");
            Object icons = accessor.invoke(engine);
            String token = ":gasmp_ui:" + screenId + ":";
            Object formatted = invokeFormat(accessor.getReturnType(), icons, token);
            if (!(formatted instanceof String glyph) || glyph.equals(token) || glyph.contains(token)) {
                return ChatColor.translateAlternateColorCodes('&', vanillaTitle);
            }
            return ChatColor.WHITE + glyph + ChatColor.RESET
                    + ChatColor.translateAlternateColorCodes('&', vanillaTitle);
        } catch (ReflectiveOperationException | RuntimeException error) {
            engine.getLogger().fine("Could not resolve GASMP UI screen '" + screenId + "': " + error.getMessage());
            return ChatColor.translateAlternateColorCodes('&', vanillaTitle);
        }
    }

    private static Object invokeFormat(Class<?> apiType, Object icons, String text)
            throws ReflectiveOperationException {
        java.lang.reflect.Method method = apiType.getMethod("format", String.class);
        return method.invoke(icons, text);
    }
}
