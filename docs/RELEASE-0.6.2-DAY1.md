> Historical 0.7.0 notes. Superseded by `RELEASE-0.7.0-DAY1.md`; use that file as the current Day 1 report.

# GASMP Core 0.7.0 — Day 1 stabilization record

## Source changes in this release
- Synchronized Gradle/plugin metadata and documentation to 0.7.0.
- Added exception handling around the Vault affordability check in bounty placement so a provider runtime failure releases the in-progress lock and reports that no bounty was placed.
- Added persistent auction mailbox delivery for purchased, cancelled, expired and failed-listing items; added join delivery, manual `/ah collect`, expiry processing and per-player mailbox collection lock.
- Corrected auction expiry to enqueue a return item only when the status transition actually changed an active listing.
- Corrected the Poker quick-simulation payout calculation to a 1.85x gross win and documented its 95% theoretical return; casino remains disabled by default.
- Hardened Battle Pass rewards: inventory item reward rollback and main-thread handoff for player-facing callbacks.
- Added crate-block protection against player breaking, explosions and piston movement; corrected physical key consumption and validates reward configuration before consuming keys.
- Updated staff command restrictions and OpenInv integration nodes; deny edit/offline access by default.
- Added maintenance persistence/login gate/MOTD handling and hologram reload/delete callback token handling.
- Repaired the SQLite data store shutdown exception handling and verified the store class compiles independently with the available JDK.

## Verification completed in this environment
- All 11 bundled YAML resources parse with PyYAML.
- All 25 commands declared in `plugin.yml` have a matching registration in `GASMPCore`; no missing or undeclared registrations.
- Java source parser scan reports no Java syntax/duplicate-local/lambda-capture diagnostics after correcting the auction listing variable shadowing.
- `PlayerDataStore.java` compiles independently with `javac --release 21` (its language features do not require Java 25).
- The complete source archive and the user-supplied rank asset archives passed ZIP/RAR integrity checks.
- SQLite transaction behavior for shard ledger and auction item-mail state transitions was tested using Python's SQLite engine.

## Not verified
- Full Gradle build and final JAR: unavailable because this workspace has Java 21, no Gradle installation, no cached Paper/Vault/LuckPerms artifacts and no network dependency resolution.
- Paper 26.2 live startup and gameplay: not tested.
- Vault provider behavior depends on the specific economy plugin and requires live balance/deposit/withdraw testing.
- Geyser/Floodgate/Bedrock and TAB behavior require a live multi-client test.

## Known intentional limitations before launch
- Casino stays disabled; current gameplay is command-mode simulation, not the animated casino GUI.
- Teams are the foundational system (create/invite/accept/leave/kick/disband/info/chat), not the requested full suite with homes, banks, alliances, upgrades and advanced roles.
- Auctions are command-based, not a finished search/filter GUI; mailbox recovery should be load-tested.
- Crates are block/key/reward functional at the source level, but custom Blockbench block models and opening animations are Phase 2/post-launch asset work.
- Battle Pass is basic season XP and milestone rewards; missions and premium track UX are not complete.
- Holograms are basic persistent text displays, not an advanced animation editor.
- Music playback and decoration catalogue are not implemented.
- Detailed ranks, custom menus and Owner/Developer dashboards are reserved for Phase 2, as requested.
