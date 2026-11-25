package net.gasmp.core.ui

import net.gasmp.core.GASMPCore
import org.bukkit.entity.Player
import ru.voidrp.ui.api.VoidRpUi

/** Optional VoidRP UI adapter; GASMP Core remains usable without this plugin. */
object GASMPCustomUi {
    @JvmStatic
    fun open(plugin: GASMPCore, player: Player): Boolean {
        val service = VoidRpUi.get() ?: return false
        return service.open(player, GASMPHomePage(plugin))
    }
}
