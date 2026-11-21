# GASMP Core dependencies

## Required at runtime

| Dependency | Why it is required | Notes |
|---|---|---|
| Paper 26.2 | Server API | Purpur may work where the same Paper API behavior is supported. |
| Java 25 | Server runtime | Required by current Paper 26.2 generation. |
| LuckPerms 5.x | Rank groups and staff membership API | GASMP uses the LuckPerms service API. |
| Vault | Economy service API | Coins systems need a provider registered with Vault. |

## Required only for building

| Dependency | Version | Purpose |
|---|---|---|
| Gradle | 9.x | Build project |
| Paper API | `26.2.build.135-stable` | Compile against Paper |
| LuckPerms API | `5.5` | Compile the rank/staff integration |
| VaultAPI | `1.7` | Compile the economy bridge |
| SQLite JDBC | `3.54.1.0` | Runtime SQLite driver; included in the plugin output |

## Optional server plugins

- **Economy:** a plugin which registers Vault `Economy` (e.g. EssentialsX Economy). Without a provider, Shards still work but Coins shop, bounties and casino remain unavailable.
- **TAB:** displays LuckPerms prefix and primary group; see `integrations/tab/README.md`.
- **PlaceholderAPI:** placeholders in supported external displays.
- **Geyser + Floodgate:** Bedrock cross-play and identity integration.
- **RP Engine (recommended free option):** optional GPL-3.0 custom-item/block/furniture/model/resource-pack framework. It supports Paper/Purpur 26.2 and reads editable Blockbench `.bbmodel` animations. GASMP includes original Vote/Legendary crates and a six-model animated furniture pack under `integrations/rp-engine/`. See `integrations/rp-engine/README.md`. Bedrock rendering still needs live Geyser validation.
- **ItemsAdder / Nexo:** optional alternatives if already installed. GASMP Core does not require a paid asset plugin; do not install multiple overlapping resource-pack engines without testing pack delivery and priority.
- **OpenInv:** staff inventory-view integration. Core's restriction listener and LuckPerms nodes aim to keep access read-only.
- **Lib's Disguises, HeadDatabase/HeadDB, Skript, ImageFrame, Typewriter:** optional third-party tools, separately installed and configured.

## Compatibility note

“Works with any economy plugin” means any plugin which correctly registers Vault's standard `Economy` service. Plugins which do not expose that service require an additional provider adapter. Do not assume proprietary economy APIs are automatically compatible.
