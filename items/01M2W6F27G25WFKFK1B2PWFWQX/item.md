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

- [x] A requirement-to-test table in this ticket covers every `CUSTOMER-REQ`, `TRANSACTION-REQ` and `SHOP-REQ` id, plus `COMP-REQ-001` (`TEST-REQ-001`).
- [x] `just check` green three consecutive runs, recorded with their game-test counts, on a clean checkout.
- [x] `TEST-REQ-002` (the `verifyPurePackage` deliberate-break proof, satisfied under `VC-1`/`VC-3`) is restated in the table with its ticket reference.
- [x] `TEST-REQ-003` (the mixin-coexistence game test, satisfied under `VC-4`) is restated in the table with its ticket reference.
- [ ] Kevin's client checklist done and recorded: a real village, a real table-cloth shop, a villager visibly detouring to it, arriving, trading and returning to `WORK`, and the payment box filling with price and xp nuggets.

## Requirement-to-test table

One row per id. "Test" names the class and method that proves it. "GAP, closed (VC-6)" marks a
requirement this sweep found untested and closed with a new game test, committed on this branch.
"client checklist: ..." names the matching item in `## Client checklist (Kevin)` below, for what
pathfinding/brain timing genuinely cannot prove headless. A few rows are marked "not testable
headless, ruling recorded" with the reasoning inline, per the standing principle to record an
explicit ruling rather than silently drop the requirement from the sweep.

### `SHOP`

| ID | Requirement | Test / checklist / ruling |
|---|---|---|
| `SHOP-REQ-001` | POI registered over the table-cloth block states | `ShopPoiGameTest.tableClothAppearsAndDisappearsAsPoi` |
| `SHOP-REQ-002` | Candidate only while price + goods are non-empty | `ShopViewGameTest.aWellFormedShopIsFoundAndLosesCandidacyCorrectly` |
| `SHOP-REQ-003` | Candidate only while the linked ticker's keeper is present | `ShopViewGameTest.aWellFormedShopIsFoundAndLosesCandidacyCorrectly` (ticker removed) + `ShopSearchGameTest.bothClothsOnAKeeperlessSharedTickerWaitTogether` |
| `SHOP-REQ-004` | Search restricted to POIs within the village; no distance beyond vanilla's own | Radius restriction (a shop beyond the given radius is excluded, not merely untried): **GAP, closed (VC-6)** — `ShopSearchGameTest.aShopBeyondTheSearchRadiusIsInvisibleToTheSearch`. The narrower claim that `ShopSearch.VILLAGE_REACH` (48) exactly equals vanilla's `AcquirePoi.SCAN_RANGE` is a one-time `javap` proof documented in `ShopSearch`'s own Javadoc (VC-2) — not re-automated, the same pattern `TEST-REQ-002` already uses for `verifyPurePackage`'s deliberate-break proof; a Minecraft version bump is the only thing that could silently invalidate it, and `just doctor`'s toolchain-floor check would already flag that upgrade for review. |
| `SHOP-REQ-005` | No screen, block or item of the mod's own | Not testable headless in any meaningful sense — a "we didn't add a class" claim. Verified by repository structure (`docs/map.md` lists every type; no `Screen`/`AbstractContainerMenu`/`Block`/`Item` subclass exists under `src/main`) and by code review at every ticket, same as VC-1's "empty mixins array" claim. |
| `SHOP-REQ-006` | A candidate losing shophood before arrival defers to the cancel path | `ShoppingTripGameTest.aShopRemovedMidWalkCancelsTheTripClearsTheMemoryAndStartsCooldown` (`UC-007`) |
| `SHOP-FAIL-001` | Table cloth outside village reach is invisible, not a bug | Same test as `SHOP-REQ-004`'s radius-exclusion gap: `ShopSearchGameTest.aShopBeyondTheSearchRadiusIsInvisibleToTheSearch` |
| `SHOP-FAIL-002` | Keeper absent → not a candidate | Same as `SHOP-REQ-003` |
| `SHOP-FAIL-003` | Table cloth removed/unlinked mid-trip | Same as `SHOP-REQ-006` |
| `SHOP-FAIL-004` | Multiple candidates matching one offer → nearest wins | `ShopSearchGameTest.nearestShopComesFirst` (`SHOP-DEC-001`) |

### `TRANSACTION`

