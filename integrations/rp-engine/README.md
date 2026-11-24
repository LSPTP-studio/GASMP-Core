# GASMP custom crate content — RP Engine

This folder contains GASMP's native RP Engine crate content:

- `vote_crate` and `legendary_crate` Blockbench models with opening animations.
- Normal, Medium and Premium cosmetic crate keys, custom blocks and placeable display models.
- Interactions routed to GASMP Core's console-only `gcrateopen` command.

**GASMP uses RP Engine only.** ItemsAdder, Oraxen and Nexo are not required or used by GASMP Core for the cosmetic rewards.

## Install RP Engine

1. Back up the server and `plugins/RPEngine/content/`.
2. Install a compatible RP Engine release.
3. Copy `content/gasmp_crates/` from this repository into `plugins/RPEngine/content/gasmp_crates/`.
4. Also extract `GASMP-RPEngine-Cosmetics.zip` into the server root. It installs the three cosmetic folders alongside `gasmp_crates`.
5. Install the GASMP Core JAR.
6. Run `/rp reload` and review the console for import warnings.
7. Regenerate/resend RP Engine's resource pack using the workflow for your installed version.
8. Test the items, physical blocks and placed models on your Java clients. Validate Bedrock/Geyser separately if you use it.

The cosmetics ZIP contains the original supplied ItemsAdder-format folders in RP Engine's direct-import layout. RP Engine can import those formats itself; the ItemsAdder plugin is not required.

## GASMP crate commands

Owner/Developer or console:

- `/crates list`
- `/crates givekey <player> <normal|medium|premium> [amount]`
- `/crates giveblock <player> <normal|medium|premium> [amount]`
- `/crates givemodel <player> <normal|medium|premium> [amount]`

Physical custom-block interactions call `/gcrateopen <player> <crate-id>` through console. That internal command rejects player senders and is not intended for direct player use.

## Interactions

- Right-click a crate to open it with the matching key.
- Left-click a crate to preview the reward pool.
- Sneak + left-click to mine/pick up the crate.

## Notes

RP Engine's custom-block state registry is stored in `plugins/RPEngine/blocks.json`. Back it up with the world and plugin data. For Geyser/Bedrock, validate the actual pack and client versions; successful Java resource-pack generation does not guarantee identical Bedrock rendering.
