# GASMP ItemsAdder content — 0.8.3

This folder contains the GASMP cosmetic/crate namespace, custom inventory font panels and optional rank-image namespace.

## Install

1. Back up plugins/ItemsAdder/contents/.
2. Copy the gasmp and gasmp_ranks folders into plugins/ItemsAdder/contents/.
3. Optionally copy rank_bundle/ for the additional rank emblem font images.
4. Run /iareload and resolve any YAML, model or font-image errors in the console.
5. Run /iazip to regenerate the resource pack.
6. Ensure the ItemsAdder pack is hosted and delivered to clients, then reconnect and ACCEPT the updated pack. Clients on a stale or declined pack cannot render the custom inventory panels or models.

## Custom inventory UI

- Font-image definitions: gasmp/configs/ui.yml
- Panel textures: gasmp/textures/font/ui_main.png, ui_shop.png and ui_crate.png
- Java inventory titles use ItemsAdder aliases such as :offset_-8::ui_main:, :offset_-8::ui_shop: and :offset_-8::ui_crate:.
- Panel PNGs are 176 x 222 pixels. The matching UI config uses scale_ratio 222 and y_position 222.

See the ItemsAdder Font Images guide: https://wiki.itemsadder.com/adding-content/font-images/font-images/.

## Six cosmetic crate tiers

| Internal ID | Placeable item | Matching key |
|---|---|---|
| normal | gasmp:normal_crate — VERDANT CACHE | gasmp:normal_key — VERDANT SEAL |
| medium | gasmp:medium_crate — RUNIC RELIQUARY | gasmp:medium_key — RUNIC SIGIL |
| premium | gasmp:premium_crate — ECLIPSE VAULT | gasmp:premium_key — ECLIPSE CREST |
| aether | gasmp:aether_crate — AETHER SANCTUM | gasmp:aether_key — AETHER SIGIL |
| tidal | gasmp:tidal_crate — TIDAL TROVE | gasmp:tidal_key — TIDAL TOKEN |
| obsidian | gasmp:obsidian_crate — OBSIDIAN RELIC | gasmp:obsidian_key — OBSIDIAN SEAL |

All six crates award cosmetic collectibles and small Shards bonuses, not weapons or combat equipment. The new crate furniture and keys have their own custom model JSONs and use vanilla block textures.

Existing plush, wing and Feral Fantasy model exports from third-party sources were not present in this repository snapshot. Their existing models remain starter geometry. Replace them only with licensed final art and keep the item IDs/model paths unchanged.

## Rank images

The gasmp_ranks namespace includes GASMP role font images and the optional gasmp_badge_01 through gasmp_badge_72 set. With PlaceholderAPI, aliases can be used as %img_gasmp_owner%, %img_gasmp_admin%, etc.

GASMP Core keeps plain-text LuckPerms prefixes by default so TAB names remain readable for players who have not accepted the resource pack. Respect source-art licenses and attribution; do not redistribute the asset pack outside the server unless permitted.