# GASMP Core dependencies

## Required at runtime

| Dependency | Why it is required | Notes |
|---|---|---|
| Paper 26.2 | Server API | Compiled against Paper API 26.2.build.135-stable. |
| Java 25 | Server runtime | Required by the current Paper 26.2 target. |
| ItemsAdder 4.x | Custom cosmetic items, keys, placeable furniture, models and resource-pack generation | ItemsAdder 4.0.13+ is recommended for modern graphics; the content config also retains legacy resource definitions. |
| LuckPerms 5.x | Rank groups and staff membership API | GASMP uses the LuckPerms service API. |
| Vault | Economy service API | Coins features need a provider registered with Vault. |

## Build dependencies

| Dependency | Version | Purpose |
|---|---|---|
| Gradle | 9.x | Build project |
| Paper API | 26.2.build.135-stable | Compile against Paper |
| LuckPerms API | 5.5 | Compile the rank/staff integration |
| VaultAPI | 1.7 | Compile the economy bridge |
| SQLite JDBC | 3.54.1.0 | Runtime SQLite driver, bundled into the plugin JAR |

## Optional server plugins

- Coins economy provider: a plugin that registers Vault's standard Economy service. Without a provider, Shards still work, but Coins shop, bounties and team bank features are unavailable.
- TAB: display LuckPerms prefixes and primary groups.
- PlaceholderAPI: placeholders in supported external displays.
- Geyser + Floodgate: Bedrock cross-play; Bedrock model delivery must be tested on the server build.
- ProtocolLib, ImageFrame, Typewriter, OpenInv, Lib's Disguises, HeadDatabase/HeadDB and Multiverse-Core: optional integrations/tools, installed separately.

## ItemsAdder content installation

Copy integrations/itemsadder/contents/gasmp/ and integrations/itemsadder/contents/gasmp_ranks/ into plugins/ItemsAdder/contents/. Run /iareload, fix any content errors, run /iazip and refresh the client resource pack.

The integrations/rp-engine/ directory is retained for historical reference only and is not required by this release.
