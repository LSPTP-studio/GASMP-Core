# GASMP Core 0.8.3 — ItemsAdder 4.0.18 Edition

All-in-one GASMP SMP plugin: Shards, Vault Coins, shops, teams, Arcade, Battle Pass, moderation, cosmetics, furniture and six animated cosmetic crates.

**Documented runtime target:** Paper 26.2 + ItemsAdder 4.0.18 · **Paper 26.3:** compile/test artifact only · **Java:** 25.

## Required plugins

- ItemsAdder **4.0.18**
- Vault and an economy plugin that registers a Vault Economy provider
- LuckPerms 5.x

**Optional dashboard:** VoidRP UI 0.4.0 (separate plugin JAR). When installed and its UI pack is accepted, `/gasmp` opens the custom GASMP dashboard. Without it, the existing native inventory menu remains available.

Other optional integrations: PlaceholderAPI, ProtocolLib, TAB, Geyser + Floodgate, ImageFrame, Typewriter, OpenInv, Lib's Disguises, HeadDatabase/HeadDB and Multiverse-Core.

## Install

1. Use **Paper 26.2** for the documented ItemsAdder 4.0.18 target. Back up your worlds and `plugins/GASMP-Core/`, including its SQLite database.
2. Stop the server.
3. Put the original ItemsAdder 4.0.18 JAR directly into `plugins/`. Install ProtocolLib too if your setup requires it.
4. Start once and let ItemsAdder create `plugins/ItemsAdder/`. Stop the server after it finishes.
5. Copy the included `gasmp/` and `gasmp_ranks/` folders into `plugins/ItemsAdder/contents/`.
6. Put the GASMP Core JAR directly into `plugins/`. Install Vault, a Vault Economy provider and LuckPerms.
7. Optional: put the separate VoidRP UI 0.4.0 JAR directly into `plugins/`.
8. Start the server, run `/iareload`, check console warnings, then run `/iazip`.
9. Ensure ItemsAdder's pack is hosted and delivered to players. With VoidRP UI, players must also accept its separate UI pack.

**Important:** `ItemsAdder.jar` belongs directly in `plugins/`, not inside `plugins/ItemsAdder/`. The `contents/` folder is for the GASMP content folders.

## Bundled GASMP content

- Six named cosmetic crate tiers with matching keys, placeable crate furniture and tier-themed opening animations.
- 108 custom item definitions under the `gasmp:` namespace, including crate models, keys, collectibles and furniture.
- Cosmetic hats, wings, backpacks, ears, tails and furniture assets.
- Rank icons, 72 badge images and optional rank-bundle artwork.
- Existing Shards, profiles, crate rewards, shop IDs and database data are retained. Do not delete the GASMP data folder when upgrading.

All six crates award cosmetics and Shards bonuses, not combat equipment. Keys are consumed only after the final reward reveal.

## VoidRP UI integration

With VoidRP UI 0.4.0 installed and its pack accepted, `/gasmp` opens a custom GASMP dashboard with live Shards, Coins and online counts. Its quick-access tiles open the existing GASMP shop, Crate Vaults, Decorations, Auction House, RTP, Warps, Arcade, Battle Pass, Teams, Statistics, Profile and Settings.

The custom dashboard is a presentation layer; the economy, crate rewards and player data remain in GASMP Core. If VoidRP UI is absent or its pack is not accepted, GASMP falls back to its existing inventory menu.

VoidRP UI is separate and is not shaded into the GASMP JAR:
- Project: <https://github.com/VOIDRP-MINECRAFT/voidrp-ui>
- Releases: <https://github.com/VOIDRP-MINECRAFT/voidrp-ui/releases>

Its documented target is Paper 26.2 and clients 1.21.6+. Test the custom interface on a staging server before production.

## ItemsAdder compatibility

ItemsAdder's published compatibility listing for 4.0.18 includes Paper 26.2, but does not list Paper 26.3. The Paper 26.3 artifact in this repository is only for compilation and testing; it is **not runtime-certified** with ItemsAdder 4.0.18.

The earlier Adventure `NoSuchMethodError` came from the ItemsAdder plugin itself. A GASMP Core update cannot repair an incompatible, modified or repackaged ItemsAdder JAR. Use the original, unmodified ItemsAdder 4.0.18 download.

## Build outputs

- `GASMP-Core-0.8.3-paper-26.2.jar` — intended Paper 26.2 + ItemsAdder 4.0.18 build.
- `GASMP-Core-0.8.3-paper-26.3.jar` — compile/test only; do not treat it as runtime support.

JDK 25 and Gradle 9.x are used. CI packages each JAR with the matching ItemsAdder content folders. A successful CI build is not a substitute for an in-game Paper 26.2 + ItemsAdder 4.0.18 staging test.
