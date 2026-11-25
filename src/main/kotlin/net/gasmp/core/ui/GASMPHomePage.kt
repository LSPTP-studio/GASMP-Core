package net.gasmp.core.ui

import net.gasmp.core.GASMPCore
import net.gasmp.core.gui.MenuPage
import org.bukkit.Bukkit
import ru.voidrp.ui.layout.Align
import ru.voidrp.ui.layout.Direction
import ru.voidrp.ui.layout.Grid
import ru.voidrp.ui.layout.Icon
import ru.voidrp.ui.layout.Justify
import ru.voidrp.ui.layout.Panel
import ru.voidrp.ui.layout.Size
import ru.voidrp.ui.layout.Text
import ru.voidrp.ui.layout.View
import ru.voidrp.ui.pack.TextFonts
import ru.voidrp.ui.page.Button
import ru.voidrp.ui.page.Page
import ru.voidrp.ui.style.Border
import ru.voidrp.ui.style.Insets
import ru.voidrp.ui.style.Paint
import ru.voidrp.ui.style.Style
import ru.voidrp.ui.style.Theme
import ru.voidrp.ui.widget.eyebrow
import ru.voidrp.ui.widget.screen
import ru.voidrp.ui.widget.statTile

/** GASMP dashboard; its tiles call the existing ItemsAdder-backed GASMP services. */
class GASMPHomePage(private val plugin: GASMPCore) : Page() {
    private var shards = "Loading…"
    private var coins = "Checking…"

    override val bleed get() = bleedOf(Theme.scrim)

