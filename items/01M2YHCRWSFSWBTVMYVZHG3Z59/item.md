---
schema_version: 1
id: 01M2YHCRWSFSWBTVMYVZHG3Z59
key: VC-12
type: bug
title: A mod-driven trade still drops the player's xp orb; the nuggets carry the orb's roll instead
created_by: kevin
created_at: 2026-09-20T04:32:36Z
---

## Scope

A mod-driven trade still drops vanilla's experience orb at the villager (Kevin, 2026-09-20, client check): `AbstractVillager.notifyTrade` → `Villager.rewardTradeXp` spawns an orb worth `3 + random(4)` xp for whoever is near, on top of the villager's own levelling xp. For a unit the villager executes against a shop, no orb shall spawn; instead the nuggets inserted into the payment box shall carry exactly the orb's roll (3 to 6 xp, one or two nuggets at 3 xp each), replacing the current offer-xp-based count (Kevin, 2026-09-20: "the orb's roll, 3 to 6 xp") (`TRANSACTION-REQ-006`, `TRANSACTION-REQ-010`, `DEC-007`).

## Approach

Read `Villager.rewardTradeXp(MerchantOffer)` and `AbstractVillager.notifyTrade` in the 26.2 jar: which part levels the villager (`villagerXp += offer.getXp()`, `increaseProfessionLevelOnUpdate`, `shouldIncreaseLevel`) and which part spawns the orb (`offer.shouldRewardExp()`, `ExperienceOrb`). Prefer the narrowest change: a mixin on `Villager.rewardTradeXp` that skips the orb spawn while a thread-local "mod-driven unit" flag is set by `TransactionExecutor` (set around the `notifyTrade` call, cleared in `finally`), keeping the levelling path untouched; the executor rolls the orb's value itself with the same formula from the level's random and converts it with `NuggetConversion`. Alternative if the mixin is awkward: replicate the levelling part through an accessor mixin and never call `notifyTrade`. Record the choice. The spec's `TRANSACTION-REQ-010` and `DEC-007` text move from "offer xp" to "the orb's roll" (vault, then `just spec-sync`).

## Acceptance criteria

- [ ] Game test: a completed unit spawns no `ExperienceOrb` in the test area, the villager's `villagerXp` still rises by the offer's xp, and the payment box holds one or two nuggets (the orb's roll at 3 xp each); over 20 units the nugget counts are within 1..2 each.
- [ ] A player trade in the same world still drops its orb (game test with a mock player trade or a unit test on the flag: the mixin skips only while the flag is set).
- [ ] `docs/spec/` synced with the amended `TRANSACTION-REQ-010`/`DEC-007`.
- [ ] Merged through a Forgejo pull request into `development`.

## Constraints and prior findings

VC-3's Findings: `notifyTrade` already calls `increaseUses`; nugget xp value 3 (`ExperienceNuggetItem.use`). `villager_customers.mixins.json` currently lists `VillagerBrainMixin` only.
