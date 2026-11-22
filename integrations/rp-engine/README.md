# GASMP RP Engine integration

GASMP Core uses RP Engine for all custom items, wearable cosmetics, crate keys, custom crate blocks and placeable crate displays. ItemsAdder is not a runtime dependency.

## Included content

- `content/gasmp_crates`: four crate tiers, matching keys and placed displays.
- `content/hatparade`: imported 30-hat wearable collection.
- `content/ax_free_hats_pack`: imported six-hat wearable collection.
- `content/gasmp_ranks` and `content/rank_bundle`: optional imported rank-image/font content.

The full cosmetic models and textures for the crates and hats are bundled in the GASMP RP Engine Test Pack archive. Copy its `content/` directory into `plugins/RPEngine/`.

## Install

1. Install a RP Engine version compatible with the server's Paper/Minecraft build.
2. Back up `plugins/RPEngine/content/` and the server resource pack.
3. Copy the namespace folders into `plugins/RPEngine/content/`, preserving their names.
4. Reload RP Engine and regenerate/reload the resource pack using commands supported by the installed release.
5. Restart GASMP Core or run `/gasmpcore reload`.
6. Test the full crate flow on a staging server before production.

RP Engine can import ItemsAdder-format content folders directly. Cosmetic item IDs remain `hatparade:<item>` and `ax_free_hats_pack:<item>`, so GASMP's existing crate reward IDs stay unchanged. The six Artillex items have been marked with wearable-hat behaviour in the test pack.

## Crate administration

- `/crates list`
- `/crates givekey <player> <tier> [amount]`
- `/crates giveblock <player> <tier> [amount]`
- `/crates givemodel <player> <tier> [amount]`
- `/crates set <tier>` and `/crates unset <tier>` for chest/barrel fallbacks
- `/crates reload`

The crate block and placed-display actions invoke the internal console-only `gcrateopen` command. Players cannot invoke that command directly.
