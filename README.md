# GASMP Core 0.8.0 — menu, currency and furniture milestone

**Target:** Paper 26.2 (Purpur compatibility only where the Paper API behavior is shared)  
**Runtime:** Java 25  
**Build:** Gradle Kotlin DSL  
**Package:** `net.gasmp`

## Important release status

This is a feature milestone built on the 0.7.0 source. The 0.8.0 branch has passed the Java 25 / Gradle 9.9.2 GitHub Actions build and YAML/Blockbench asset validation. Live Paper 26.2, economy-provider, RP Engine and Geyser staging tests are still required before production use.

## Dependencies

### Required
- Paper 26.2 (or a compatible Purpur build)
- Java 25
- LuckPerms 5.x — rank and staff group API
- Vault — economy service API
- A Coins plugin that registers the Vault `Economy` service (for example EssentialsX Economy)
- Gradle 9.x for building from source

### Optional integrations
- TAB — reads LuckPerms primary group and `%luckperms-prefix%`
- PlaceholderAPI — placeholders in compatible displays
- Geyser + Floodgate — Bedrock cross-play; RP Engine can build/deliver a Bedrock pack when Geyser-Spigot is running on the same server
- RP Engine — recommended free/open-source resource-pack, custom-item, custom-block and animated placed-model option; ItemsAdder/Nexo remain optional alternatives
- OpenInv, Lib's Disguises, HeadDatabase/HeadDB, Skript, ImageFrame and Typewriter — restricted third-party staff/build tools, not bundled into GASMP Core

**Economy compatibility note:** Coins features support any plugin that exposes a Vault `Economy` provider. A proprietary economy API without Vault support needs a separate adapter and is not automatically compatible.

## Build

```powershell
gradle build
```

Output: `build/libs/GASMP-Core-0.8.0.jar`.

The Gradle build targets Paper API `26.2.build.135-stable`, LuckPerms API 5.5 and VaultAPI 1.7. SQLite JDBC is included as a runtime dependency in the plugin JAR. Do not use JDK 21 for this target; compile and run with Java 25.

## Phase 1 feature status

| System | Current source implementation | Limitations / release notes |
|---|---|---|
| Player profiles | SQLite UUID profiles, first/last seen, kills/deaths/play ticks | Static checks only; live migration test required |
| Shards | Persistent integer balance, give/take/set, transaction ledger, separate paginated Shard Shop with confirmation and durable item-mail fallback | Separate from Coins; no transfers, cash-out or real-money top-ups; custom furniture items require RP Engine |
| Coins shop | Expanded Vault-backed categories, pages, purchase confirmation, transaction log and rollback/reconciliation | Verify provider behavior on the actual economy plugin |
| RTP | Safe-location search, warmup, cooldown, combat restriction, movement cancellation | Depends on configured world being loaded and suitable terrain |
| Combat | Combat tags, logout record/punishment, quit/join handling | Tune tag duration and punishment policy before launch |
| Bounties | Vault-funded placements, kill claims, top list and refund/reconciliation paths | Economy provider failure can require staff reconciliation |
| Auction house | Text-command list/sell/buy/cancel/mine, expiry, persistent item mailbox, payment reconciliation and Owner review queue | Not yet a polished searchable GUI; inventory-to-mailbox delivery still needs live crash stress tests |
| Battle Pass | Season XP, ore/mob XP events, level rewards and claim records | Basic progression/reward implementation, not full mission/season GUI |
| Crates | Configurable weighted rewards, physical key checks, location persistence, protected custom blocks, RP Engine keys and animated placed Vote/Legendary crate models | RP Engine content must be installed/reloaded; custom block variant is static, use the placed model for the lid animation; reward preview UI still needs work |
| Teams | Leader/Co-Leader/Officer/Member roles, team bank, capacity upgrades, descriptions, named homes/list, alliances, invite expiry, team chat and membership management | Requires a Vault provider for Coins bank/upgrades; team missions, contribution tracking, friendly-fire/war and custom GUI still need work |
| Casino | Main-menu button now opens the real casino GUI; bet selection, animated spin panels, server-side result and transaction audit | Enabled by default on fresh installs; uses virtual Coins only. Blackjack, Poker and Tic-Tac-Toe remain simplified quick-match simulations, not full interactive games |
| Chat games | Scheduled/manual chat rounds and configured rewards | Text-based formats, not a large custom GUI game suite |
| Moderation | Ban/unban, mute/unmute, warn, kick and history | Verify moderation policy, online/offline handling and persistence on live server |
| Maintenance | Persistent enable/disable, login gate and MOTD change | Test bypass groups and server-list display with actual clients |
| Holograms | Persistent text-display entities and basic create/set/delete/list | Not a full editor, animated hologram suite or placeholder leaderboard system |
| Tool skills | Vein Miner and Tree Capitator with block limits and permissions | Test against protection plugins and custom block providers |
| Staff vanish | LuckPerms group-gated vanish and join/quit visibility behavior | Verify with TAB, Geyser and other vanish plugins to avoid duplicate visibility control |
| Leaderboards / performance | Basic top-stat queries and server snapshot command | Snapshot is diagnostic, not an automatic optimizer |

