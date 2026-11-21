# TAB + LuckPerms setup

1. Keep `assign-groups-by-permissions: false` in `plugins/TAB/config.yml`.
2. Merge `groups.yml` into your existing TAB `groups.yml` (do not overwrite unrelated settings).
3. GASMP Core creates/synchronizes configured LuckPerms groups, their global weights, and their text prefixes.
4. TAB should read each player's primary group from LuckPerms and display `%luckperms-prefix%` for the tab and nametag prefixes.
5. Run `/tab dump <player>` and `/tab parse <player> %luckperms-prefix%` to verify.

GASMP Core does not rely on TAB's temporary prefix API. Prefixes are saved in LuckPerms and persist across reconnects/restarts.

Weights in `ranks.yml` determine the GASMP primary rank across the player's active rank tracks. If another plugin changes the same LuckPerms group prefix or weight, the last writer can overwrite the GASMP value.
