# GASMP RP Engine integration

GASMP-Core uses RP Engine for custom items, wearable cosmetics, animated rank tags, crate blocks, placeable crate displays, custom menu screens, and furniture. ItemsAdder is not a runtime dependency.

## Tested build output

The companion content archive is built for Minecraft Java 1.21.11 resource-pack format 75:

- `GASMP-RPEngine-Content-1.21.11-Fixed.zip`
- The archive contains the six namespaces `gasmp_ui`, `gasmp_ranks`, `gasmp_crates`, `hatparade`, `ax_free_hats_pack`, and `gasmp_furniture`.
- It is a transport archive of namespace content folders; it is **not** a standalone Minecraft resource-pack ZIP.
- The archive deliberately has no `assets/minecraft/` overrides and no `overrides/` directory.

## Install

1. Stop the server and back up `plugins/RPEngine/content/`, `plugins/RPEngine/blocks.json`, and the current server resource-pack output.
2. Extract the namespace directories directly into `plugins/RPEngine/content/`. Do not put them under another nested `content/` directory.
3. Keep the existing `plugins/RPEngine/blocks.json`: custom block state allocations are persistent and must not be reset after blocks have been placed.
4. Start the server or use `/rp reload` if supported by the installed RP Engine release. Review all RP Engine warnings and errors before players join.
5. Verify the custom content using `/rp info`, `/rp blocks`, and `/rp items`.
6. Install the updated GASMP-Core JAR with a full server restart. Do not use Bukkit `/reload`.
7. Ask one client to disconnect and reconnect to receive the newly-built server resource pack. Clear their cached server pack only if Minecraft keeps showing the previous one.

## Included content

- `gasmp_ui`: custom 256×256 screens for GASMP's main menu, shop, casino, ranks, crates, and related pages.
- `gasmp_ranks`: animated RP Engine tag icons for the 23 configured LuckPerms rank groups.
- `gasmp_crates`: four custom crate blocks, four matching keys, and four animated placeable crate displays.
- `hatparade`: 30 wearable cosmetics using the vendor's 1.21.11 preview resource pack.
- `ax_free_hats_pack`: six wearable cosmetics with updated 1.21.11 item definitions and pack metadata.
- `gasmp_furniture`: the placeable GASMP Modern Sofa model with a sit animation.

## Crate administration

- `/crates list`
- `/crates givekey <player> <tier> [amount]`
- `/crates giveblock <player> <tier> [amount]`
- `/crates givemodel <player> <tier> [amount]`
- `/crates set <tier>` and `/crates unset <tier>` for vanilla chest/barrel fallback locations
- `/crates reload`

Valid tiers are `common`, `rare`, `epic`, and `legendary`. The patched commands also normalize the corresponding `gasmp_crates:` item IDs. The generated server config migration fills missing defaults in existing YAML files without overwriting settings already present.

Test items can be obtained with RP Engine's `/rp give <namespace:id>` command, or with GASMP-Core's `/crates giveblock` and `/crates givemodel` commands.

## Rank badges and animations

GASMP-Core adds `rpengine.tag.gasmp_ranks.<image-id>` to the matching LuckPerms group. To show the animated rank badge in chat or TAB, the formatting must include RP Engine's `%rpengine_tag%` placeholder. The ranks catalogue displays each configured badge through PlaceholderAPI when the RP Engine icon placeholder is available.

The rank-up cinematic plays after a rank is successfully assigned through the GASMP rank-admin confirmation flow. It is controlled by `rank-admin.play-rank-up-cinematic` in `config.yml`.

## Casino dependency

The arcade spends Vault Coins, not Shards. Vault is only the economy API; a Vault-compatible economy provider is also required (for example EssentialsX Economy). Ensure both Vault and the provider load, then keep `modules.casino: true` in GASMP-Core's `config.yml` and `settings.enabled: true` in `casino.yml`. The casino menu can be browsed without an economy provider, but betting remains locked until one is available.

## Compatibility note

The content archive is for Java 1.21.11 resource-pack format 75. Do not serve it to other Minecraft versions without a resource-pack compatibility conversion. RP Engine itself must also be compatible with the server's Paper build.
