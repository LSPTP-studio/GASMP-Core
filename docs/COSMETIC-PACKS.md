# GASMP cosmetic packs — RP Engine only

GASMP Core 0.7.8 uses **RP Engine as its only custom-item provider**. Every crate reward points directly to a native RP Engine content ID.

## Included cosmetics

The uploaded packs provide these items:

- **FNAF plushies:** Freddy, Chica, Bonnie, Foxy, Golden Freddy and The Puppet.
- **Feral Fantasy:** bear, cat, fox, rabbit, tiger and wolf ears/tails.
- **Wing Cosmetics:** angel, astronaut, bluefire, brown, butterfly, demon, dragon, eagle, golden, ocean, phoenix and purple wings.

All three cosmetic crate tiers use these content families in their reward pools. The reward pools do not award combat equipment.

## Install

Use `GASMP-RPEngine-Cosmetics.zip`. Its paths are already arranged for the server root:

`plugins/RPEngine/content/fnafpack/`
`plugins/RPEngine/content/ero_feral_fantasy_set/`
`plugins/RPEngine/content/ax_wings_pack/`

1. Back up `plugins/RPEngine/content/`.
2. Extract the ZIP into the **server root**, preserving the folder paths.
3. Run `/rp reload` from console.
4. Read the RP Engine console output and fix any asset-import warnings before testing.
5. Regenerate/resend the RP Engine resource pack using the workflow for your installed RP Engine version, then reconnect clients.

The supplied source folders are already arranged in RP Engine's documented direct-import layout. RP Engine imports them directly; no second custom-item plugin is needed.

The imported pack folder names become namespaces:

| Pack folder | RP Engine item ID examples |
|---|---|
| `fnafpack` | `fnafpack:freddy_plush` |
| `ero_feral_fantasy_set` | `ero_feral_fantasy_set:ero_feral_fantasy_bear_ears` |
| `ax_wings_pack` | `ax_wings_pack:angel_wings` |

Test the imports from console:

```text
rp give fnafpack:freddy_plush 1
rp give ero_feral_fantasy_set:ero_feral_fantasy_bear_ears 1
rp give ax_wings_pack:angel_wings 1
```

## Physical GASMP crates

The same RP Engine content folder contains GASMP's Normal, Medium and Premium crate keys, blocks and animated display models. Keep `integrations/rp-engine/content/gasmp_crates/` in place alongside the cosmetic pack folders.

- Right-click a physical crate to open it with its matching key.
- Left-click to preview its configured reward pool.
- Sneak + left-click to break/pick up the matching crate block or display item.

Give test items from the server console or as an Owner/Developer:

```text
crates givekey <player> normal 1
crates givekey <player> medium 1
crates givekey <player> premium 1
crates giveblock <player> normal 1
crates giveblock <player> medium 1
crates giveblock <player> premium 1
crates givemodel <player> normal 1
crates givemodel <player> medium 1
crates givemodel <player> premium 1
```

## Important notes

- Install **RP Engine only** for these cosmetic items.
- The FNAF plushes import as placeable furniture where their original config defines that behavior.
- The wings and Feral Fantasy models are custom items. Their display/equip behavior is limited to what the source item definitions and RP Engine importer support; a separate cosmetic-equipment plugin is not included.
- Follow the original creators' license and attribution terms. The uploaded files are being prepared for this server's own use; do not redistribute them outside the server unless their licenses allow it.
