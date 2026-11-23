# Cosmetic crate packs

GASMP Core 0.7.6 includes reward entries for the three cosmetic packs uploaded with the project:

- FNAF plushies: Freddy, Chica, Bonnie, Foxy, Golden Freddy and The Puppet.
- Feral Fantasy: bear, cat, fox, rabbit, tiger and wolf ears/tails.
- Wing Cosmetics: angel, astronaut, bluefire, brown, butterfly, demon, dragon, eagle, golden, ocean, phoenix and purple wings.

All Normal, Medium and Premium pools contain these cosmetic reward families. The pools do not award combat equipment. Existing GASMP furniture entries remain cosmetic placeables.

## Install the assets

Use the companion GASMP-Cosmetic-Assets.zip bundle:

- ItemsAdder: copy ItemsAdder/contents/ into plugins/ItemsAdder/contents/.
- Oraxen: merge Oraxen/items/ and Oraxen/pack/ into the matching folders under plugins/Oraxen/.
- Nexo: merge Nexo/items/ and Nexo/pack/ into the matching folders under plugins/Nexo/. The supplied Nexo files cover Feral Fantasy.

ItemsAdder is the easiest single-provider setup for the three uploaded packs. Oraxen is also supported for all three. You do not need to install all three providers; install the one whose assets you are using. Avoid loading duplicate versions of the same pack through multiple resource-pack engines.

After copying the files, run the appropriate item-plugin reload and resource-pack build/reload commands for the installed plugin version, then reconnect with a client to receive the updated pack. Command names vary by plugin version; use that plugin's installed documentation. Verify each pack's item IDs are loaded before opening crates.

## Physical GASMP crates

Copy integrations/rp-engine/content/gasmp_crates/ into plugins/RPEngine/content/gasmp_crates/, then reload RP Engine and regenerate/reload its resource pack. The pack defines Normal, Medium and Premium keys, physical crate blocks and animated placed crate displays. The Normal and Medium block models reuse the included Vote crate model; Premium reuses the Legendary model.

- Right-click a physical crate to open it with its matching key.
- Left-click to preview its configured reward pool.
- Sneak + left-click to break/pick up the matching crate block or display item.

Give test items from the server console or as an Owner/Developer:

    crates givekey <player> normal 1
    crates givekey <player> medium 1
    crates givekey <player> premium 1
    crates giveblock <player> normal 1
    crates giveblock <player> medium 1
    crates giveblock <player> premium 1
    crates givemodel <player> normal 1
    crates givemodel <player> medium 1
    crates givemodel <player> premium 1

## Provider resolution

Reward IDs use provider alternatives separated by a vertical bar. GASMP Core tries each configured provider and uses the first matching loaded item. For example, the Freddy Plush reward tries ItemsAdder fnafpack:freddy_plush and then Oraxen freddy_plush. Feral Fantasy also has Nexo candidates.

The optional provider APIs are resolved reflectively; there are no hard compile-time dependencies on ItemsAdder, Oraxen or Nexo. If none of the configured providers is installed or an item ID is missing, the crate reward is rejected and its key is returned instead of consuming a key without a reward.

## Asset licensing

The packs were supplied by the server owner for this project. Keep each pack's original license and creator terms with the files; do not redistribute them outside the server/project unless their respective licenses allow it.