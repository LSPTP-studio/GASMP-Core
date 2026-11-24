# GASMP Core command reference

Commands are registered in src/main/resources/plugin.yml. Actual access is also controlled by module settings and service-level permission/group checks.

## Player commands

| Command | Purpose |
|---|---|
| /gasmp | Open the GASMP player menu. Aliases: /menu and /gasmpmenu. |
| /shards or /shards balance | View your Shards balance. |
| /shop | Open the Coins/Shards shop selector. |
| /shop coins | Open the Vault Coins catalogue. |
| /shop shards | Open the Shards key and decoration shop. |
| /rtp | Request a safe random teleport. |
| /bounty place <player> <amount> | Place a Coins bounty on an online player. |
| /bounty top | View active bounties. |
| /ah list [page] | Browse auction listings. |
| /ah sell <price> | List the item held in your main hand. |
| /ah buy <id> | Buy a listing; the item goes to your mailbox. |
| /ah cancel <id> | Cancel your own listing; the item returns to your mailbox. |
| /ah mine | List your active listings. |
| /ah collect | Collect pending auction items. |
| /team create <name> | Create a team. |
| /team invite <player> and /team accept | Invite and join a team. |
| /team leave, /team kick <player>, /team disband | Manage team membership. |
| /team info and /team chat <message> | View team information or send team chat. |
| /team promote <player>, /team demote <player>, /team transfer <player> | Manage team roles and leadership. |
| /team description <text> | Set the team description. |
| /team bank, /team deposit <amount>, /team withdraw <amount>, /team upgrade | Use the team Coins bank and capacity upgrades. |
| /team sethome [name], /team home [name], /team homes | Manage named team homes. |
| /team ally <team>, /team allyaccept, /team unally <team>, /team allies | Manage team alliances. |
| /battlepass and /battlepass claim <level> | View Battle Pass progress and claim a level reward. |
| /casino menu and /casino odds | Open the Shards Arcade or view configured odds. |
| /casino <game> ... | Play one of the configured Shards Arcade games. |
| /casino challenge <player> <game> <shards> | Invite a player to a Shards-staked duel. |
| /casino accept, /casino decline, /casino duel | Accept, decline or reopen an Arcade duel. |
| /gvanish | Toggle staff vanish when permitted. |

## Cosmetic crate commands

Normal, Medium and Premium are cosmetic crate tiers, not equipment tiers.

| Command | Purpose |
|---|---|
| /crates list | List configured crate tiers and legacy location status. |
| /crates help | Display crate administration help. |
| /crates givekey <player> <normal|medium|premium> [amount] | Give a matching ItemsAdder crate key. |
| /crates giveblock <player> <normal|medium|premium> [amount] | Give the matching placeable ItemsAdder crate furniture item. |
| /crates givemodel <player> <normal|medium|premium> [amount] | Compatibility alias for giveblock. |
| /crates set <normal|medium|premium> | Set a legacy vanilla chest/barrel crate location while looking at it. |
| /crates unset <normal|medium|premium> | Remove a legacy vanilla crate location. |
| /crates reload | Reload the GASMP crate definitions. |
| /keyall <crate-id|all> <amount> [world:<world>] [silent] | Distribute keys to online players, optionally filtered by world. |

Crate administration is restricted to Owner/Developer staff by the service checks. The server console can run givekey, giveblock, givemodel, list, reload and keyall commands.

### Player crate interactions

- Right-click a placed crate while holding the matching key in either hand.
- Sneak + left-click a configured vanilla crate block to preview its reward pool.
- Right-click a placed ItemsAdder crate furniture model to open it.
- Sneak + punch a placed ItemsAdder crate model to preview the pool.
- Ordinary furniture mining/punching is left to ItemsAdder's furniture handler for pickup.

The existing GUI animation uses a decelerating three-slot roulette, staged titles and sounds, a reward reveal and burst particles. The key is consumed only after the final reveal is committed; closing or disconnecting before then cancels the opening without consuming it.

## Staff and administration commands

| Command | Purpose |
|---|---|
| /gasmpcore status | Show Core status. |
| /gasmpcore reload | Reload supported Core configuration files. |
| /gasmpadmin [online-player] | Open the restricted rank administration panel. |
| /shards give <player> <amount> [reason] | Give Shards. |
| /shards take <player> <amount> [reason] | Remove Shards. |
| /shards set <player> <amount> [reason] | Set a Shards balance. |
| /gmaintenance on|off|status|reload | Control maintenance mode. |
| /gban <player> <duration|perm> <reason>, /gunban <player> | Ban or unban a player. |
| /gmute <player> <duration> <reason>, /gunmute <player> | Mute or unmute a player. |
| /gwarn <player> <reason> | Record a warning. |
| /gkick <player> <reason> | Kick a player. |
| /ghistory <player> | View moderation history. |
| /gholo create|set|delete|list|reload | Manage persistent text holograms. |
| /gchatgame start|stop|status|reload | Manage chat games. |
| /gtop <kills|deaths|playtime|shards> | View leaderboards. |
| /gperf | View a lightweight server diagnostic snapshot. |

## Internal dispatcher

/gcrateopen <player> <normal|medium|premium> is an internal console-only crate-opening dispatcher retained for integrations. Player senders are rejected by its handler; do not expose it as a player command.

## ItemsAdder setup commands

1. /iareload — load the GASMP content definitions.
2. Check the console for any YAML or model errors.
3. /iazip — regenerate the resource pack.
4. Refresh/resend the pack using the ItemsAdder hosting method configured on your server.
