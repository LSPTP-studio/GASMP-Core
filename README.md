# GASMP Core 0.8.3 — CraftEngine Edition

All-in-one Paper SMP plugin for GASMP: player profiles, Shards, Vault Coins, shops, teams, Arcade, Battle Pass, moderation, furniture, cosmetics and animated crates.

**Official target:** Paper 26.2 · **Test target:** Paper 26.3 · **Java:** 25 · **Custom content:** CraftEngine 26.9.2+

## Required plugins

- CraftEngine 26.9.2 or newer (Community Edition is sufficient)
- LuckPerms 5.x
- Vault and a plugin that registers a Vault Economy provider for Coins

**Optional UI:** VoidRP UI v0.4.0. When installed, `/gasmp` opens a custom GASMP dashboard. Without it, the native inventory menu remains available. VoidRP UI is a separate MIT-licensed plugin and is not included in the GASMP JAR.

Other optional integrations: PlaceholderAPI, TAB, Geyser + Floodgate, ProtocolLib, PacketEvents, ImageFrame, Typewriter, OpenInv, Lib's Disguises, HeadDatabase/HeadDB and Multiverse-Core.

## Install

1. Stop the server and back up worlds, `plugins/GASMP-Core/`, its SQLite database and the existing resource-pack setup.
2. Install CraftEngine and start once so it creates `plugins/CraftEngine/resources/`.
3. Stop the server and copy the included `gasmp/` directory into `plugins/CraftEngine/resources/`.
4. Put the GASMP Core JAR in `plugins/`.
5. Install Vault, LuckPerms and a Vault Economy provider.
6. Optional: install the separate VoidRP UI v0.4.0 plugin JAR.
7. Start the server and run `/ce reload all`.
8. Ensure players accept the CraftEngine resource pack. When using VoidRP UI, players must also accept its separate UI pack. The two packs stack; do not merge the plugin JARs.

CraftEngine pack location: `plugins/CraftEngine/resources/gasmp/`. Docs: <https://xiao-momi.github.io/craft-engine-wiki/getting_start/project_structure/>.

## Migrated custom content

- 108 item IDs retained under `gasmp:<id>`.
- Six crate tiers and matching keys.
- 40 CraftEngine furniture definitions.
- 43 head-equippable former hat collectibles; verify each on-player render in-game.
- 125 rank, badge and optional bundle PNG images.
- 27 model JSON files migrated into CraftEngine's resourcepack tree.

Existing crate reward and decoration shop IDs have been preserved. Existing player profiles, balances, and other stored data are not intentionally reset by this provider migration.

## Crate tiers

| ID | Crate | Key |
|---|---|---|
| normal | VERDANT CACHE | VERDANT SEAL |
| medium | RUNIC RELIQUARY | RUNIC SIGIL |
| premium | ECLIPSE VAULT | ECLIPSE CREST |
| aether | AETHER SANCTUM | AETHER SIGIL |
| tidal | TIDAL TROVE | TIDAL TOKEN |
| obsidian | OBSIDIAN RELIC | OBSIDIAN SEAL |

Crates are cosmetic-only and award collectibles or Shards bonuses, not combat equipment. See `docs/COMMANDS.md` for crate commands.

## VoidRP UI

With VoidRP UI installed and its UI pack accepted, `/gasmp` opens a custom dashboard with live Shards, Coins and online counts. Its tiles route to the existing GASMP shop, crates, decorations, auction, RTP, warps, arcade, Battle Pass, teams and statistics systems.

If the UI plugin is absent or its pack is not ready, GASMP falls back to its original native inventory menu. VoidRP UI also provides `/vui screen`, `/vui cursor` and `/vui pack`.

Project: <https://github.com/VOIDRP-MINECRAFT/voidrp-ui> · Release: <https://github.com/VOIDRP-MINECRAFT/voidrp-ui/releases>

VoidRP UI documents Paper 26.2 and clients 1.21.6+ as its target. Test it separately on Paper 26.3; the inventory fallback remains available.

## Builds

- `GASMP-Core-0.8.3-paper-26.2.jar` — official Paper 26.2
- `GASMP-Core-0.8.3-paper-26.3.jar` — Paper 26.3 test only

Builds use JDK 25 and Gradle 9.x. CI produces separate JARs and full packages per target.

## Validation

Compilation/package verification is not an in-game test. On a staging server validate CraftEngine content loading, model rendering, furniture placement/mining, hats, item lookup, keys, crate animations, shop purchases, pack acceptance, VoidRP UI cursor/actions and SQLite persistence before production use.
