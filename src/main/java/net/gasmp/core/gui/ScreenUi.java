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
            Object icons = engine.getClass().getMethod("icons").invoke(engine);
            String token = ":gasmp_ui:" + screenId + ":";
            Object formatted = invokeFormat(icons, token);
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

    private static Object invokeFormat(Object icons, String text) throws ReflectiveOperationException {
        for (var method : icons.getClass().getMethods()) {
            if (method.getName().equals("format") && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == String.class) {
                return method.invoke(icons, text);
            }
        }
        throw new NoSuchMethodException("RP Engine Icons.format(String) is unavailable");
    }
}
