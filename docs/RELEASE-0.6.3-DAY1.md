# GASMP Core 0.7.0 — Day 1 stabilization

**Target:** Paper 26.2 / compatible Purpur, Java 25.
**Status:** source release candidate; not a compiled or live-tested JAR.

## Changes in 0.7.0
- Added a reservation timestamp migration for existing auction databases.
- Changed abandoned auction payment reservations from automatic reopen to `REVIEW`, preserving buyer UUID so ambiguous debit/credit states cannot silently create duplicate sales.
- Added Owner-only `/ah review`, `/ah review-release <id> confirm-unpaid`, and `/ah review-settle <id> confirm-paid`; partial transfers require manual Coins reconciliation before resolving.
- Added persistent atomic resolution paths: release returns an expired item to seller mail; settle marks the listing SOLD and queues the item to the buyer in one SQLite transaction.
- Changed auction startup reconciliation to run once at startup only; routine expiry sweeps no longer quarantine an in-flight transaction in a live process.
- After `completeAuction` failure/false, the service inspects durable auction state before reversing any Coins. A committed SOLD row for the same buyer is treated as successful.
- Fixed Vein Miner/Tree Capitator BFS traversal so adjacent blocks are explored from the initiating block; additional blocks still pass through real cancellable block-break events.
- Kept Casino disabled by default.
- Updated docs and command help for review procedures and limitations.

## Verification performed
- `PlayerDataStore.java` compiles independently with `javac --release 21`.
- Full-source javac parser scan reports no syntax, duplicate-local-variable, unreported-exception or lambda-capture diagnostics; unresolved Bukkit/Paper/Vault/LuckPerms symbols remain because API dependencies are not installed in this workspace.
- All 11 YAML files parse with unique keys.
- All 25 commands in `plugin.yml` match startup registrations.
- Version metadata matches 0.7.0.
- SQLite migration and reservation-to-review/release/settle transactions pass an isolated SQLite test.
- Source ZIP and supplied ZIP asset archives pass CRC checks.

## Not verified
- Full Gradle build / plugin JAR: unavailable; this environment has JDK 21, no Gradle, and no local Paper/Vault/LuckPerms APIs.
- Paper 26.2 live startup, Vault provider transaction behavior, Geyser/Floodgate, TAB, WorldGuard/protection and third-party permission behavior remain to be tested on GASMP's staging server.
- The RAR header/index was readable, and its extracted assets are included, but a full RAR compressed-data CRC could not be checked without an unrar-compatible utility.

## Known release limitations
- Teams are foundational (create/invite/accept/leave/kick/disband/info/chat), not the requested advanced role/home/bank/alliance/upgrades suite.
- Auction house is command-based, not a full GUI. Mailbox delivery and player inventory persistence cannot be made completely atomic across a process crash.
- Casino stays disabled pending live odds/provider testing; command simulations are not the animated GUI games.
- Crates use configured physical key items and protected locations, but custom Blockbench models and opening cinematics are Phase 2/post-launch work.
- Battle Pass is basic XP/milestone rewards; advanced missions and full reward track UI are unfinished.
- The current hologram service is basic text displays, not a full editor.
- Music player, complete decoration catalogue, custom GUI textures and full Bedrock pack are not complete.
- Rank/menu redesign, Owner/Developer dashboard design and rank cinematics remain Phase 2 as requested.