| ID | Requirement | Test / checklist / ruling |
|---|---|---|
| `TRANSACTION-REQ-001` | Match rule: goods = cost, price = result | `MatchRuleTest` (all six shape cases) + `ShopViewGameTest.aRealShopExecutesATransactionAsShopAccess` (real integration) |
| `TRANSACTION-REQ-002` | Stock checked before a unit executes | `RefusalGameTest.stockTooLowRefusesTheUnitAndMovesNothing` + `RefusalGameTest.aShortDrawReturnsEveryStackToItsOwnChestAndMovesNothing` |
| `TRANSACTION-REQ-003` | Payment-box capacity checked before a unit executes | `RefusalGameTest.aFullPaymentBoxRefusesTheUnitAndMovesNothing` |
| `TRANSACTION-REQ-004` | Direct draw from the network's stock | `TransactionGameTest.aMatchedOfferDrawsPaysAndAdvancesUsesOneVisitAtATime` + `RefusalGameTest.aShortDrawReturnsEveryStackToItsOwnChestAndMovesNothing` (rollback on a short draw) |
| `TRANSACTION-REQ-005` | Price + xp nuggets inserted into the payment box | `TransactionGameTest.aMatchedOfferDrawsPaysAndAdvancesUsesOneVisitAtATime` |
| `TRANSACTION-REQ-006` | `increaseUses()`/trade xp granted exactly once per unit | `TransactionGameTest.aMatchedOfferDrawsPaysAndAdvancesUsesOneVisitAtATime` |
| `TRANSACTION-REQ-007` | Repeats per visit until uses/stock/box runs out or a unit is refused | `TransactionGameTest.aMatchedOfferDrawsPaysAndAdvancesUsesOneVisitAtATime` (two visits) + `RefusalGameTest`'s three refusal tests |
| `TRANSACTION-REQ-008` | An offer with a second cost item never matches | `MatchRuleTest.anOfferWithASecondCostNeverMatchesRegardlessOfTheFirst` + `RefusalGameTest.anOfferWithASecondCostNeverMatchesAndMovesNothing` |
| `TRANSACTION-REQ-009` | Demand and player reputation stay untouched | `TransactionGameTest.aMatchedOfferDrawsPaysAndAdvancesUsesOneVisitAtATime` (explicit before/after assertions, non-zero starting reputation) |
| `TRANSACTION-REQ-010` | XP-nugget conversion, rounded up | `NuggetConversionTest` (five arithmetic cases) + `TransactionGameTest.aMatchedOfferDrawsPaysAndAdvancesUsesOneVisitAtATime` (real box count) |
| `TRANSACTION-FAIL-001` | Offer with a second cost item | Same as `TRANSACTION-REQ-008` |
| `TRANSACTION-FAIL-002` | Stock too low mid-loop | `RefusalGameTest.stockTooLowRefusesTheUnitAndMovesNothing` + `TransactionGameTest.aMatchedOfferDrawsPaysAndAdvancesUsesOneVisitAtATime` (loop stops once stock is gone) |
| `TRANSACTION-FAIL-003` | Payment box full mid-loop | `RefusalGameTest.aFullPaymentBoxRefusesTheUnitAndMovesNothing` |
| `TRANSACTION-FAIL-004` | Keeper absent → not a candidate, never reached here | Caught by `SHOP-REQ-003`'s tests, per `docs/spec/domains/transaction.md`'s own note that this is "caught by `domains/shop.md`, not reached here" |

### `CUSTOMER`

