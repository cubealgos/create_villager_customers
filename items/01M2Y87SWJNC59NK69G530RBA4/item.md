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

- [ ] Game test `ShoppingTripGameTest.aFarmerWithAComposterLeavesItForTheShop`: profession farmer, composter job site, `WORK` active, forced trip, shop 12 blocks away: the payment box fills within the timeout and the villager's `WALK_TARGET` during the walk points at the shop, not the composter.
- [ ] The existing trip tests stay green; the cancel-on-panic and leave-WORK paths still clear the memory.
- [ ] `just client`: a real farmer leaves its composter for a matching shop (Kevin's check).
- [ ] Merged through a Forgejo pull request into `development`.

## Constraints and prior findings

VC-4's Findings: WORK slot 6 (vanilla uses 2, 3, 5, 5, 10, 10, 99); `Brain.addActivity` registers memories only through `getRequiredMemories()`; arrival is told from cancellation by an instance flag. VC-6's table row for `CUSTOMER-REQ-004` names the walk as client-checked only; this ticket closes that with a test.
