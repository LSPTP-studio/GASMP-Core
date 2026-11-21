# GASMP Core permissions and staff groups

LuckPerms is the permission source of truth. Group membership is case-insensitive in GASMP checks; the default group names are lowercase.

## Core role policy

| Group | Core access |
|---|---|
| `owner` | Full admin marker, rank admin, Shards admin, crates, casino config, vanish, maintenance bypass, OpenInv view/search only, HeadDatabase, Lib's Disguises, Skript admin |
| `dev` | Developer admin, rank admin subject to rank protection, Shards admin, crates, casino config, vanish, maintenance bypass, OpenInv view/search only, HeadDatabase, Lib's Disguises, Skript admin |
| `admin` | Vanish, maintenance bypass, Shards admin, OpenInv view/search only |
| `manager` | Vanish and maintenance bypass |
| `builder` | Vanish and HeadDatabase view/search/category access |
| `mod` | Vanish |
| `helper` | Vanish |
| `user` | No staff access |

The exact set is configured in `RankService`. Review it before using this candidate on a live server.

## GASMP permission nodes

- `gasmpcore.menu` — default true.
- `gasmpcore.admin` — reload/status management (group grants are applied by RankService).
- `gasmpcore.rankadmin` — rank GUI marker; actual access also checks LuckPerms group membership.
- `gasmpcore.shards.admin` — Shards balance modification.
- `gasmpcore.combat.bypass` — bypass combat logout tagging/punishment.
- `gasmpcore.crates.manage` — crate management marker; command itself also requires `owner` or `dev`.
- `gasmpcore.casino.play` — casino command permission. Casino remains disabled in default config.
- `gasmpcore.maintenance.bypass` — bypass maintenance login gate.
- `gasmpcore.skills.veinminer` — Vein Miner ability.
- `gasmpcore.skills.treecap` — Tree Capitator ability.
- `gasmpcore.staff.vanish` — marker for staff vanish; command itself checks group membership.
- `gasmpcore.moderation.basic` / `gasmpcore.moderation.advanced` — documented markers; moderation command role checks are additionally enforced in the service.

## Third-party staff access

- **HeadDatabase:** `builder`, `dev`, `owner`; Builder receives open/search/category nodes. Dev/Owner additionally receive the give node.
- **OpenInv:** `admin`, `dev`, `owner`; grant open-other, enderchest-open-other, search and online-access nodes only. Do not grant offline-access or inventory-edit nodes. Core sets explicit negative edit nodes for these groups.
- **Lib's Disguises:** `dev`, `owner` only. The command restriction listener also covers the common command aliases.
- **Skript:** `dev`, `owner` only, via `skript.admin` and command restriction.
- **Crates management:** `dev`, `owner` only. Player crate opening is by physical key at a configured block, not by `/crates` public command.
- **Vanish:** `owner`, `admin`, `manager`, `dev`, `builder`, `mod`, `helper`. Common vanish command aliases are blocked for non-staff.

## Before launch

1. Make sure the real Owner account is a member of `owner`.
2. Make sure only trusted developer accounts are members of `dev`.
3. Test all restricted commands with a normal player, Builder, Mod, Admin, Developer and Owner.
4. Verify aliases and namespaced commands are blocked as expected.
5. Check the actual OpenInv and HeadDatabase versions' permission nodes; plugin forks/versions can differ.


## Auction payment review

- `gasmpcore.auction` allows normal `/ah` commands (default true).
- `/ah review`, `/ah review-release` and `/ah review-settle` require the configured LuckPerms `owner` group membership. OP status alone is not accepted.
- `review-release` requires the literal confirmation token `confirm-unpaid`; only use it after checking the economy provider shows no completed transfer.
- `review-settle` requires `confirm-paid`; only use it after checking the buyer was debited and seller credited. It then atomically marks the auction SOLD and creates the buyer mailbox item.
- Stale reservations are never automatically reopened because an interrupted transaction may have moved Coins.
