# Coins / Vault Economy integration

GASMP Core does not create a second Coins balance. It asks Bukkit's ServicesManager for the registered Vault `Economy` service and forwards balance, has, deposit, and withdrawal calls to that provider.

This works with economy plugins that register Vault's Economy service. A plugin with a proprietary economy API and no Vault bridge needs a separate adapter; no universal API can directly operate every unrelated economy.

Shards remain an independent GASMP-owned currency in SQLite. Shards do not automatically convert to Coins.
