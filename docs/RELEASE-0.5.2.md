# GASMP Core 0.5.2 — reliability pass

This release focuses on correctness in the existing RTP/combat/Shards paths.

## Fixes
- RTP warmups and asynchronous chunk searches are represented by a unique request object. Cancelled/old callback results are ignored.
- Movement cancels RTP during both warmup and chunk search, while head movement does not.
- Combat state is checked again after chunk loading and immediately before teleport.
- `avoid-water` is now read from `rtp.yml` and respected during safe-location selection.
- RTP cooldowns are retained when a player disconnects, preventing a relog from bypassing the cooldown during the same server session.
- The killer's tag is retained/refreshed on player death; the victim's tag is removed.
- Combat-bypass staff do not receive logout penalties.
- Combat logout flag consumption uses a single atomic delete operation.
- Shards transaction log `change_type` is now correctly set to GIVE/TAKE/SET.

## Validation
- YAML resources parsed successfully.
- RTP callback/request lifecycle source checks passed.
- SQLite combat-logout consume semantics tested against SQLite.
- Java compilation and Paper 26.2 live-server tests remain unverified in this environment (Java 21, Gradle unavailable).