| ID | Requirement | Test / checklist / ruling |
|---|---|---|
| `CUSTOMER-REQ-001` | Trip behaviour only runs while `Activity.WORK` | `ShoppingTripGameTest.aVillagerNotInWorkAtNightDoesNotStartATripEvenWithAForcedRoll` (negative) + `ShoppingTripGameTest.aVillagerInWorkWalksToAMatchingShopAndTrades` (positive) + `DebugCommandGameTest.tripEndsWithThePaymentBoxHoldingThePriceWithinTheTimeout` (forces `WORK`, proving the "forced" claim directly) |
| `CUSTOMER-REQ-002` | One chance roll per restock, default 50% | `CustomerRulesTest.rollsBelowTheChanceSucceed`/`rollsAtOrAboveTheChanceFail` (pure boundary) + every forced-roll game test |
| `CUSTOMER-REQ-003` | On success, search and remember the nearest matching shop | `ShoppingTripGameTest.aVillagerInWorkWalksToAMatchingShopAndTrades` (trip-target memory set) + `DebugCommandGameTest.searchPrintsAMatchAgainstARealShop` (read-only search) |
| `CUSTOMER-REQ-004` | Walk to the target, arrive within 2 blocks, give up after 2400 ticks | Arrival distance: `ShoppingTripGameTest.aVillagerInWorkWalksToAMatchingShopAndTrades` (a real walk that must close to within `CustomerRules.ARRIVAL_DISTANCE` to trade at all). The 2400-tick give-up itself: **not testable headless, ruling recorded** — it is the base `Behavior` class's own inherited `minDuration`/`maxDuration` machinery (`ShoppingTripBehavior`'s own Javadoc), not this mod's logic, and a game test that actually ran 2400 ticks would be far slower than the rest of the suite combined (current tests finish in ~1.2s total) for a vanilla mechanism already exercised the same way by every other mod using `Behavior`. Verified by code review, not by test. |
| `CUSTOMER-REQ-005` | Arrival hands off to `TRANSACTION` and clears the trip memory | **GAP, closed (VC-6)** — the handoff (payment box filled) was already tested, but not the memory clear; `ShoppingTripGameTest.aVillagerInWorkWalksToAMatchingShopAndTrades` now asserts both in the same `succeedWhen` |
| `CUSTOMER-REQ-006` | Panicking or leaving `WORK` cancels the trip | Leaving `WORK` *before* a trip starts: `ShoppingTripGameTest.aVillagerNotInWorkAtNightDoesNotStartATripEvenWithAForcedRoll`. Panicking or leaving `WORK` *while a trip is already walking*: **not testable headless, ruling recorded** — forcing `Activity.PANIC` or `Activity.REST` mid-walk risks fighting vanilla's own `UpdateActivityFromSchedule`, which re-asserts the schedule's activity every tick during the day; this ticket did not verify whether panic is exempt from that override without real risk of a flaky test, and the standing principle is to stop and record rather than guess. The `isEligible()` guard (`!brain.isActive(WORK) \|\| brain.isActive(PANIC)`) is symmetric with the already-tested "not in WORK" and "lost shophood" cancellation paths and was verified by code review. |
| `CUSTOMER-REQ-007` | Timeout or shophood loss → cancel, clear memory, start cooldown | Shophood loss: `ShoppingTripGameTest.aShopRemovedMidWalkCancelsTheTripClearsTheMemoryAndStartsCooldown` (`UC-007`, asserts memory cleared + cooldown started + no partial transaction). Timeout: same ruling as `CUSTOMER-REQ-004`. |
| `CUSTOMER-REQ-008` | At most one active trip at a time | **GAP, closed (VC-6)** — `ShoppingTripGameTest.aSecondForcedRollWhileATripIsAlreadyActiveDoesNotReplaceTheTarget` |
| `CUSTOMER-REQ-009` | No eligible offer, or no matching shop → idle, no error, no retry | **GAP, closed (VC-6)** — `DebugCommandGameTest.searchReportsNoOfferWithUsesLeft` + `DebugCommandGameTest.searchReportsNoShopMatchingOffer` |
| `CUSTOMER-FAIL-001` | Target removed/unlinked mid-walk | Same as `CUSTOMER-REQ-007` |
| `CUSTOMER-FAIL-002` | Offer runs out of uses before arrival → nothing executes, no error | **GAP, closed (VC-6)** — `ShoppingTripGameTest.anOfferExhaustedMidWalkExecutesNothingOnArrival` |
| `CUSTOMER-FAIL-003` | Panic or night falls mid-walk → cancelled, no partial transaction | Same ruling as `CUSTOMER-REQ-006`'s mid-walk case |
| `CUSTOMER-FAIL-004` | No matching shop on the roll → idle | Same as `CUSTOMER-REQ-009` |

### `COMP` and `TEST-REQ`

| ID | Requirement | Test / checklist / ruling |
|---|---|---|
| `COMP-REQ-001` | No network call of the mod's own | `SourceSurfaceTest.noNetworkingTypeIsReferencedByTheMod` (`VC-1`) |
| `TEST-REQ-001` | Every `CUSTOMER-REQ`/`TRANSACTION-REQ`/`SHOP-REQ` names its test | This table, `VC-6` |
| `TEST-REQ-002` | Deliberate-break proof for `verifyPurePackage` | Satisfied under `VC-1`: a deliberate `import net.minecraft...` added to `villager_customers.model` and shown to fail the Gradle `verifyPurePackage` task, then reverted — a one-time hand proof, not a persisting test (a break that stayed would fail every future `just check`, which is the point). Restated here per `TEST-REQ-002`. |
| `TEST-REQ-003` | Mixin coexists with a second, independently added `WORK` behaviour | Satisfied under `VC-4`: `ShoppingTripGameTest.theMixinCoexistsWithASecondIndependentlyAddedWorkBehaviour` (`ARCH-FAIL-004`). Restated here per `TEST-REQ-003`. |

