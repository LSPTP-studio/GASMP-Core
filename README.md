# GASMP Core 0.7.0 — Day 1 stabilization candidate

**Target:** Paper 26.2 (Purpur compatibility only where the Paper API behavior is shared)  
**Runtime:** Java 25  
**Build:** Gradle Kotlin DSL  
**Package:** `net.gasmp`

## Important release status

This is a **source release candidate**, not a verified production JAR. The workspace used to prepare it has Java 21 and does not have Gradle or cached Paper/Vault/LuckPerms dependencies. Static/source and database checks are possible here, but Paper 26.2 compilation and live server integration tests remain outstanding. Build it with JDK 25 and Gradle on a network-enabled development machine before installing it on GASMP.

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

Output: `build/libs/GASMP-Core-0.7.0.jar`.

The Gradle build targets Paper API `26.2.build.135-stable`, LuckPerms API 5.5 and VaultAPI 1.7. SQLite JDBC is included as a runtime dependency in the plugin JAR. Do not use JDK 21 for this target; compile and run with Java 25.

## Phase 1 feature status

| System | Current source implementation | Limitations / release notes |
|---|---|---|
| Player profiles | SQLite UUID profiles, first/last seen, kills/deaths/play ticks | Static checks only; live migration test required |
| Shards | Persistent integer balance, give/take/set, transaction ledger | Separate from Coins; no transfers, cash-out or real-money top-ups |
| Coins shop | Vault-backed categories, pages, confirm, purchase log and rollback/reconciliation | Verify provider behavior on the actual economy plugin |
| RTP | Safe-location search, warmup, cooldown, combat restriction, movement cancellation | Depends on configured world being loaded and suitable terrain |
| Combat | Combat tags, logout record/punishment, quit/join handling | Tune tag duration and punishment policy before launch |
| Bounties | Vault-funded placements, kill claims, top list and refund/reconciliation paths | Economy provider failure can require staff reconciliation |
| Auction house | Text-command list/sell/buy/cancel/mine, expiry, persistent item mailbox, payment reconciliation and Owner review queue | Not yet a polished searchable GUI; inventory-to-mailbox delivery still needs live crash stress tests |
| Battle Pass | Season XP, ore/mob XP events, level rewards and claim records | Basic progression/reward implementation, not full mission/season GUI |
| Crates | Configurable weighted rewards, physical key checks, location persistence, protected custom blocks, RP Engine keys and animated placed Vote/Legendary crate models | RP Engine content must be installed/reloaded; custom block variant is static, use the placed model for the lid animation; reward preview UI still needs work |
| Teams | Leader/Co-Leader/Officer/Member roles, team bank, capacity upgrades, descriptions, named homes/list, alliances, invite expiry, team chat and membership management | Requires a Vault provider for Coins bank/upgrades; team missions, contribution tracking, friendly-fire/war and custom GUI still need work |
| Casino | Casino selector GUI, wager selection, game-themed animated spin panels, server-side results, transaction audit, odds/help | Disabled by default; Blackjack, Poker and Tic-Tac-Toe are simplified quick-match simulations, not full interactive game rules/boards |
| Chat games | Scheduled/manual chat rounds and configured rewards | Text-based formats, not a large custom GUI game suite |
| Moderation | Ban/unban, mute/unmute, warn, kick and history | Verify moderation policy, online/offline handling and persistence on live server |
| Maintenance | Persistent enable/disable, login gate and MOTD change | Test bypass groups and server-list display with actual clients |
| Holograms | Persistent text-display entities and basic create/set/delete/list | Not a full editor, animated hologram suite or placeholder leaderboard system |
| Tool skills | Vein Miner and Tree Capitator with block limits and permissions | Test against protection plugins and custom block providers |
| Staff vanish | LuckPerms group-gated vanish and join/quit visibility behavior | Verify with TAB, Geyser and other vanish plugins to avoid duplicate visibility control |
| Leaderboards / performance | Basic top-stat queries and server snapshot command | Snapshot is diagnostic, not an automatic optimizer |

## Default release safety

- Casino is disabled by default (`modules.casino: false`, `casino.yml settings.enabled: false`). Keep it disabled until odds, limits and provider error behavior are tested on the live economy plugin.
- Shards cannot go negative. Player-to-player transfers, real-money purchases and cash-outs are disabled.
- `/crates` management is Owner/Developer-only; players open configured crate blocks with physical keys.
- OpenInv is restricted to Builder/Developer/Owner policy as configured for your server, but the default permission mapping is read-only and does not grant inventory-edit nodes.
- Lib's Disguises and Skript management are Developer/Owner-only; HeadDatabase is Builder/Developer/Owner; vanish is staff-group-only.
- Never grant the Developer group access to Owner/Admin/Manager/Dev rank assignment. The rank GUI implementation is a Phase 2 area for further review and polish.
- Back up the entire server and database before testing this candidate.

## Config files

On first start, Core copies the default resources into `plugins/GASMP-Core/`:
- `config.yml` — master settings and module toggles
- `shop.yml` — product list/prices/category settings
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

The command list and aliases are declared in `src/main/resources/plugin.yml`. The key user commands include `/gasmp`, `/shards`, `/shop`, `/rtp`, `/bounty`, `/ah`, `/team`, `/battlepass`, `/casino` (disabled by default), and `/gvanish`. Staff/admin commands include `/gasmpcore`, `/gasmpadmin`, `/crates`, `/gmaintenance`, `/gban`, `/gunban`, `/gmute`, `/gunmute`, `/gwarn`, `/gkick`, `/ghistory`, `/gholo`, `/gchatgame`, `/gtop` and `/gperf`.

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
9. Keep casino disabled until all bets/payouts and provider-failure scenarios are verified.
10. Review console logs and database after repeated join/quit, plugin reload and server restart cycles.


## 0.7.0 remaining-system additions
- Expanded Teams service and full command guide, including named homes and `/team homes`.
- Transactionally limited team homes and symmetric alliance creation.
- Original 64×64 Vote and Legendary pixel-art crate atlases, Blockbench 5 models, separate animated lid bones, key icons and display icons.
- RP Engine content definitions for custom static crate blocks, animated placed crate displays, physical keys and guarded console interaction.
- Casino GUI menu layout fix and game-specific visual spin panels for the configured game set.
- Casino payout calibration updates and honest descriptions for simplified quick-match simulations.
- Staging-server launch plan, dependency notes and GitHub Actions Java 25 build workflow.

The GitHub Actions workflow is included at `.github/workflows/build.yml`. It runs static release validation and builds with Java 25 / Gradle 9.9.2, then uploads the JAR artifact. Follow `docs/GITHUB-ACTIONS.md` to publish this source to your GitHub repository and download the artifact. This source project has not been compiled here; live Paper 26.2 / Geyser / RP Engine testing remains necessary.
