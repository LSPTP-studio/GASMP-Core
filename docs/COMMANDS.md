# GASMP Core command reference — 0.8.3

Commands are registered in src/main/resources/plugin.yml. Actual access is also controlled by module settings and service-level permission/group checks. Run /gasmp for the player menu and /crates help for crate-specific help.

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
| /team invite <player>, /team accept | Invite a player or accept an invitation. |
| /team leave, /team kick <player>, /team disband | Manage team membership. |
| /team info, /team chat <message> | View team information or send team chat. |
| /team promote <player>, /team demote <player>, /team transfer <player> | Manage team roles and leadership. |
| /team description <text> | Set the team description. |
| /team bank, /team deposit <amount>, /team withdraw <amount>, /team upgrade | Use the team Coins bank and capacity upgrades. |
| /team sethome [name], /team home [name], /team homes | Manage named team homes. |
| /team ally <team>, /team allyaccept, /team unally <team>, /team allies | Manage team alliances. |
| /battlepass, /battlepass claim <level> | View Battle Pass progress and claim a level reward. |
| /casino menu, /casino odds | Open the Shards Arcade or view configured odds. |
| /casino <game> ... | Play one of the configured Shards Arcade games. |
| /casino challenge <player> <game> <shards> | Invite a player to a Shards-staked duel. |
| /casino accept, /casino decline, /casino duel | Accept, decline or reopen an Arcade duel. |
| /gvanish | Toggle staff vanish when permitted. |

## Cosmetic crate commands

All six crate tiers are cosmetic-only. Each has its own display name, matching ItemsAdder key, placeable crate furniture, model and reward pool.

| ID | Crate name | Key name |
|---|---|---|
| normal | VERDANT CACHE | VERDANT SEAL |
| medium | RUNIC RELIQUARY | RUNIC SIGIL |
| premium | ECLIPSE VAULT | ECLIPSE CREST |
| aether | AETHER SANCTUM | AETHER SIGIL |
| tidal | TIDAL TROVE | TIDAL TOKEN |
| obsidian | OBSIDIAN RELIC | OBSIDIAN SEAL |

| Command | Purpose |
|---|---|
| /crates list | List every configured crate, matching key, reward count and location status. |
| /crates info <crate-id> | Show crate name, key, Shards price, reward count and legacy location status. |
| /crates preview <crate-id> | Open the paged reward preview with weighted chances. Player-only. |
| /crates givekey <player> <crate-id> [amount] | Give the matching ItemsAdder key. |
| /crates giveblock <player> <crate-id> [amount] | Give the matching placeable ItemsAdder crate furniture. |
| /crates givebundle <player> <crate-id> [amount] | Give both the placeable crate and its matching key. |
| /crates givemodel <player> <crate-id> [amount] | Compatibility alias for giveblock. |
| /crates set <crate-id> | Set a legacy vanilla chest/barrel crate location while looking at it. |
| /crates unset <crate-id> | Remove a legacy vanilla crate location. |
| /crates reload | Reload crate definitions. |
| /keyall <crate-id|all> <amount> [world:<world>] [silent] | Distribute matching keys to online players, optionally filtered by world. |

Crate administration is restricted to Owner/Developer staff by the service checks. The server console can run givekey, giveblock, givemodel, givebundle, info, list, reload and keyall. The console cannot open the player preview GUI.

### Player crate interactions

- Right-click a placed crate while holding its matching key in either hand.
- Sneak + left-click a configured vanilla crate block to preview its reward pool.
- Right-click a placed ItemsAdder crate furniture model to open it.
- Sneak + punch a placed ItemsAdder crate model to preview the pool.
- Normal furniture mining/pickup remains handled by ItemsAdder.

The opening GUI uses a decelerating three-slot roulette, tier-colored frame materials, themed particles, staged titles and sounds, a final reward reveal and burst particles. The key is consumed only at the final reveal; closing or disconnecting before then cancels the opening without consuming the key.

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

/gcrateopen <player> <normal|medium|premium|aether|tidal|obsidian> is an internal console-only crate-opening dispatcher retained for integrations. Player senders are rejected by its handler; do not expose it as a player command.

## ItemsAdder setup and custom menu UI

1. Copy the gasmp and gasmp_ranks folders into plugins/ItemsAdder/contents/.
2. Run /iareload and check the console for YAML/model/font-image errors.
3. Run /iazip to regenerate the resource pack.
4. Ensure the ItemsAdder resource pack is hosted and pushed to players.
5. Reconnect and accept the updated pack. Clients using an older cached pack or declining the pack will not see the custom UI panels or custom models.

The custom menu titles reference the font-image aliases in gasmp/configs/ui.yml; the corresponding PNG files are in gasmp/textures/font/. ItemsAdder uses font-image placeholders such as :offset_-8::ui_main:.