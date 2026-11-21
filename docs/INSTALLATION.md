# GASMP Core 0.7.0 — installation guide

## 1. Required software

- Paper 26.2 or a compatible Purpur build.
- Java 25 for the Minecraft server and Gradle build.
- LuckPerms 5.x.
- Vault.
- A Coins/economy plugin that registers a Vault `Economy` provider (EssentialsX Economy is one option).

Optional integrations: TAB, PlaceholderAPI, Geyser, Floodgate, RP Engine (recommended free custom-content option), ItemsAdder, Nexo, OpenInv, Lib's Disguises, HeadDatabase/HeadDB, Skript, ImageFrame and Typewriter. Optional integrations are not all required for the Core systems to start.

## 2. Build from source

Open this project in IntelliJ IDEA or another Gradle-capable Java IDE, set the project and Gradle JVM to JDK 25, and run:

```powershell
gradle build
```

Expected output:

```text
build/libs/GASMP-Core-0.7.0.jar
```

This release archive has not been compiled in the preparation environment. Do not assume a JAR exists until the build completes successfully on your machine or CI runner.

## 3. Install on a test server first

1. Stop the test server and back up the entire server directory.
2. Install Paper 26.2, LuckPerms and Vault.
3. Install and configure one Vault-compatible economy plugin if you want Coins systems.
4. Copy `GASMP-Core-0.7.0.jar` into `plugins/` after building it.
5. Start the server once. Confirm the console reports SQLite initialization and does not show plugin-load errors.
6. Confirm `plugins/GASMP-Core/` contains `config.yml`, `shard-shop.yml`, `rtp.yml`, `casino.yml`, `battlepass.yml`, `crates.yml`, `chat-games.yml`, `maintenance.yml`, `holograms.yml` and `ranks.yml`.
7. Stop the server and review each config before inviting players.
8. Add your trusted staff accounts to LuckPerms groups. Review the group/permission setup in `docs/PERMISSIONS.md`.
9. Start the server and use the test checklist in `docs/PHASE1-TEST-PLAN.md`.

## 4. Economy setup

GASMP Core uses Vault's Economy service for Coins. It is compatible with economy plugins which register Vault's `Economy` service. A plugin with only a proprietary, non-Vault API is not automatically supported.

Shards are stored separately in GASMP Core's SQLite database and do not use Vault.

## 5. Backups and upgrades

Before every upgrade:

- Stop the server cleanly.
- Back up `plugins/GASMP-Core/`, including `data.db` and all YAML files.
- Back up the LuckPerms database/configuration.
- Keep a copy of the previous plugin JAR.
- Read the release notes for schema and config changes.

Do not delete `data.db` during an upgrade. Do not edit it manually while the server is running.

## 6. Java and Bedrock clients

Geyser does not automatically convert a Java resource pack into a Bedrock pack. Deliver a separately prepared Bedrock pack through your Geyser/Floodgate resource-pack configuration. The current Core release keeps basic menus readable with vanilla icons; final custom GUI artwork and dual-pack validation are Phase 2.

## 7. RP Engine custom crates (optional, recommended for custom models)

1. Install the free RP Engine plugin on the test server.
2. Copy `integrations/rp-engine/content/gasmp_crates/` to `plugins/RPEngine/content/gasmp_crates/`.
3. Run `/rp reload` and inspect startup/console output for content errors.
4. Use Developer/Owner `/crates givemodel <player> vote` and `/crates givemodel <player> legendary` to receive the animated placed models. Use `/crates giveblock ...` for the static custom-block variant and `/crates givekey ...` for matching keys.
5. The placed model furniture items use the Blockbench lid-open animation on interaction. The custom block variant is static because an ordinary block model cannot animate like an entity model.
6. Test Java and Bedrock clients through Geyser before placing the crates in the live spawn.

## 8. Rollback

1. Stop the server.
2. Restore the previous Core JAR and matching data/config backup.
3. Start the server and inspect the console.
4. Do not restore only the plugin JAR if the newer release has already migrated persistent data; restore the matching database backup too.
