# GASMP ItemsAdder content

This folder contains the full GASMP cosmetic/crate namespace and the optional rank-image namespace.

## Install

1. Back up plugins/ItemsAdder/contents/.
2. Copy the gasmp and gasmp_ranks folders into plugins/ItemsAdder/contents/.
3. Optionally copy rank_bundle/ for the additional six-emblem font-image bundle.
4. Run /iareload and resolve any YAML/model errors in the console.
5. Run /iazip and refresh the configured resource pack for clients.

## GASMP crate and cosmetic namespace

gasmp/configs/cosmetics.yml defines the item IDs expected by the plugin:

- 96 cosmetic reward IDs shared by the three crate pools and Decoration Shop.
- gasmp:normal_key, gasmp:medium_key and gasmp:premium_key.
- gasmp:normal_crate, gasmp:medium_crate and gasmp:premium_crate as placeable furniture.

The display names are STARLIGHT CACHE, ASTRAL CACHE and CELESTIAL VAULT. Starter models use vanilla block textures. Original third-party plush, wing and Feral Fantasy source-model exports were not present in this repository snapshot; replace the starter models with licensed final models while preserving each item ID and model path.

## Rank images

The gasmp_ranks namespace includes gasmp_owner, gasmp_admin, gasmp_dev, gasmp_builder and other font images, plus gasmp_badge_01 through gasmp_badge_72. With PlaceholderAPI, the corresponding font-image placeholders are %img_gasmp_owner%, %img_gasmp_admin%, etc.

GASMP Core keeps plain-text LuckPerms prefixes by default. This is intentional so TAB names remain readable for players who have not accepted the resource pack.

The original rank_bundle namespace/config and textures are preserved. Respect the source-art license and attribution terms; do not redistribute the asset pack outside the server unless its license allows it.
