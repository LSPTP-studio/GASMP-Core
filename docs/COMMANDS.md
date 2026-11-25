# GASMP Core 0.8.3 — ItemsAdder 4.0.18 Edition

Run `/gasmp` to open the custom VoidRP dashboard (when available) or the native menu fallback. Run `/crates help` for crate-specific help.

## Player commands

| Command | Purpose |
|---|---|
| /gasmp | Open the custom VoidRP dashboard when installed and its pack is accepted; otherwise open the native GASMP menu. |
| /shards or /shards balance | View your Shards balance. |
| /shop | Open the Coins/Shards shop selector. |
| /shop coins | Open the Vault Coins catalogue. |
| /shop shards | Open the Shards key shop. |
| /rtp | Open the RTP world selector. |
| /bounty place <player> <amount> | Place a Coins bounty. |
| /bounty top | View active bounties. |
| /ah or /ah list [page] | Open the paged Auction House GUI. |
| /ah sell <price> | List the item held in your main hand. |
| /ah buy <id> | Buy a listing; the item goes to mailbox. |
| /ah cancel <id> | Cancel your own listing. |
| /ah mine | List your active listings. |
| /ah collect | Collect pending auction items. |
| /team create <name> | Create a team. |
| /team invite <player>, /team accept | Invite or accept a team invitation. |
| /team leave, /team kick <player>, /team disband | Manage team membership. |
| /team info, /team chat <message> | View information or send team chat. |
| /battlepass, /battlepass claim <level> | View Battle Pass progress and claim rewards. |
| /casino menu, /casino odds | Open the Shards Arcade or view configured odds. |
| /casino <game> ... | Play an enabled Shards Arcade game. |
| /gvanish | Toggle staff vanish when permitted. |

## Six cosmetic crate tiers

| ID | Crate | Matching key |
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
| /crates givekey <player> <crate-id> [amount] | Give the matching ItemsAdder key. |
| /crates giveblock <player> <crate-id> [amount] | Give a placeable ItemsAdder crate furniture item. |
| /crates givebundle <player> <crate-id> [amount] | Give the crate and matching key. |
| /crates givemodel <player> <crate-id> [amount] | Alias for giveblock. |
| /crates set <crate-id> | Set a legacy vanilla chest/barrel crate location. |
| /crates unset <crate-id> | Remove a legacy vanilla crate location. |
| /crates reload | Reload crate definitions. |
| /keyall <crate-id|all> <amount> [world:<world>] [silent] | Distribute matching keys to online players. |

Right-click a placed ItemsAdder crate furniture model with its matching key. Sneak + punch the model to preview its reward pool. ItemsAdder handles normal furniture placement and pickup.

## Staff and administration

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
| /gwarn <player> <reason> | Record a player warning. |
| /gkick <player> <reason> | Kick a player. |
| /ghistory <player> | View moderation history. |
| /gholo create|set|delete|list|reload | Manage text holograms. |
| /gchatgame start|stop|status|reload | Manage chat games. |
| /gtop <kills|deaths|playtime|shards> | View leaderboards. |
| /gperf | View a server diagnostic snapshot. |

## ItemsAdder 4.0.18 setup

1. Use the original ItemsAdder 4.0.18 plugin on Paper 26.2.
2. Copy `gasmp` and `gasmp_ranks` into `plugins/ItemsAdder/contents/`.
3. Run `/iareload` and check the console for YAML or model errors.
4. Run `/iazip` to regenerate the ItemsAdder resource pack.
5. Confirm the pack is hosted, pushed and accepted by players.

The ItemsAdder JAR belongs directly in `plugins/`, not inside `plugins/ItemsAdder/`.

## Optional VoidRP UI dashboard

- `/gasmp` uses the custom dashboard when VoidRP UI is installed and its pack is accepted.
- `/vui screen` sets the player's screen shape.
- `/vui cursor` opens pointer settings.
- `/vui pack` resends the separate UI pack.

The VoidRP UI pack is separate from the ItemsAdder pack; clients must accept both.
