> Historical notes for 0.5.1 only. Superseded by `RELEASE-0.7.0-DAY1.md`; do not use this file as the current feature list.

# GASMP Core 0.5.1 — feature notes

## Functional in this source milestone
- Vault-backed configurable Coins shop (`shop.yml`) with item selection, confirmation, inventory capacity checks, payment response checks, and no purchase without a Vault Economy provider.
- `/rtp` with async chunk requests, safe floor/headroom checks, world border validation, warmup movement cancellation, combat-tag restriction, and cooldown.
- Player-vs-player combat tags (including projectile shooter resolution); combat logout is recorded in SQLite and a one-time death penalty is applied on next join.
- Persistent Coins bounties: `/bounty place <online-player> <amount>` and `/bounty top`; bounty claims on player kill and are restored if the payout fails.
- `/gvanish` with role-based visibility; hidden staff are hidden from non-staff who join later.
- Main menu routes Shop to the live Coins catalogue and includes RTP/Bounty action hints.
- LuckPerms group setup grants the requested HeadDB, Lib's Disguises, OpenInv, vanish and GASMP management permission nodes to selected role groups.

## Dependencies and limits
- Coins features require Vault plus an economy plugin that registers `net.milkbowl.vault.economy.Economy`. A proprietary provider without Vault support requires a separate adapter.
- The combat-logout penalty is a kill on the player's next login, after the quit was recorded. Test it with keepInventory, death chests and inventory/drop plugins before enabling it in production.
- `rtp.yml` defaults to world `world`, min radius 750, max radius 6000. Change the world to the exact GASMP survival world.
- RTP uses Paper's `World#getChunkAtAsync` and `Entity#teleportAsync` APIs.
- The rank panel still requires LuckPerms. `/gasmpadmin` authorizes by actual configured group membership plus the GASMP rankadmin node.

## Not yet complete
Auction house, casino games, battle pass, crates GUI, Vein Miner, Tree Capitator, advanced punishment history/mutes, hologram editor, settings persistence, music playback, decorations catalog, maintenance GUI and complete custom Java/Bedrock visual pack are not enabled in this source milestone. Their menu labels must not be treated as finished features.

