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

## GASMP physical crates

The pack currently includes two named, placeable crate types:

| Crate | Internal ID | Matching key |
|---|---|---|
| **Vanguard Supply Cache** | `vote` | Vanguard Key |
| **Abyssal Relic Vault** | `legendary` | Abyssal Key |

Each type has a custom block definition, a Blockbench model, an animated display-item variant, and its own key item. The internal IDs stay stable so existing reward tables and stored locations are not broken.

### Install and place

1. Install the compatible RP Engine release and copy `content/gasmp_crates/` into its content directory.
2. Run `/rp reload` and check the console for missing models, textures, or content IDs.
3. As an Owner/Developer, give yourself the block and key:
   - `/crates giveblock <player> vote`
   - `/crates givekey <player> vote`
   - `/crates giveblock <player> legendary`
   - `/crates givekey <player> legendary`
4. Place the physical custom block in the world. Right-click it while holding the matching key.
5. Use `/crates givemodel <player> <crate-id>` for the animated floor-display variant instead of the solid custom block.

Developer/Owner commands:

- `/crates list` — show crate names and whether RP Engine is enabled.
- `/crates givekey <player> <crate-id> [amount]` — issue the matching key.
- `/crates giveblock <player> <crate-id> [amount]` — issue the solid custom block.
- `/crates givemodel <player> <crate-id> [amount]` — issue the animated placeable display item.
- `/crates set <crate-id>` — register a vanilla chest/barrel fallback.
- `/crates unset <crate-id>` — remove the fallback location.
- `/crates reload` — reload reward and display-name configuration.

Physical custom-block interactions call `/gcrateopen <player> <crate-id>` through console. The internal command rejects player senders. GASMP Core then checks the matching key, rolls the configured weighted reward, runs the opening animation, and only consumes the key when the opening completes.

## Java/Bedrock behavior

- Java clients use custom blocks and models supported by RP Engine.
- Geyser's RP Engine bridge can generate/deliver a Bedrock pack for supported assets.
- Custom blocks on Bedrock may fall back to their underlying vanilla block appearance; the placed model/furniture representation is the preferred path where the RP Engine version supports Bedrock entity-model delivery.
- Always test on the actual Geyser/Floodgate build and client versions used by GASMP. A resource pack compiling successfully does not guarantee every shader/model behaves identically on Java and Bedrock.

## Model editing

Open the `.bbmodel` files in Blockbench 5.x. Each crate contains a body group and a separate animated lid group. The `open` animation rotates the lid around its hinge pivot. Export/convert through the RP Engine workflow supported by the installed version.

The textures are 64x64 pixel-art atlases with separate UV regions for crate body faces and lid faces. Replace textures in the pack's `assets/textures/block/` and update the inline texture source in the `.bbmodel` if you edit the model texture externally.


## GASMP furniture content

The separate `gasmp_furniture` content pack adds the editable GASMP Modern Sofa Blockbench 5 model and its original 128x128 texture. The model contains separate frame, upholstery, seat cushion, back cushion, throw pillow and brass hardware groups, plus a short `sit_press` cushion animation. Copy the entire `gasmp_furniture` folder into the RP Engine content directory before reloading/generating the resource pack. Test the resulting model and placement on the exact RP Engine and Geyser versions used by the server.
