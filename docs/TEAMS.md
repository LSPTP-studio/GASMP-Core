# GASMP Teams — Phase 1 feature guide

Teams store their membership, role, bank, capacity, descriptions, homes and alliances in the GASMP SQLite database. The Coins bank requires a working Vault Economy provider.

## Roles

- **Leader:** full team authority, can transfer leadership and disband.
- **Co-Leader:** invitations, moderation of lower roles, bank withdrawals when allowed by config, and team administration.
- **Officer:** invitations, member moderation below their role, description updates, and home setting when enabled.
- **Member:** join team chat, view team information, bank balance, team homes and allies.

A role cannot kick or demote a member with equal or higher authority. The leader cannot leave without transferring leadership or disbanding.

## Commands

| Command | Purpose |
|---|---|
| `/team create <name>` | Create a team |
| `/team invite <player>` / `/team accept` | Invite and join |
| `/team info` | Show members, capacity, bank and description |
| `/team chat <message>` | Team-only chat |
| `/team promote <player>` / `/team demote <player>` | Change a lower member's role |
| `/team transfer <player>` | Transfer leadership |
| `/team kick <player>` / `/team leave` | Remove or leave |
| `/team description <text>` | Change description |
| `/team bank` / `/team deposit <amount>` / `/team withdraw <amount>` | Team Coins bank |
| `/team upgrade` | Buy capacity using team-bank Coins |
| `/team sethome [name]` | Save a home |
| `/team home [name]` | Teleport to a saved home (not while combat-tagged) |
| `/team homes` | List saved home locations |
| `/team ally <team>` / `/team allyaccept` | Request/accept alliance |
| `/team unally <team>` / `/team allies` | End/list alliances |
| `/team disband` | Disband as leader |

## Defaults

- Starting capacity: 8 members.
- Capacity upgrade: 5,000 Coins for 2 slots.
- Maximum capacity: 100.
- Homes: 3 per team.
- Alliances: 5 per team.
- Invites and alliance requests expire after 60 seconds.
- Only `LEADER` and `COLEADER` can withdraw from the team bank by default.
- `LEADER`, `COLEADER`, and `OFFICER` can set homes by default.

All limits are configured in `config.yml` under `teams:`. Team-bank Coins are separate from player Coins. Bank transactions compare the Vault balance before/after and use conservative recovery: uncertain provider results are logged for Owner review instead of blindly refunding/crediting an amount that might duplicate Coins.

## Notes

- Homes are stored as world name, coordinates, yaw and pitch. The destination world must be loaded and the destination chunk is loaded asynchronously before teleport.
- Invitations are in-memory and expire on server restart. Membership and team data persist.
- Custom team menus, team upgrades beyond capacity, team missions, war/friendly-fire systems, and team cosmetics are planned for later UI/gameplay milestones.
