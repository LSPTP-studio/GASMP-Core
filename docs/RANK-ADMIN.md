# GASMP rank administration

## Access
- `/gasmpadmin` and `/gadmin` open the rank panel.
- Access requires membership in one of `rank-admin.admin-groups` in `ranks.yml` (default: `dev`, `owner`).
- GASMP Core adds `gasmpcore.rankadmin` to those LuckPerms groups. OP status alone is not enough.
- Developers cannot change their own rank, grant Owner/Admin/Manager/Dev, or modify a target who holds a protected top-staff rank.
- Owner can grant every configured rank.
- Every successful change is written to `gasmp_rank_audit`.

## Tracks
A player can hold one rank per track. Assigning a rank replaces only the previous GASMP rank in that same track; ranks in other tracks and unrelated LuckPerms groups remain intact.

- `staff`: Owner, Admin, Manager, Dev, Builder, Mod, Helper
- `creator`: YouTuber, TikToker, Streamer, Famous
- `supporter`: Booster, MVP+, MVP, VIP+, VIP, User
- `prestige`: Legendary, Heroic, Epic, Rare, Uncommon, Common

The highest configured weight among a player's active GASMP rank groups is saved as their LuckPerms primary group for TAB.

## Setup
1. Install and enable LuckPerms.
2. Start the server once. GASMP Core creates configured groups and synchronizes global permanent prefix/weight nodes.
3. Confirm the group names under each `group:` key match the names used on your server.
4. Add your trusted developer accounts to `dev` and your owner account to `owner` using LuckPerms.
5. Test `/gasmpadmin` with an Owner, Developer, Admin, and ordinary player.

GASMP Core manages the configured rank group's global permanent prefix and weight nodes. It does not delete other permission nodes from those groups.

## Rank-up reveal
A successful change displays a "RANK UNLOCKED" title, toast sound, and particles to the online target. Disable with `rank-admin.play-rank-up-cinematic: false`.
