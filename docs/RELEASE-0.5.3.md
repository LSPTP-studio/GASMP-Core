# GASMP Core 0.5.3 — Coins shop reliability and catalogue UX

## Changes
- Shop categories from `shop.yml` are now real filters in the GUI.
- The `/gasmp` main-menu Shop button opens the live Vault-backed catalogue.
- Category filters, item pagination, confirmation, cancel and return-to-menu controls are connected.
- Purchase confirmation re-reads the current catalogue entry and re-checks item permission.
- Shop prices are normalized to two decimal places and invalid/non-finite values are rejected.
- Item delivery is staged before the Vault withdrawal. If the provider rejects or reports a failed withdrawal, the player's inventory snapshot is restored so there is no free item.
- An ambiguous provider exception triggers a conservative balance-delta check; when a full matching debit is observed, GASMP attempts a compensating deposit.
- Added persistent SQLite `gasmp_shop_transactions` audit rows for success, insufficient funds, no-space and failed/ambiguous purchase outcomes.
- Added an in-flight per-player purchase lock to prevent double-click duplicate purchases.
- Updated the fallback Shop page copy so it no longer says the catalogue is not connected.

## Limits
Vault Economy is synchronous and third-party providers are not transactional. GASMP can restore inventory on a reported failure and attempt compensation when a thrown provider call visibly deducted the full amount, but it cannot guarantee exactly-once settlement from a broken economy provider. Use a reputable Vault-compatible economy plugin and review the audit table if the provider throws errors.

## Validation
- Source archive structure checked.
- Resource YAML syntax checked.
- Shop catalogue validation script checked all configured item materials, quantities, categories and positive finite prices.
- Java compilation / Paper integration testing is still pending; this build environment does not have JDK 25 + Gradle.
