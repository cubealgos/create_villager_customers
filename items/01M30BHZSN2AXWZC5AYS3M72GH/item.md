---
schema_version: 1
id: 01M30BHZSN2AXWZC5AYS3M72GH
key: VC-21
type: bug
title: VillagerBrainMixin erases vanilla's JOB_SITE requirement for the WORK activity on every villager
created_by: kevin
created_at: 2026-09-20T21:29:04Z
---

## Scope

<fill this in before committing>

## Approach

<fill this in before committing>

## Acceptance criteria

- [x] <fill this in before committing>

## Constraints and prior findings

<fill this in before committing>

## Scope

Found by VC-20 (bytecode): `VillagerBrainMixin` (VC-4) calls `Brain.addActivity(Activity.WORK, ..., Set.of(), ...)`, and `addActivity` stores the requirement set with a replacing `Map.put`, so vanilla's `JOB_SITE VALUE_PRESENT` requirement for `WORK` is wiped for every villager. Unemployed villagers and nitwits can then enter `WORK` with no job site, which vanilla never allows; the shopping-trip tests relied on it (they force `WORK` on job-siteless test villagers). Restore vanilla's requirement: register the shopping trip into `WORK` without replacing the activity's requirements (`Brain.addActivityWithConditions` or by re-adding the vanilla condition set alongside), and give `ShoppingTripGameTest`'s villagers a real job site so the tests hold under vanilla's rule. `KeeperSeekBehavior` (IDLE) is unaffected.

## Acceptance criteria

- [x] `WORK` keeps vanilla's `JOB_SITE` requirement with the mod loaded (a game test: a job-siteless villager never enters `WORK`; an employed one still runs the shopping trip).
- [x] Existing customer tests pass with employed test villagers; `just check` green; merged through a Forgejo pull request into `development`.
