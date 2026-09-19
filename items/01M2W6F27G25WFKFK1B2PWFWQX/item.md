---
schema_version: 1
id: 01M2W6F27G25WFKFK1B2PWFWQX
key: VC-6
type: test
title: Requirement-to-test table, three green just check runs, client checklist
created_by: kevin
created_at: 2026-09-19T06:43:08Z
---

## Scope

The requirement-to-test table `operations/testing.md` `TEST-REQ-001` requires: every `CUSTOMER-REQ`, `TRANSACTION-REQ` and `SHOP-REQ` mapped to the test that proves it, plus `COMP-REQ-001` (no network call, `SourceSurfaceTest`, established under `VC-1`) and the two once-only proofs `TEST-REQ-002` (the pure-package deliberate-break proof) and `TEST-REQ-003` (the mixin-coexistence game test), both already satisfied under `VC-1`/`VC-4` and only restated here. Three green `just check` runs in a row on a clean checkout, recorded with their game-test counts. Kevin's client checklist (`operations/testing.md`, "Client / Kevin's checklist" row): a real village, a real table-cloth shop, watching a villager visibly detour to it, arrive, pause convincingly, trade, and return to `WORK`, and the payment box filling with price and xp nuggets — pathfinding and brain timing are not meaningfully testable headless, so this is load-bearing here, not decorative.

## Approach

One table in this ticket, filled from the tests already written under `VC-2` through `VC-5`; where a requirement cannot be proven headless (the walk itself looking right, timing that depends on real day/night cycles), the table names the reason and points at the client checklist instead, exactly as `operations/testing.md` scopes it. `just check` run three times in a row with no flaky failure, on a clean checkout, each run's game-test count recorded here.

## Acceptance criteria

- [ ] A requirement-to-test table in this ticket covers every `CUSTOMER-REQ`, `TRANSACTION-REQ` and `SHOP-REQ` id, plus `COMP-REQ-001` (`TEST-REQ-001`).
- [ ] `just check` green three consecutive runs, recorded with their game-test counts, on a clean checkout.
- [ ] `TEST-REQ-002` (the `verifyPurePackage` deliberate-break proof, satisfied under `VC-1`/`VC-3`) is restated in the table with its ticket reference.
- [ ] `TEST-REQ-003` (the mixin-coexistence game test, satisfied under `VC-4`) is restated in the table with its ticket reference.
- [ ] Kevin's client checklist done and recorded: a real village, a real table-cloth shop, a villager visibly detouring to it, arriving, trading and returning to `WORK`, and the payment box filling with price and xp nuggets.

## Constraints and prior findings

`operations/testing.md` `TEST-REQ-001..003`; its "What is genuinely hard here, stated plainly" note: the mod's core behaviour lives inside a real `Villager`'s brain and Create Fly's real block entities, so game tests prove the search, match and transaction correct in isolation, but only a human watching `just client` can confirm the whole trip looks right end to end — this ticket's client checklist is that confirmation, not an optional extra. Blocked by `VC-4` — every requirement domain (`SHOP`, `TRANSACTION`, `CUSTOMER`) must exist and be individually tested before the sweep table and the end-to-end client check are meaningful.
