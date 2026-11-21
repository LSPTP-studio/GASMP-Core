# GASMP custom crate content — RP Engine

This is an original, editable crate-content starter pack for **RP Engine**, a free/open-source resource-pack and custom-model engine. It contains:

- `vote_crate` and `legendary_crate` Blockbench 5 models (`.bbmodel`).
- A lid-open animation for each model.
- Custom crate block definitions.
- Animated placed crate-display items.
- Custom Vote and Legendary key items.
- Interaction actions that call GASMP Core's console-only `gcrateopen` command.

## Install RP Engine

1. Back up the current server and resource-pack files.
2. Install a RP Engine release that explicitly supports your Paper 26.2 build.
3. Restart the server once to generate its content directory.
4. Copy the `content/gasmp_crates/` folder from this archive into `plugins/RPEngine/content/gasmp_crates/` (keep the namespace and folder names).
5. Install the matching GASMP Core JAR and ensure its plugin descriptor soft-depends on `RPEngine`.
6. Run `/rp reload` and review the console for content errors.
7. Generate/reload the RP Engine resource pack using the commands documented by your installed RP Engine release.
8. Join with a Java client and a Geyser Bedrock client to confirm assets and fallback behavior.

RP Engine command names and folder casing can change between releases. Use the exact folder/commands in the installed version's documentation; do not rename the `gasmp_crates` content namespace.

## GASMP crate commands

Developer/Owner only:

- `/crates list`
- `/crates givekey <player> <crate-id> [amount]`
- `/crates giveblock <player> <crate-id> [amount]`
- `/crates givemodel <player> <crate-id> [amount]`

Physical custom-block interactions call `/gcrateopen <player> <crate-id>` through console. This internal command rejects player senders and is not intended for direct player use.

## Java/Bedrock behavior

- Java clients use custom blocks and models supported by RP Engine.
- Geyser's RP Engine bridge can generate/deliver a Bedrock pack for supported assets.
- Custom blocks on Bedrock may fall back to their underlying vanilla block appearance; the placed model/furniture representation is the preferred path where the RP Engine version supports Bedrock entity-model delivery.
- Always test on the actual Geyser/Floodgate build and client versions used by GASMP. A resource pack compiling successfully does not guarantee every shader/model behaves identically on Java and Bedrock.

## Model editing

Open the `.bbmodel` files in Blockbench 5.x. Each crate contains a body group and a separate animated lid group. The `open` animation rotates the lid around its hinge pivot. Export/convert through the RP Engine workflow supported by the installed version.

The textures are 64x64 pixel-art atlases with separate UV regions for crate body faces and lid faces. Replace textures in the pack's `assets/textures/block/` and update the inline texture source in the `.bbmodel` if you edit the model texture externally.
