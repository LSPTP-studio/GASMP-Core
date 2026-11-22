# GASMP rank images — ItemsAdder

This optional content export was generated from the rank image archives supplied for GASMP.

## Install
1. Back up `plugins/ItemsAdder/contents/`.
2. Copy the `gasmp_ranks` folder into `plugins/ItemsAdder/contents/`.
3. If you want to keep the six-emblem Chiaki bundle as well, copy `rank_bundle/` too.
4. Run `/iareload`, review the console, then run `/iazip`.
5. Deliver the generated pack using your existing ItemsAdder/Geyser pack workflow.

Prefix image names include `:gasmp_owner:`, `:gasmp_admin:`, `:gasmp_dev:`, `:gasmp_builder:`, etc. The 72 badge icons are `:gasmp_badge_01:` through `:gasmp_badge_72:`. With PlaceholderAPI, the equivalent font-image placeholders are `%img_gasmp_owner%`, `%img_gasmp_admin%`, etc.

GASMP Core keeps plain text LuckPerms prefixes by default. That is intentional so TAB names stay readable without a custom font pack. The image assets are available for GUI decoration and optional later prefix styling.

The original `rank_bundle` namespace/config and its textures are preserved. Keep these assets inside the license terms under which they were obtained; do not redistribute the asset pack as a standalone product.


## GASMP cosmetic crate keys

The additional contents/gasmp_crates/configs/keys.yml file defines the four physical crate keys used by GASMP Core:
- gasmp_crates:common_key
- gasmp_crates:rare_key
- gasmp_crates:epic_key
- gasmp_crates:legendary_key

Copy contents/gasmp_crates/ into plugins/ItemsAdder/contents/, then run /iareload and /iazip.

The full wearable cosmetic collection (30 Hat Parade hats and six Artillex hats, including models and textures) is supplied in the separate GASMP ItemsAdder Test Pack archive. Both collections use behaviours.hat: true. The Artillex six-hat definitions are adapted to the 26.2 graphics.model profile.

GASMP's crate loot is cosmetic-only. Players purchase the matching key in /shardshop, inspect drop chances with /crate, and open registered chest/barrel locations. Register locations as Owner/Developer with /crates set common, /crates set rare, /crates set epic, and /crates set legendary.
