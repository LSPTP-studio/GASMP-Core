# GASMP launch test plan — Paper 26.2

Do not install an unverified build on the live server. Make a separate staging server with a copy of the world and plugin configs.

## Build gate

- [ ] JDK 25 available to Gradle.
- [ ] `gradle clean build` succeeds.
- [ ] No `Could not load` or missing dependency errors on boot.
- [ ] SQLite file is created and can be reopened after restart.
- [ ] Vault registers the intended Economy provider.
- [ ] LuckPerms and TAB both load before GASMP Core.

## Economy and data

- [ ] Shards balance persists across reconnect/restart.
- [ ] Shop success, not-enough-balance and full-inventory paths.
- [ ] Auction list/buy/cancel/collect paths with buyer and seller reconnects.
- [ ] Auction stale reservation enters Owner review and is not reopened automatically.
- [ ] Bounty placing, partial aggregation, kill claim and disconnect cases.
- [ ] Team deposit/withdraw, capacity upgrade and simultaneous withdrawals.

## Teams

- [ ] Create, invite, expiry, accept and full-capacity edge cases.
- [ ] Role hierarchy: Leader > Co-Leader > Officer > Member.
- [ ] Kicking equal/higher role is blocked.
- [ ] Leadership transfer and disband cascades.
- [ ] Team homes maximum, homes list, unloaded world/chunk and combat-tag rejection.
- [ ] Alliance request expiry, duplicate alliance and both teams' limits.
- [ ] Bank provider errors are logged and not silently duplicated.

## Crates and RP Engine

- [ ] Both Blockbench models import without errors.
- [ ] Both crate `open` animations rotate the lid around the hinge.
- [ ] RP Engine content reload produces no YAML/model errors.
- [ ] Keys decrement exactly once per successful open.
- [ ] Crate animation/menu cannot be double-triggered.
- [ ] Player/command-block cannot invoke internal `gcrateopen` command.
- [ ] Only Owner/Developer can edit crates or break protected crate blocks.
- [ ] Bedrock clients receive the RP Engine/Geyser pack and have a usable vanilla fallback.

## Java/Bedrock and staff

- [ ] Java client: connect, menu, shop, team and crate paths.
- [ ] Bedrock client: connect, inventory actions, fallback icons and crate display.
- [ ] Developer, Owner, Admin, Builder and ordinary-player permission matrices.
- [ ] Maintenance join bypass and MOTD behavior.
- [ ] Vanish visibility to players, staff and newly joining players.
- [ ] TAB prefixes/primary groups after rank changes and reconnect.

## Performance and backups

- [ ] Profile/chunk searches do not run on main thread.
- [ ] No repeating task per player without a bounded lifecycle.
- [ ] Test 20+ concurrent players on staging.
- [ ] Check timings/spark and console for repeating exceptions.
- [ ] Back up SQLite and plugin configs before production migration.
