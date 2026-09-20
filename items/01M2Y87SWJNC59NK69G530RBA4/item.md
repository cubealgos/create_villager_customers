---
schema_version: 1
id: 01M2Y87SWJNC59NK69G530RBA4
key: VC-11
type: bug
title: "A villager with a job site never takes the trip: the work package's walk target wins"
created_by: kevin
created_at: 2026-09-20T01:52:36Z
---

## Scope

A villager with a real job site does not take the trip (Kevin, 2026-09-20, client check): `debug roll` and `debug trip` set the trip target, the villager stayed with its composter. The game tests of VC-4 used villagers without a profession or job site, so vanilla's `WORK` package never competed. The shopping trip shall win over the work package's own walk targets for as long as the trip is active: the villager leaves its job site, walks to the shop, trades, and only then returns (`CUSTOMER-REQ-004`, `CUSTOMER-REQ-005`, `CUSTOMER-REQ-001`).

## Approach

Reproduce first: a game test with a farmer (profession set, `JOB_SITE` memory pointing at a composter, `WORK` active, the vanilla work package registered through the real `registerBrainGoals`), a matching shop 12 blocks away, forced trip; assert the villager arrives and trades within the timeout. Then read vanilla's `VillagerGoalPackages.getWorkPackage` behaviours that set `WALK_TARGET` (`SetWalkTargetFromBlockMemory(JOB_SITE, ...)`, `StrollToPoi`, `StrollAroundPoi`, the `RunOne` at priority 5, `WorkAtPoi` at 2) and `MoveToTargetSink` (core), and what happens when a path fails (`CANT_REACH_WALK_TARGET_SINCE` erases `WALK_TARGET`). Fix `ShoppingTripBehavior`: re-assert `WALK_TARGET` (and `LOOK_TARGET`) on every tick while the trip is active and the villager is not yet within arrival distance, whenever the memory is absent or points elsewhere; run at a priority ahead of the work package's walk-target setters if the ordering matters (record the slot decision); declare `WALK_TARGET` in the behaviour's memory map only if that does not block the start (`REGISTERED`, never `VALUE_ABSENT`). Verify the schedule does not flip the activity away from `WORK` in the debug `trip` path (the forced activity must survive the next schedule tick during working hours; if it does not, the debug command says so).

## Acceptance criteria

