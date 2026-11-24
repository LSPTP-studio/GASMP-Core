# GASMP Core 0.8.2 — ItemsAdder Edition

**Target:** Paper 26.2  
**Runtime:** Java 25  
**Build:** Gradle 9.x  
**Custom item provider:** ItemsAdder 4.x

## Release status

This is a source release candidate. The repository changes are committed on the ItemsAdder branch, but a JDK 25 Gradle build and a live Paper/ItemsAdder test have not yet been completed in this workspace. Build and verify on a staging server before production.

## Required server plugins

- Paper 26.2 and Java 25
- ItemsAdder 4.x (4.0.13+ recommended for modern item graphics)
- LuckPerms 5.x
- Vault plus a plugin that registers Vault Economy for Coins features

Optional integrations include PlaceholderAPI, TAB, Geyser + Floodgate, ProtocolLib, ImageFrame, Typewriter, OpenInv, Lib's Disguises, HeadDatabase/HeadDB and Multiverse-Core.

**RP Engine is not required or used by GASMP Core 0.8.2.** The old integrations/rp-engine directory is kept only as a legacy reference. Do not install its crate content for this edition.

## Build

Run Gradle with JDK 25:

    gradle clean build

Expected output: build/libs/GASMP-Core-0.8.2.jar

The build targets Paper API 26.2.build.135-stable, LuckPerms API 5.5 and VaultAPI 1.7. SQLite JDBC is bundled into the plugin JAR.

## Install ItemsAdder content

Copy these two folders into plugins/ItemsAdder/contents/:

- integrations/itemsadder/contents/gasmp/
- integrations/itemsadder/contents/gasmp_ranks/

Optionally copy integrations/itemsadder/contents/rank_bundle/ for the additional rank emblem font images.

Then run /iareload, fix any YAML/model warnings shown in the console, run /iazip and refresh the resource pack for clients.

The gasmp namespace contains 96 cosmetic reward IDs, all three matching crate keys, all three placeable crate furniture items, and starter models for hats, wings, ears, tails, plushies and decorations. Each item has its own configured display name. The same IDs are referenced by crates.yml and decoration-shop.yml.

The source snapshot did not contain the original custom model exports for the previously referenced plush, wing and Feral Fantasy packs. The included models are self-contained GASMP starter geometry using vanilla Minecraft block textures, not the original artists' models. Replace the starter JSONs with licensed final art when available, keeping item IDs and paths unchanged.

## Cosmetic crates

| Tier | Display name | Key ID | Placeable crate ID |
|---|---|---|---|
| Normal | STARLIGHT CACHE | gasmp:normal_key | gasmp:normal_crate |
| Medium | ASTRAL CACHE | gasmp:medium_key | gasmp:medium_crate |
| Premium | CELESTIAL VAULT | gasmp:premium_key | gasmp:premium_crate |

All three tiers are cosmetic crates. Their configured reward pools contain cosmetics and small Shards bonuses, not weapons or combat equipment.

The opening GUI already includes a slowing three-slot roulette, staged titles and sounds, a final reveal, particles and key-consumption safety checks. Right-click a placed crate with the matching key to open it. Sneak + left-click a crate block previews the pool. For furniture crates, right-click opens and sneak-punch previews.

The default Shards key-shop prices are Normal 100, Medium 300 and Premium 800. They can be changed in plugins/GASMP-Core/config.yml.

Hats use ItemsAdder's hat behaviour. Furniture and plush items use ItemsAdder furniture behaviour. Wings, tails and backpacks are currently collectible 3D items; GASMP Core does not yet include a back-slot cosmetic equipment system.

## Systems included

Player profiles, Shards ledger, Vault Coins shop, RTP, combat tagging, bounties, auction house and item mailbox, Battle Pass progression, cosmetic crates, Shards Decoration Shop, casino arcade, teams, chat games, moderation, maintenance, holograms, leaderboards, staff vanish, tool skills and rank administration.

## Commands

See docs/COMMANDS.md for the complete player, staff and console command list, including the ItemsAdder crate commands and internal crate dispatcher.

## Main configuration files

GASMP Core copies its default resources into plugins/GASMP-Core/ on first start:

- config.yml — module settings, Shards key-shop prices and game-world commands
- crates.yml — weighted Normal, Medium and Premium cosmetic reward pools
- decoration-shop.yml — Shards furniture/cosmetic catalogue
- shop.yml — Coins shop catalogue
- casino.yml, battlepass.yml, ranks.yml, rtp.yml
- chat-games.yml, maintenance.yml, holograms.yml

Back up the server and database before upgrading. Do not edit the SQLite database manually.

## Staging checklist

1. Build with JDK 25 and Gradle 9.x.
2. Install ItemsAdder 4.x, LuckPerms, Vault and a Vault Economy provider.
3. Copy the ItemsAdder folders, run /iareload and /iazip, then reconnect a Java client.
4. Give and place each crate tier; verify its model, interaction and pickup.
5. Open each tier with the matching key, close the GUI and disconnect mid-animation to confirm keys are not lost before the reveal.
6. Preview each reward pool and verify only configured cosmetics and Shards bonuses appear.
7. Test hat behaviour, placeable furniture, Shards purchases and Vault Coins transactions.
8. Test permissions with separate player, staff, Developer and Owner accounts.
9. Test Geyser/Bedrock separately; Java resource-pack rendering does not guarantee identical Bedrock output.
10. Check console output and SQLite persistence through repeated restarts.
