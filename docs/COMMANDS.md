# GASMP Core commands

The command descriptor is the definitive list of registered commands. Commands are subject to module toggles and permission/group checks.

## Player commands

| Command | Purpose |
|---|---|
| `/gasmp` | Opens the current GASMP menu framework. |
| `/shards [balance]` | View your Shards balance. |
| `/rtp` | Requests a safe random teleport. |
| `/bounty place <player> <amount>` | Place a Coins bounty on an online player. |
| `/bounty top` | View active bounties. |
| `/ah list [page]` | Browse auction listings. |
| `/ah sell <price>` | List the item held in your main hand. |
| `/ah buy <id>` | Buy a listing. Purchased item goes to the persistent mailbox. |
| `/ah cancel <id>` | Cancel your own active listing. Returned item goes to your mailbox. |
| `/ah mine` | List your active listings. |
| `/ah collect` | Collect pending auction items. |
| `/ah review` | Owner-only queue of ambiguous auction payments. |
| `/ah review-release <id> confirm-unpaid` | Owner-only; reopen a reviewed listing only after verifying that no Coins transfer was applied. Expired listings return to the seller mailbox. |
| `/ah review-settle <id> confirm-paid` | Owner-only; mark a reviewed sale as complete and place its item in the buyer mailbox after verifying both buyer debit and seller credit. |
| `/team create <name>` | Create a team. |
| `/team invite <player>` / `/team accept` | Invite and join a team. |
| `/team leave` / `/team kick <player>` / `/team disband` | Team membership management. |
| `/team info` / `/team chat <message>` | Team information and chat. |
| `/team promote <player>` / `/team demote <player>` / `/team transfer <player>` | Team role hierarchy and leadership transfer. |
| `/team description <text>` | Set the team description. |
| `/team bank` / `/team deposit <amount>` / `/team withdraw <amount>` / `/team upgrade` | Shared Coins bank and member capacity upgrades. |
| `/team sethome [name]` / `/team home [name]` / `/team homes` | Named team homes and home listing. |
| `/team ally <team>` / `/team allyaccept` / `/team unally <team>` / `/team allies` | Symmetric alliances with configurable limits. |
| `/battlepass` / `/battlepass claim <level>` | View progress and claim milestone rewards. |
| `/casino menu` / `/casino odds` / `/casino <game> ...` | Animated game-themed spin UI and virtual-Coins games; disabled by default. Poker/Tic-Tac-Toe remain simplified quick-match simulations. |

## Staff and administration commands

| Command | Purpose |
|---|---|
| `/gasmpcore status` | Core status snapshot. |
| `/gasmpcore reload` | Reloads supported Core configuration files. |
| `/gasmpadmin [online-player]` | Rank panel; restricted to configured Developer/Owner groups. |
| `/shards give|take|set <player> <amount> [reason]` | Staff Shards balance adjustment. |
| `/gvanish` | Toggle GASMP staff vanish. |
| `/crates list|set|unset|givekey|giveblock|givemodel|reload` | Owner/Developer crate management; `/crates givemodel` gives the animated RP Engine display item. Players use physical keys at configured crate blocks. |
| `/gmaintenance on|off|status|reload` | Maintenance mode control. Console can also operate it. |
| `/gban`, `/gunban`, `/gmute`, `/gunmute`, `/gwarn`, `/gkick`, `/ghistory` | Moderation and history. |
| `/gholo create|set|delete|list|reload` | Basic persistent text-display hologram management. |
| `/gchatgame start|stop|status|reload` | Chat-game management. |
| `/gtop <kills|deaths|playtime|shards>` | Basic leaderboards. |
| `/gperf` | Lightweight server diagnostic snapshot. |

## Third-party restricted commands

The Core restriction listener blocks configured third-party command roots for players outside the relevant LuckPerms groups. The server must still have the third-party plugin installed for the command itself to exist. See `docs/PERMISSIONS.md`.