**Gaps found and closed on this branch (5):** `CUSTOMER-REQ-008`, `CUSTOMER-FAIL-002`,
`CUSTOMER-REQ-009`/`CUSTOMER-FAIL-004` (two tests), `SHOP-REQ-004`/`SHOP-FAIL-001`,
`CUSTOMER-REQ-005` (strengthened an existing test rather than adding a new one). One review finding
along the way: the new `CUSTOMER-REQ-009`/`CUSTOMER-FAIL-004` "no shop" test's first attempt used
the same wheat-for-emerald offer shape most of this suite's shops share, and matched a neighbouring
game-test structure's shop within the production 48-block search radius instead of finding none —
caught by running `just check`, not by inspection; fixed by giving that test (and the
`CUSTOMER-FAIL-002` test, which had the same latent risk) an offer shape no other shop in the suite
uses.

**Gaps found and *not* closed, with the ruling recorded above rather than silently dropped (3):**
the 2400-tick walk-timeout half of `CUSTOMER-REQ-004`/`CUSTOMER-REQ-007`, the mid-walk
panic/leave-`WORK` half of `CUSTOMER-REQ-006`/`CUSTOMER-FAIL-003`, and `SHOP-REQ-004`'s exact
vanilla-constant-equivalence claim (its radius-exclusion behaviour itself *is* closed).

## Constraints and prior findings

`operations/testing.md` `TEST-REQ-001..003`; its "What is genuinely hard here, stated plainly" note: the mod's core behaviour lives inside a real `Villager`'s brain and Create Fly's real block entities, so game tests prove the search, match and transaction correct in isolation, but only a human watching `just client` can confirm the whole trip looks right end to end — this ticket's client checklist is that confirmation, not an optional extra. Blocked by `VC-4` — every requirement domain (`SHOP`, `TRANSACTION`, `CUSTOMER`) must exist and be individually tested before the sweep table and the end-to-end client check are meaningful.

## Verification runs

Five new/strengthened game tests committed on `feature/vc-6-test-sweep` (`71c03c1`, pushed), closing
the gaps the table above marks `GAP, closed (VC-6)`; `just map` re-run (12 files current). Three
consecutive `just check` runs on a clean checkout (`./gradlew clean` between each), no flake:

| Run | `./gradlew test` (unit) | `tools` tests | Game tests | Wall clock |
|---|---|---|---|---|
| 1 | `BUILD SUCCESSFUL` | `Ran 4 tests ... OK` | `All 24 required tests passed :)` | 11.01s |
| 2 | `BUILD SUCCESSFUL` | `Ran 4 tests ... OK` | `All 24 required tests passed :)` | 10.34s |
| 3 | `BUILD SUCCESSFUL` | `Ran 4 tests ... OK` | `All 24 required tests passed :)` | 9.68s |

24 is the full game-test count after this ticket's additions (18 before `VC-6`, +6 new `@GameTest`
methods across `DebugCommandGameTest`, `ShopSearchGameTest` and `ShoppingTripGameTest`; one existing
test — `ShoppingTripGameTest.aVillagerInWorkWalksToAMatchingShopAndTrades` — was strengthened in
place for `CUSTOMER-REQ-005`, not counted as new). `just doctor` also clean (toolchain floors met,
`docs/spec/` identical to the vault, `docs/map.md` current) and `just lint` (`verifyPurePackage`
included) green on every run.

## Client checklist (Kevin)

Pathfinding and brain timing are not meaningfully testable headless (`operations/testing.md`'s own
"What is genuinely hard here" note); this is the load-bearing confirmation the game tests above
cannot give. Unticked — for Kevin, on `just client` with Create Fly:

- [ ] A real village during working hours (villagers in `WORK`, not `REST`/`PANIC`).
- [ ] A table-cloth shop with a price and a request set, its stock ticker with a keeper present, and stock in the network.
- [ ] `/villager_customers debug search @e[type=villager,limit=1,sort=nearest]` shows a match.
- [ ] `/villager_customers debug trip ...` and watching: the villager detours, arrives within 2 blocks, the payment box fills with the price and experience nuggets, and the villager returns to its work.
- [ ] A table cloth removed mid-walk cancels the trip (the villager stops detouring and returns to work).
- [ ] At night, no trip starts.
- [ ] A second shop further away is ignored while the nearer one matches.
