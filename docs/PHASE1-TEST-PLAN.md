# GASMP Phase 1 launch test plan

Run on a separate Paper 26.2 test server with Java 25 first. Never use a live player economy as the first integration test.

## Startup and persistence
- [ ] Plugin enables without severe startup errors.
- [ ] SQLite database is created and all tables/indexes initialize.
- [ ] Stop/start preserves Shards, auctions, bounties, Battle Pass progress, team records, moderation records and maintenance state.
- [ ] `/gasmpcore status` returns a healthy status.

## Economy / Shards
- [ ] Verify Vault resolves the intended Coins provider.
- [ ] Shards give/take/set works and a take cannot create a negative balance.
- [ ] Shop rejects insufficient funds and logs each attempt.
- [ ] Shop purchase delivers only once and reconciles an explicitly failed/ambiguous Vault response.
- [ ] Bounty placement, bounty aggregation, kill payout and failure restoration tested.

## RTP / combat
- [ ] RTP searches within configured world/radius and avoids configured hazards.
- [ ] Movement, damage/combat state and quit cancel the RTP warmup as configured.
- [ ] RTP cooldown survives relog during the same server session.
- [ ] Combat tag applies to valid PvP damage and clears on expiration/death.
- [ ] Combat logout penalty applies once; bypass groups/permission are honored.

## Auction House
- [ ] Selling removes the exact held stack and creates one active listing.
- [ ] Two players attempting to buy the same listing cannot both succeed.
- [ ] Buyer is charged once and the item appears in the buyer's mailbox.
- [ ] Seller cancellation returns the item through the mailbox.
- [ ] Expired listings return to the seller through the mailbox.
- [ ] `/ah collect` works with a full inventory and after relog.
- [ ] Test shutdown/restart at each stage; inspect the database if a delivery marker fails. Mailbox/inventory operations cannot be made fully atomic across an OS/server crash, so keep backups and logs.
- [ ] Simulate a stale RESERVED auction on startup; verify it becomes REVIEW and is NOT automatically reopened.
- [ ] Verify a non-Owner (including OP without the configured Owner group) cannot run `/ah review` or resolve a listing.
- [ ] Verify Owner `/ah review-release <id> confirm-unpaid` reopens only an unpaid listing (or returns an expired listing to seller mail).
- [ ] Verify Owner `/ah review-settle <id> confirm-paid` marks it SOLD and creates exactly one buyer mailbox row.
- [ ] Test partial-transfer recovery: manually normalize the buyer/seller balances before choosing release or settle.
- [ ] Simulate `completeAuction` reporting an error after a committed SOLD transition; confirm the service inspects the durable status before compensating Coins.

## Crates / Battle Pass / teams
- [ ] Only configured Owner/Developer can run `/crates` management.
- [ ] Invalid crate configuration does not consume a key.
- [ ] Physical key for one crate cannot open another crate.
- [ ] Crate block cannot be broken, exploded or moved by pistons.
- [ ] Each Battle Pass reward is claimable once; disconnect and full inventory scenarios tested.
- [ ] Team create/invite/accept/leave/kick/disband/info/chat tested by at least three accounts.

## Moderation / staff / maintenance
- [ ] Temp/permanent ban, unban, mute, unmute, warn, kick and history tested.
- [ ] Mute survives relog and expiry behavior is correct.
- [ ] Maintenance blocks new normal logins but permits configured bypass groups.
- [ ] Server list MOTD changes while maintenance is enabled.
- [ ] Vanished player is hidden from non-staff, visible to staff and stays hidden on later joins.
- [ ] HeadDatabase, OpenInv, Lib's Disguises, Skript and crates commands obey the staff policy.
- [ ] OpenInv inventory editing and offline inspection are not granted to staff.

## Bedrock / TAB / protection compatibility
- [ ] Java client can join and use the supported menus.
- [ ] Bedrock client can join through Geyser/Floodgate and use vanilla-fallback menus.
- [ ] TAB displays the intended LuckPerms primary group and prefix.
- [ ] Vein Miner and Tree Capitator do not bypass region protection or block-break cancellation.
- [ ] No duplicate vanish/hologram behavior from another plugin.

## Release gate

Do not enable casino or advertise the release as production-ready until all applicable tests pass. Keep the casino disabled in `config.yml` and `casino.yml` by default.