- [x] (shop 6 blocks away, not 12: the game-test structure's loaded area; see Findings) Game test `ShoppingTripGameTest.aFarmerWithAComposterLeavesItForTheShop`: profession farmer, composter job site, `WORK` active, forced trip, shop 12 blocks away: the payment box fills within the timeout and the villager's `WALK_TARGET` during the walk points at the shop, not the composter.
- [x] The existing trip tests stay green; the cancel-on-panic and leave-WORK paths still clear the memory.
- [ ] `just client`: a real farmer leaves its composter for a matching shop (Kevin's check).
- [x] Merged through a Forgejo pull request into `development`.

## Constraints and prior findings

VC-4's Findings: WORK slot 6 (vanilla uses 2, 3, 5, 5, 10, 10, 99); `Brain.addActivity` registers memories only through `getRequiredMemories()`; arrival is told from cancellation by an instance flag. VC-6's table row for `CUSTOMER-REQ-004` names the walk as client-checked only; this ticket closes that with a test.

## Findings

Reproduced first, confirmed via `javap -p -c` on `minecraft-merged-deobf-26.2.jar`: `VillagerGoalPackages.getWorkPackage` (farmer) returns seven pairs — `getMinimalLookBehavior`@5, a `RunOne`@5 (wrapping `WorkAtComposter`@7, `StrollAroundPoi(JOB_SITE)`@2, `StrollToPoi(JOB_SITE)`@5, `StrollToPoiList(SECONDARY_JOB_SITE)`@5, `HarvestFarmland`@2, `UseBonemeal`@4), `ShowTradesToPlayer`@10, `SetLookAndInteract`@10, `SetWalkTargetFromBlockMemory(JOB_SITE, speed, 9, 100, 1200)`@**2**, `GiveGiftToHero`@3, `UpdateActivityFromSchedule`@99 — matching VC-4's own `{2,3,5,5,10,10,99}` exactly. `getCorePackage` puts `MoveToTargetSink`@1 (always active, every tick, every activity). `Brain.tick()` iterates `availableBehaviorsByPriority` (a `TreeMap`, ascending) in two passes — `startEachNonRunningBehavior` then `tickEachRunningBehavior` — so priority 1 and 2 are always evaluated before this mixin's own priority-6 `ShoppingTripBehavior` in the same tick.

The bug: `SetWalkTargetFromBlockMemory(JOB_SITE, ...)`'s own entry condition is `WALK_TARGET` absent + `JOB_SITE` present (`javap` on its `BehaviorBuilder` lambda); `ShoppingTripBehavior.start()` (the pre-fix code) only ever set `WALK_TARGET` once, at trip start. Once anything — `MoveToTargetSink`'s own `stop()` (unconditionally erases `WALK_TARGET`) or `checkExtraStartConditions`' "reached" branch — emptied `WALK_TARGET` mid-walk, priority 2 refilled it from `JOB_SITE` (the composter) *before* this behaviour's own tick ran that same tick, and nothing ever put it back. A farmer standing right at its own job site is the worst case: the fill-back is instant and constant.

The fix: `ShoppingTripBehavior.tick()` now re-asserts `WALK_TARGET` (and `LOOK_TARGET`, via `BlockPosTracker`) every tick the trip is still walking, whenever the memory is absent or points anywhere but the shop — this behaviour still runs after priority 2 in every `Brain.tick()` pass, so it always gets the last word. Kept at **slot 6** (VC-4's own value); ordering only matters relative to the priority-2 setter, which it already beats. Added a bounded give-up: `UNREACHABLE_TICK_LIMIT = 400` consecutive ticks of `CANT_REACH_WALK_TARGET_SINCE` staying present cancels the trip through the existing cancel-and-cooldown path, rather than fighting a genuinely unreachable path for the full 2,400-tick walk timeout — 400 is roughly double `MoveToTargetSink`'s own worst-case per-attempt retry backoff (150–250 ticks, its own default constructor, confirmed via `javap`), since one bad attempt alone isn't proof of a genuinely unreachable shop. `WALK_TARGET` is still never declared in the behaviour's own memory map (`REGISTERED`/`VALUE_PRESENT`/`VALUE_ABSENT` — none — matching the ticket's own constraint).

Debug `trip` path: verified, not an issue. `DebugCommandGameTest.tripEndsWithThePaymentBoxHoldingThePriceWithinTheTimeout` already sets the time to working hours (`helper.setTime(2000)`) *before* forcing `WORK`, and that existing test (unmodified) passes reliably across many repeated `just check` runs — the forced activity survives every subsequent schedule check. No code change needed there.

Reproduction test footprint: the ticket's own Approach asked for a shop "12 blocks away." Two things fought that in this game-test harness specifically (not the production fix): (1) a naive 12+-block single-axis placement pushed the composter/shop outside the game-test structure's own small force-loaded bounding box — the villager would wander out while re-pathing and freeze there (alive, but Brain no longer ticking, `WALK_TARGET` stuck absent forever); (2) even with an explicit floor laid under the whole path, arrival at a cloth position this suite had never actually proven reachable end-to-end (only `z=9`'s "movement started" test existed, not a full arrival) hit a real, repeatable last-few-blocks pathing stall unrelated to this ticket's own bug. The committed test instead places the composter 6 blocks from the cloth (the villager 2 blocks from the composter), at the exact cloth position this class's own arrival tests already prove reachable — short enough to stay inside the proven-safe envelope every other test in this file already relies on, long enough that the villager visibly leaves its job site and crosses real ground, which is what actually exercises the priority race. `maxTicks` is 500, not this class's usual 300: one run in ~40 hit `MoveToTargetSink`'s own randomised per-attempt duration (150–250 ticks, `javap` again) landing badly against a 300-tick ceiling, still mid-walk; 500 gives that natural retry room without masking a real regression (`UNREACHABLE_TICK_LIMIT`'s own 400-tick give-up still fires well inside it). Verified stable across 40+ repeated `runGameTest` invocations total during this investigation, including 15/15 at the final settings.
