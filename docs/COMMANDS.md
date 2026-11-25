# GASMP Core command reference — 0.8.3

Run `/gasmp` for the custom dashboard (VoidRP UI) or native menu fallback. Use `/crates help` for crate-specific help.

## Player commands

| Command | Purpose |
|---|---|
| /gasmp | Open the custom VoidRP UI dashboard when the plugin and pack are available; otherwise open the native inventory menu. |
| /shards or /shards balance | View your Shards balance. |
| /shop | Open the Coins/Shards shop selector. |
| /shop coins | Open the Vault Coins catalogue. |
| /shop shards | Open the Shards key shop. |
| /rtp | Open the RTP world selector. |
| /bounty place <player> <amount> | Place a Coins bounty. |
| /bounty top | View active bounties. |
| /ah or /ah list [page] | Open the paged Auction House GUI. |
| /ah sell <price> | List the item held in your main hand. |
| /ah buy <id> | Buy a listing; the item goes to your mailbox. |
| /ah cancel <id> | Cancel your own listing. |
| /ah mine | List your active listings. |
| /ah collect | Collect pending auction items. |
| /team create <name> | Create a team. |
| /team invite <player>, /team accept | Invite or accept a team invitation. |
| /team leave, /team kick <player>, /team disband | Manage team membership. |
| /team info, /team chat <message> | View team information or send team chat. |
| /battlepass, /battlepass claim <level> | View Battle Pass progress and claim rewards. |
| /casino menu, /casino odds | Open the Shards Arcade or view configured odds. |
| /casino <game> ... | Play an enabled Shards Arcade game. |
| /gvanish | Toggle staff vanish when permitted. |

## Crates

| ID | Crate | Key |
|---|---|---|
| normal | VERDANT CACHE | VERDANT SEAL |
| medium | RUNIC RELIQUARY | RUNIC SIGIL |
| premium | ECLIPSE VAULT | ECLIPSE CREST |
| aether | AETHER SANCTUM | AETHER SIGIL |
| tidal | TIDAL TROVE | TIDAL TOKEN |
| obsidian | OBSIDIAN RELIC | OBSIDIAN SEAL |

| Command | Purpose |
|---|---|
| /crates list | List configured crates, keys, rewards and locations. |
| /crates info <crate-id> | Show crate details. |
| /crates preview <crate-id> | Open the paged reward preview. |
| /crates givekey <player> <crate-id> [amount] | Give a matching CraftEngine key. |
| /crates giveblock <player> <crate-id> [amount] | Give a placeable CraftEngine furniture crate. |
| /crates givebundle <player> <crate-id> [amount] | Give the crate and matching key. |
| /crates givemodel <player> <crate-id> [amount] | Alias for giveblock. |
| /crates set <crate-id> | Set a legacy vanilla chest/barrel crate location. |
| /crates unset <crate-id> | Remove a legacy vanilla crate location. |
| /crates reload | Reload crate definitions. |
| /keyall <crate-id|all> <amount> [world:<world>] [silent] | Distribute matching keys to online players. |

Right-click a placed CraftEngine crate furniture model with its matching key. Sneak + punch to preview the reward pool. CraftEngine handles furniture placement and pickup/mining.

## Staff commands

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
| /gholo create|set|delete|list|reload | Manage text holograms. |
| /gchatgame start|stop|status|reload | Manage chat games. |
| /gtop <kills|deaths|playtime|shards> | View leaderboards. |
| /gperf | View a server diagnostic snapshot. |

## CraftEngine pack

Pack folder: `plugins/CraftEngine/resources/gasmp/`.
- `/ce reload config` reloads content configuration.
- `/ce reload all` reloads the entire pack and its resource-pack data.

## Optional VoidRP UI

- `/vui screen` sets the player's screen shape.
- `/vui cursor` opens pointer settings.
- `/vui pack` sends the UI pack again.
- `/gasmp` uses the custom dashboard when VoidRP UI's pack is accepted; otherwise GASMP falls back to its native inventory menu.

VoidRP UI's pack is sent separately and stacks with CraftEngine's pack.
