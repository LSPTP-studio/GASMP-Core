# Shards and profiles

Database file: `plugins/GASMP-Core/data.db`

Tables:
- `gasmp_players`: UUID, name, Shards, kills, deaths, play ticks, first/last seen timestamps
- `gasmp_shard_transactions`: actor, delta, resulting balance, reason and timestamp
- `gasmp_rank_audit`: actor, target, rank key, previous and new primary groups

Commands:
- `/shards` — view your balance
- `/shards balance <online-player>` — admin-only balance lookup
- `/shards give <online-player> <amount> [reason]`
- `/shards take <online-player> <amount> [reason]`
- `/shards set <online-player> <amount> [reason]`

Amounts are whole non-negative integers. Taking more than a player's balance is rejected. No player transfers, cash-out, or real-money purchases are enabled. SQLite operations use a dedicated single-thread executor rather than running queries on the Paper main thread.
