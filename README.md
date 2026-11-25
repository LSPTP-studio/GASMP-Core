# GASMP Core 0.8.3 — ItemsAdder Edition

**GASMP Core** is an all-in-one Paper SMP plugin for the GASMP server, with Shards, Coins, shops, teams, casino arcade, Battle Pass, moderation, furniture, cosmetic collections and animated crate systems.

**Target:** Paper 26.2 · **Runtime:** Java 25 · **Build:** Gradle 9.x · **Custom item and font provider:** ItemsAdder 4.x

## Downloads

- **[Download EVERYTHING — Full Package ZIP](https://github.com/LSPTP-studio/GASMP-Core/releases/download/v0.8.3/GASMP-Core-0.8.3-Full-Package.zip)** — source code, plugin JAR, ItemsAdder models, UI textures and installation guide.
- **[Plugin JAR only](https://github.com/LSPTP-studio/GASMP-Core/releases/download/v0.8.3/GASMP-Core-0.8.3.jar)**
- **[All releases](https://github.com/LSPTP-studio/GASMP-Core/releases)**
- **[0.8.3 source branch](https://github.com/LSPTP-studio/GASMP-Core/tree/itemsadder-edition-0.8.3)**

## What's new in 0.8.3

- Fixed inventory title rendering so menus use ItemsAdder font-image panels rather than ignoring the screen ID.
- Added custom panel artwork for the main menu, shop screens and crate screens.
- Added three new GASMP crate themes: AETHER SANCTUM, TIDAL TROVE and OBSIDIAN RELIC.
- Added matching AETHER SIGIL, TIDAL TOKEN and OBSIDIAN SEAL keys, furniture items and unique model textures.
- Added crate-specific roulette color palettes and tier particles during the opening animation.
- Added /crates preview, /crates info and /crates givebundle.
- Added migration logic to add the new crate tiers and Shards key prices to existing servers without replacing saved reward pools or placed locations.
- Expanded crate list output, help messages, tab completion and command documentation.

## Required server plugins

- Paper 26.2 and Java 25
- ItemsAdder 4.x (4.0.13+ recommended)
- LuckPerms 5.x
- Vault plus a plugin that registers Vault Economy for Coins features

Optional integrations include PlaceholderAPI, TAB, Geyser + Floodgate, ProtocolLib, ImageFrame, Typewriter, OpenInv, Lib's Disguises, HeadDatabase/HeadDB and Multiverse-Core.

**RP Engine is not required or used by this edition.** The old integrations/rp-engine directory is retained only as a legacy reference. Do not install its pack for this ItemsAdder edition.

## Install

1. Stop the server and make a full backup, including plugins/GASMP-Core/ and its SQLite database.
2. Put GASMP-Core-0.8.3.jar in the server's plugins/ folder.
3. Copy the included plugins/ItemsAdder/contents/gasmp/ and gasmp_ranks/ folders into your server's plugins/ItemsAdder/contents/ folder. The optional rank_bundle/ folder adds extra rank emblems.
4. Start or restart the server.
5. Run /iareload and fix any YAML, model or font-image warnings in the console.
6. Run /iazip to rebuild the resource pack.
7. Ensure the ItemsAdder pack is hosted and delivered to players, then reconnect and ACCEPT the updated pack. A stale cached pack or declined pack will show missing glyphs and models.
8. Test the menus and all six crate/key pairs before production use.

The UI background files live in gasmp/textures/font/ and their glyph definitions live in gasmp/configs/ui.yml. The Java menu title helper references the aliases defined there. See the ItemsAdder Font Images guide: https://wiki.itemsadder.com/adding-content/font-images/font-images/.

## Six cosmetic crates

| ID | Crate display name | Matching key | Default Shards price |
|---|---|---|---:|
| normal | VERDANT CACHE | VERDANT SEAL | 100 |
| medium | RUNIC RELIQUARY | RUNIC SIGIL | 300 |
| premium | ECLIPSE VAULT | ECLIPSE CREST | 800 |
| aether | AETHER SANCTUM | AETHER SIGIL | 1,200 |
| tidal | TIDAL TROVE | TIDAL TOKEN | 1,800 |
| obsidian | OBSIDIAN RELIC | OBSIDIAN SEAL | 2,500 |

All six are cosmetic crates. Their configured pools award ItemsAdder cosmetics and small Shards bonuses, not weapons or combat equipment. Each crate has its own matching key, placeable furniture model, display name, color and opening-animation palette.

The animation uses a decelerating three-slot roulette, themed border materials, portal/tier particles, staged titles and sounds, a final reward reveal and burst particles. Keys are consumed only after the final reveal; closing or disconnecting before that point cancels the opening without consuming the key.

## Crate commands

```text
/crates list
/crates info <crate-id>
/crates preview <crate-id>
/crates givekey <player> <crate-id> [amount]
/crates giveblock <player> <crate-id> [amount]
/crates givebundle <player> <crate-id> [amount]
/crates givemodel <player> <crate-id> [amount]
/crates set <crate-id>
/crates unset <crate-id>
/crates reload
/keyall <crate-id|all> <amount> [world:<world>] [silent]
```

Crate administration is restricted to Owner/Developer staff by the service checks. The console can run crate distribution and administration commands. Players can sneak + left-click or sneak-punch a placed crate to open its reward preview.

See docs/COMMANDS.md for the full player, staff, console and crate command reference.

## Other systems

Player profiles, Shards ledger, Vault Coins shop, RTP, combat tagging, bounties, auction house and item mailbox, Battle Pass progression, Shards Decoration Shop, casino arcade, teams, chat games, moderation, maintenance, holograms, leaderboards, staff vanish, tool skills and rank administration.

## Build from source

Use JDK 25 and Gradle 9.x:

```bash
gradle clean build
```

Output: build/libs/GASMP-Core-0.8.3.jar. SQLite JDBC is bundled into the plugin JAR.

## Asset notes

The new UI panels and themed crate models are GASMP starter assets using vanilla Minecraft textures. The source snapshot did not contain the original custom model exports for the previously referenced plush, wing and Feral Fantasy packs. Replace those starter models only with licensed final art, keeping the item IDs and model paths unchanged.

## Release validation

The release pipeline verifies the Java build, checks the JAR, assembles the source + server-install ZIP and uploads both assets to GitHub Releases. A successful build does not replace a live Paper/ItemsAdder staging test. Validate resource-pack delivery, model rendering, menu alignment, crate interactions, key consumption, economy transactions and database persistence on a staging server.