    override fun onOpen() {
        val viewer = player
        coins = runCatching {
            val economy = plugin.getEconomyBridge()
            if (economy.isAvailable()) String.format(java.util.Locale.US, "%,.2f", economy.getBalance(viewer))
            else "Not linked"
        }.getOrDefault("Unavailable")

        plugin.getDataStore().getShards(viewer.uniqueId).whenComplete { balance, failure ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (!viewer.isOnline) return@Runnable
                shards = if (failure == null) String.format(java.util.Locale.US, "%,d", balance) else "Unavailable"
                refresh()
            })
        }
    }

    override fun view(): View {
        val header = Panel(
            style = Style(
                background = Paint(0x101827),
                border = Border(1, Paint(Theme.VIOLET, 0.34)),
                radius = Theme.R_XL,
                padding = Insets.all(Theme.SPACE_5),
                glow = Paint(Theme.VIOLET, 0.16),
            ),
            width = Size.Fill,
            direction = Direction.ROW,
            gap = Theme.SPACE_4,
            align = Align.CENTER,
            children = listOf(
                Panel(
                    width = Size.Fill,
                    gap = Theme.SPACE_2,
                    children = listOf(
                        eyebrow("GASMP  /  SURVIVAL NETWORK", Theme.VIOLET_SOFT),
                        Text("Welcome, ${player.name}", Theme.TEXT_H1, Theme.INK, TextFonts.Weight.BOLD, wrap = false),
                        Text("Your server. Your story. Your next adventure.", Theme.TEXT_BODY, Theme.INK_SOFT),
                    ),
                ),
                Panel(
                    style = Style(
                        background = Paint(Theme.VIOLET, 0.14),
                        border = Border(1, Paint(Theme.VIOLET, 0.34)),
                        radius = Theme.R_LG,
                        padding = Insets.all(Theme.SPACE_3),
                    ),
                    width = Size.Fixed(104),
                    height = Size.Fixed(104),
                    justify = Justify.CENTER,
                    align = Align.CENTER,
                    children = listOf(Icon("flame", 42, Theme.VIOLET_SOFT), eyebrow("GASMP", Theme.INK)),
                ),
            ),
        )

        val balances = Grid(
            columns = 3,
            gap = Theme.SPACE_3,
            width = Size.Fill,
            children = listOf(
                statTile("Shards", shards, "gem", accent = Theme.VIOLET_SOFT),
                statTile("Coins", coins, "wallet", accent = Theme.GOLD),
                statTile("Online", Bukkit.getOnlinePlayers().size.toString(), "users", accent = Theme.GREEN),
            ),
        )

        val tiles = listOf(
            Tile("shop", "Coins Shop", "Buy server supplies", "shopping-cart", Theme.GOLD),
            Tile("vaults", "Crate Vaults", "Six cosmetic crate tiers", "gift", Theme.VIOLET_SOFT),
            Tile("decorations", "Decorations", "Furniture and collectibles", "grid", Theme.VIOLET_SOFT),
            Tile("auction", "Auction House", "Browse player listings", "gavel", Theme.GOLD),
            Tile("rtp", "Random Teleport", "Choose your destination", "compass", Theme.VIOLET_SOFT),
            Tile("warps", "Warps", "Spawn, crates and minigames", "map", Theme.GREEN),
            Tile("casino", "Arcade", "Play Shards mini-games", "gamepad-2", Theme.VIOLET_SOFT),
            Tile("battlepass", "Battle Pass", "Progress and claim rewards", "trophy", Theme.GOLD),
            Tile("teams", "Teams", "Create or manage your team", "users", Theme.GREEN),
            Tile("stats", "Statistics", "View your server records", "activity", Theme.VIOLET_SOFT),
            Tile("profile", "Profile", "Your GASMP player overview", "user", Theme.VIOLET_SOFT),
            Tile("settings", "Settings", "Personal menu preferences", "settings", Theme.INK_SOFT),
        )

        val grid = Grid(
            columns = viewport.by(compact = 2, regular = 3),
            gap = Theme.SPACE_3,
            rowGap = Theme.SPACE_3,
            width = Size.Fill,
            children = tiles.map { tile ->
                val id = "tile:${tile.id}"
                Panel(
                    style = if (hovered == id) {
                        Style(
                            background = Paint(tile.accent, 0.14),
                            border = Border(1, Paint(tile.accent, 0.48)),
                            radius = Theme.R_LG,
                            padding = Insets.all(Theme.SPACE_4),
                            glow = Paint(tile.accent, 0.12),
                        )
                    } else {
                        Style(
                            background = Paint(0x111B2A),
                            border = Border(1, Paint(Theme.LINE, 0.18)),
                            radius = Theme.R_LG,
                            padding = Insets.all(Theme.SPACE_4),
                        )
                    },
                    width = Size.Fill,
                    height = Size.Fixed(108),
                    gap = Theme.SPACE_2,
                    id = id,
                    children = listOf(
                        Panel(
                            direction = Direction.ROW,
                            width = Size.Fill,
                            align = Align.CENTER,
                            gap = Theme.SPACE_2,
                            children = listOf(
                                Icon(tile.icon, 20, tile.accent),
                                Text(tile.title, Theme.TEXT_LEAD, Theme.INK, TextFonts.Weight.SEMIBOLD, wrap = false),
                            ),
                        ),
                        Text(tile.description, Theme.TEXT_CAPTION, Theme.INK_SOFT),
                    ),
                )
            },
        )

        val body = Panel(
            width = Size.Fill,
            maxWidth = 1040,
            gap = Theme.SPACE_4,
            children = listOf(
                header,
                balances,
                Panel(
                    style = Style(
                        background = Paint(0x0B1220),
                        border = Border(1, Paint(Theme.LINE, 0.16)),
                        radius = Theme.R_XL,
                        padding = Insets.all(Theme.SPACE_4),
                    ),
                    width = Size.Fill,
                    gap = Theme.SPACE_3,
                    children = listOf(
                        Panel(
                            direction = Direction.ROW,
                            width = Size.Fill,
                            align = Align.CENTER,
                            children = listOf(
                                eyebrow("QUICK ACCESS", Theme.VIOLET_SOFT),
                                Panel(width = Size.Fill),
                                eyebrow("SELECT A SECTION", Theme.INK_DIM),
                            ),
                        ),
                        grid,
                    ),
                ),
                Panel(
                    direction = Direction.ROW,
                    width = Size.Fill,
                    align = Align.CENTER,
                    children = listOf(
                        Text("GASMP CORE  •  ITEMSADDER 4.0.18 EDITION", Theme.TEXT_MICRO, Theme.INK_DIM),
                        Panel(width = Size.Fill),
                        Text("Click a tile to open its system", Theme.TEXT_MICRO, Theme.INK_DIM),
                    ),
                ),
            ),
        )

        return screen(
            style = Theme.scrim,
            justify = Justify.CENTER,
            align = Align.CENTER,
            children = listOf(body),
        )
    }

    override fun onClick(id: String, button: Button) {
        if (!id.startsWith("tile:")) return
        val target = player
        val action = id.removePrefix("tile:")
        close()
        when (action) {
            "shop" -> target.performCommand("shop coins")
            "vaults" -> target.performCommand("shop shards")
            "decorations" -> plugin.getDecorationShopService().openCategories(target)
            "auction" -> target.performCommand("ah")
            "rtp" -> target.performCommand("rtp")
            "warps" -> plugin.getMenuService().open(target, MenuPage.WARPS)
            "casino" -> target.performCommand("casino menu")
            "battlepass" -> target.performCommand("battlepass")
            "teams" -> target.performCommand("team info")
            "stats" -> plugin.getMenuService().open(target, MenuPage.STATISTICS)
            "profile" -> plugin.getMenuService().open(target, MenuPage.PROFILE)
            "settings" -> plugin.getMenuService().open(target, MenuPage.SETTINGS)
        }
    }

    private data class Tile(
        val id: String,
        val title: String,
        val description: String,
        val icon: String,
        val accent: Int,
    )
}