## Default release safety

- Casino uses virtual in-server Coins only, with no real-money deposits or cash-out. Fresh installs enable it; for an existing 0.7.0 config, set `modules.casino: true` and `casino.yml` `settings.enabled: true` after reviewing limits and odds.
- The named config-admin allowlist is in `config.yml`: `carltzy3k` is the Owner and `thommacide` is the Developer. It allows Core config reload and rank administration with Owner/Developer restrictions; it does not grant unrelated LuckPerms permissions.
- Shards cannot go negative. Player-to-player transfers, real-money purchases and cash-outs are disabled.
- `/crates` management is Owner/Developer-only; players open configured crate blocks with physical keys.
- OpenInv is restricted to Builder/Developer/Owner policy as configured for your server, but the default permission mapping is read-only and does not grant inventory-edit nodes.
- Lib's Disguises and Skript management are Developer/Owner-only; HeadDatabase is Builder/Developer/Owner; vanish is staff-group-only.
- Never grant the Developer group access to Owner/Admin/Manager/Dev rank assignment. The rank GUI implementation is a Phase 2 area for further review and polish.
- Back up the entire server and database before testing this candidate.

## Config files

On first start, Core copies the default resources into `plugins/GASMP-Core/`:
- `config.yml` — master settings and module toggles
- `shop.yml` — Coins product list/prices/category settings
- `shard-shop.yml` — separate Shards catalogue, categories and prices
- `rtp.yml` — safe teleport world, radius, attempts and cooldown
- `casino.yml` — command-game enable flags, odds limits and bet range
- `battlepass.yml` — season ID, levels and reward definitions
- `crates.yml` — crate rewards/weights and placed crate locations
- `chat-games.yml` — chat-game timing and reward settings
- `maintenance.yml` — maintenance status, kick message and MOTD
- `holograms.yml` — persistent display definitions
- `ranks.yml` — LuckPerms group/rank definitions (Phase 2 polish)

Read `docs/` before changing live data. Avoid manually editing the SQLite database.

## Commands

The command list and aliases are declared in `src/main/resources/plugin.yml`. The key user commands include `/gasmp`, `/shards`, `/shardshop`, `/shop`, `/rtp`, `/bounty`, `/ah`, `/team`, `/battlepass`, `/casino`, and `/gvanish`. Staff/admin commands include `/gasmpcore`, `/gasmpadmin`, `/crates`, `/gmaintenance`, `/gban`, `/gunban`, `/gmute`, `/gunmute`, `/gwarn`, `/gkick`, `/ghistory`, `/gholo`, `/gchatgame`, `/gtop` and `/gperf`.

## Rank assets for Phase 2

The three rank-art bundles supplied by the server owner are preserved in `assets/phase2-rank-sources/`. The existing optional ItemsAdder export under `integrations/itemsadder/contents/` contains the 72 badge images, English PIXED prefix images and Chiaki bundle content. Phase 2 will turn the rank artwork into the custom rank/menu experience. Do not install the asset bundle into a live pack until namespaced assets and all client formats are reviewed.

## Phase 2 scope

