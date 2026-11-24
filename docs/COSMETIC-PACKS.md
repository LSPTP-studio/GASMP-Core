# GASMP cosmetic pack — ItemsAdder only

GASMP Core 0.8.2 uses ItemsAdder 4.x as its only custom-item, custom-furniture and resource-pack provider. RP Engine is not required.

## Included namespace

integrations/itemsadder/contents/gasmp/ defines the exact gasmp: IDs used by GASMP Core:

- 96 cosmetic IDs referenced by the crate reward pools and Shards Decoration Shop.
- FNAF-inspired plush collectible entries.
- Bear, cat, fox, rabbit, tiger and wolf ears/tails.
- Twelve wing collectibles.
- Named hats, crowns, beanies, helmets and novelty hats.
- Placeable decoration items including beds, sofas, tables, lamps, windows, kitchen pieces, screens and plushies.
- Three named crate furniture items and three matching keys.

Normal, Medium and Premium crates contain configured cosmetics and small Shards bonuses only. They do not award combat equipment.

## Install

1. Back up plugins/ItemsAdder/contents/.
2. Copy integrations/itemsadder/contents/gasmp/ and integrations/itemsadder/contents/gasmp_ranks/ into plugins/ItemsAdder/contents/.
3. Optionally copy integrations/itemsadder/contents/rank_bundle/ for the additional rank emblem font images.
4. Run /iareload and resolve any YAML/model errors in the console.
5. Run /iazip to regenerate the resource pack.
6. Refresh or resend the resource pack using your configured ItemsAdder hosting method, then reconnect test clients.

The included models use vanilla Minecraft block textures with self-contained starter geometry. The original source-model exports for the previously referenced plush, wings and Feral Fantasy packs were not present in this repository snapshot; these assets are not represented as recovered original artwork. Replace the starter JSON files with licensed final art while keeping the item IDs and model paths stable.

## Crate tiers

| Tier | Display name | Key | Placeable crate |
|---|---|---|---|
| Normal | VERDANT CACHE | gasmp:normal_key (VERDANT SEAL) | gasmp:normal_crate |
| Medium | RUNIC RELIQUARY | gasmp:medium_key (RUNIC SIGIL) | gasmp:medium_crate |
| Premium | ECLIPSE VAULT | gasmp:premium_key (ECLIPSE CREST) | gasmp:premium_crate |

Give test items from console or as Owner/Developer staff:

    crates givekey <player> normal 1
    crates givekey <player> medium 1
    crates givekey <player> premium 1
    crates giveblock <player> normal 1
    crates giveblock <player> medium 1
    crates giveblock <player> premium 1

givemodel is retained as an alias for giveblock.

## In-game behaviour

- Right-click a placed crate to open it using the matching key.
- Sneak + left-click a vanilla crate block to preview rewards.
- Right-click a placed ItemsAdder furniture crate to open it.
- Sneak + punch a furniture crate to preview it.
- Ordinary mining/punching is left to ItemsAdder for furniture pickup.

The GUI roulette is animated by GASMP Core: it scrolls across three item slots, slows before the reward, plays staged sounds/titles, displays a reveal card and burst particles, then consumes the key and delivers the reward. The key is not consumed if the opening is cancelled before the reward is committed.

Hats use ItemsAdder's hat behaviour, and furniture/plush items are placeable. Wings, tails and backpacks are currently 3D collectibles rather than a back-slot equipment system.

## Legacy files

integrations/rp-engine/ remains in the repository as a historical archive. Do not copy it into a live server for this ItemsAdder edition.
