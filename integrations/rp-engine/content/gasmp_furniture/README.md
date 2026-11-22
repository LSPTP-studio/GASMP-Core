# GASMP Furniture Pack

This pack contains original GASMP furniture content for RP Engine.

## Included model

- `gasmp_modern_sofa.bbmodel`: editable Blockbench 5 project, 33 cubes, six named groups, embedded texture and `sit_press` cushion animation.
- `gasmp_modern_sofa.png`: matching 128x128 texture atlas.

## In-game behavior

The GASMP Modern Sofa is a placeable RP Engine furniture item.

- Place it on a solid floor using the item from the Shard Shop.
- Right-click the placed sofa to play its one-shot cushion-press animation.
- Sneak + right-click to sit on its seat.
- Sneak to stand up again.
- Breaking the model returns the original custom item by default.

The model's cushion animation comes from its Blockbench `.bbmodel` animation. The item's `place.animations` configuration sets that animation to play once on a normal right-click, while `place.seat` enables RP Engine's built-in seating behavior for a sneaking right-click.

## Shard Shop item

The Shard Shop product uses the content ID `gasmp_furniture:gasmp_modern_sofa`. The shop materializes the actual RP Engine item before debiting Shards. If RP Engine or the content definition is unavailable, checkout is refused before any balance change.

## Install

1. Back up the server and resource pack.
2. Copy the complete `gasmp_furniture` folder into `plugins/RPEngine/content/`.
3. Run the RP Engine content reload and resource-pack generation workflow documented by the exact installed release.
4. Confirm the console loads `gasmp_furniture:gasmp_modern_sofa` without errors.
5. Test placement, normal right-click animation, sneak-right-click seating, breaking and item return on Java and Geyser Bedrock clients before distributing it.

RP Engine handles the placement, click animation, seat and item return. Client rendering and behavior should still be verified on the actual RP Engine and Geyser/Floodgate versions used by GASMP.