- Final rank ladder/role tracks and rank prefix design using the supplied icons
- Owner-only and Developer-only admin dashboards
- Fully customized Owner/Developer dashboards and refreshed player/shop/team/auction/crate/casino/staff menus
- Final rank ladder, rank art wiring and cinematic rank-up with sound, particles, emblems and optional model/texture effects
- Final Java/Bedrock UI artwork, Geyser validation and cross-edition fallbacks

## Required launch testing

1. Build with JDK 25 and Gradle 9.x.
2. Start a clean Paper 26.2 test server with LuckPerms, Vault and an economy provider.
3. Confirm SQLite schema initialization and restart persistence.
4. Test Shards, shop transactions, bounties and auction mailbox with normal players.
5. Test team invites, crate keys/rewards, Battle Pass claims and chat games.
6. Test maintenance and staff permissions with separate Player, Builder, Mod, Admin, Developer and Owner accounts.
7. Test with Java and Bedrock clients, and confirm TAB prefixes from LuckPerms.
8. Test with protection plugins installed; confirm vein miner/tree capitator do not bypass protection.
9. Keep the casino on a staging server until every game, wager limit, payout and provider-failure/refund path has been tested. Only then enable it on the live server.
10. Review console logs and database after repeated join/quit, plugin reload and server restart cycles.


## 0.7.0 remaining-system additions
- Expanded Teams service and full command guide, including named homes and `/team homes`.
- Transactionally limited team homes and symmetric alliance creation.
- Original 64×64 Vote and Legendary pixel-art crate atlases, Blockbench 5 models, separate animated lid bones, key icons and display icons.
- RP Engine content definitions for custom static crate blocks, animated placed crate displays, physical keys and guarded console interaction.
- Casino GUI menu layout fix and game-specific visual spin panels for the configured game set.
- Casino payout calibration updates and honest descriptions for simplified quick-match simulations.
- Staging-server launch plan, dependency notes and GitHub Actions Java 25 build workflow.

The GitHub Actions workflow is included at `.github/workflows/build.yml`. It parses the shipped YAML resources, validates the six furniture Blockbench models and their embedded PNG signatures, builds with Java 25 / Gradle 9.9.2, and uploads separate plugin-JAR and RP Engine furniture-pack artifacts. Live Paper 26.2 / Geyser / RP Engine testing remains necessary.


## 0.8.0 feature milestone

- Rewired the main-menu Casino tile to the real casino GUI and enabled the virtual-Coins arcade defaults for fresh installs.
- Added a separate Shard Shop with category filters, pagination, purchase confirmation, atomic Shards debit and a durable mail fallback if a buyer disconnects during a purchase.
- Expanded the Coins catalogue with Blocks, Materials, Tools, Combat, Food and Utility entries; the existing GUI keeps 21 items per page.
- Added a cleaner main menu with direct links to Auction House, RTP, Teams, Battle Pass, Shards, Profile and Casino.
- Added six original RP Engine furniture models: Oak Chair, Velvet Sofa, Dining Table, Prismatic Crystal Lamp, Arcade Cabinet and Champion Trophy. The chair and sofa have seats; the lamp emits light level 14; the models include authored click animations.
- Added named config-admin allowlist defaults: `carltzy3k` (Owner) and `thommacide` (Developer). These names authorize GASMP config reload and the rank-admin panel with Owner/Developer limits; they do not grant unrelated LuckPerms permissions.

### Remaining limitations

- The new 0.8.0 branch still needs CI compilation, RP Engine pack reload, and live staging checks on Paper 26.2.
- The six furniture models are Java/RP Engine assets; Geyser/Bedrock rendering and sit placement need real client tests.
- The Shard Shop hides its custom furniture entries when RP Engine is absent; the regular vanilla décor stock remains separate.
- Casino Blackjack, Poker and Tic-Tac-Toe are quick-match simulations. Crash is a fixed-target round rather than a live cash-out game. Full rule-driven versions remain future work.
- Music playback and personal Settings controls are still placeholders. The decorations/furniture area is connected to the Shard Shop catalogue but is not yet a full furniture placement editor.
- Custom font/glyph GUI artwork and the final dual-edition resource packs remain future work.
