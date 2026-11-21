# Remaining launch checklist — GASMP Core 0.8.0

## Added in this source milestone
- [x] Advanced team roles: Leader, Co-Leader, Officer, Member.
- [x] Team bank, Vault Coins, capacity upgrades, transfer leadership and description.
- [x] Named team homes with a configurable cap, async chunk loading and `/team homes` listing.
- [x] Symmetric alliances with invitation expiry and database-enforced per-team limits.
- [x] Casino GUI uses game-specific visual spin sequences; server outcome remains authoritative.
- [x] Original Blockbench 5 Vote and Legendary crate models with separate animated lid bones.
- [x] RP Engine content definitions for keys, static crate blocks and animated placed crate-display models.
- [x] GitHub Actions Java 25 build workflow.
- [x] Staging test plan.

## Still requires owner/server-side work
- [x] Publish the feature branch and open the 0.8.0 pull request.
- [x] Compile the 0.8.0 branch with Java 25 / Gradle in GitHub Actions.
- [ ] Install the resulting JAR on a staging Paper 26.2 server and smoke-test every command.
- [ ] Install RP Engine and verify both crate and furniture content packs reload without errors.
- [ ] Test animated placed crate models and all six furniture models on Java clients.
- [ ] Test Bedrock pack delivery, furniture seats and model fallback through the actual Geyser/Floodgate version.
- [ ] Confirm economy plugin provider and test deposits/refunds on staging.
- [ ] Verify staff restrictions with real LuckPerms Builder, Developer, Admin and Owner test accounts.
- [ ] Verify TAB primary groups and prefix rendering after reconnect.

## Not yet a full implementation
- Casino blackjack/poker/Tic-Tac-Toe are simplified quick-match simulations, not full interactive rulesets.
- Crate custom blocks are static; use the placed furniture display item for the lid animation.
- Fully customized Owner/Developer dashboards, detailed rank design and complete cinematic rank-up sequence are Phase 2.
- Bedrock custom GUI artwork and final dual-edition resource packs are not complete.
- Live server performance/load test and production release sign-off remain outstanding.
