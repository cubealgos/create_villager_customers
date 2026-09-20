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

- [x] Game test: a completed unit spawns no `ExperienceOrb` in the test area, the villager's `villagerXp` still rises by the offer's xp, and the payment box holds one or two nuggets (the orb's roll at 3 xp each); over 20 units the nugget counts are within 1..2 each.
- [x] A player trade in the same world still drops its orb (game test with a mock player trade or a unit test on the flag: the mixin skips only while the flag is set).
- [x] `docs/spec/` synced with the amended `TRANSACTION-REQ-010`/`DEC-007`.
- [x] Merged through a Forgejo pull request into `development`.

## Constraints and prior findings

VC-3's Findings: `notifyTrade` already calls `increaseUses`; nugget xp value 3 (`ExperienceNuggetItem.use`). `villager_customers.mixins.json` currently lists `VillagerBrainMixin` only.

## Findings

`javap -p -c` against `minecraft-merged-deobf-26.2.jar` confirmed the bytecode the Approach assumed:

- `AbstractVillager.notifyTrade(MerchantOffer)` is a straight line: `offer.increaseUses()`, then
  `this.rewardTradeXp(offer)` (abstract on `AbstractVillager`, implemented on `Villager`), then the
  `TradeTrigger` advancement if the trading player is a `ServerPlayer`.
- `Villager.rewardTradeXp(MerchantOffer)` is one method, no branches around the orb: `int xp = 3 +
  this.random.nextInt(4)`; `this.villagerXp += offer.getXp()`; `this.lastTradedPlayer =
  getTradingPlayer()`; if `shouldIncreaseLevel()`, sets `updateMerchantTimer = 40`,
  `increaseProfessionLevelOnUpdate = true` and `xp += 5` (a level-up adds 5 to the *orb's* roll, not
  reflected in `TRANSACTION-REQ-010`'s "3 to 6" text — irrelevant here since the orb is suppressed
  outright for a mod-driven unit, but worth knowing if this method is ever revisited); then, only if
  `offer.shouldRewardExp()`, `this.level().addFreshEntity(new ExperienceOrb(level(), x, y + 0.5, z,
  xp))` — a single call, its `boolean` result discarded with `pop`. No static `ExperienceOrb.award`
  helper is used on this path. `MerchantOffer.shouldRewardExp()` is a plain `boolean rewardExp` field
  getter, true by default for offers built through the plain `(ItemCost, ItemStack, int, int, float)`
  constructor (traced through its constructor-chaining bytecode; confirmed at runtime by the new
  `aDirectNotifyTradeOutsideTheExecutorStillSpawnsItsOrb` game test, which asserts
  `offer.shouldRewardExp()` before relying on it).

  Chosen injection point: `@Redirect` on `Level.addFreshEntity(Entity)Z` inside
  `Villager.rewardTradeXp`, gated on a new `villager_customers.transaction.ModDrivenTrade` thread-local
  flag. An `@Inject` cannot skip a single call by itself (only cancel the whole method, which would
  also skip the levelling, or need a Mixin Extras `@WrapOperation` this project does not otherwise
  depend on — though `mixinextras` is present transitively via Fabric API/Create, per the game test's
  own mod list), so `@Redirect` on the one call that actually spawns the orb is the narrowest target
  the bytecode allows. The levelling code above it runs unconditionally and is never touched.

  `Level.random` (the field the bytecode reads as `this.random` on the *villager*, not the level) is
  `protected` on `Level`, not visible from `villager_customers.transaction` — `TransactionExecutor`
  rolls the orb's value with the public `level.getRandom().nextInt(4)` instead of a `level.random`
  field access; same `RandomSource`, same formula, public accessor.

- `TransactionExecutor.execute` now rolls `int orbXp = 3 + level.getRandom().nextInt(4)` per unit
  (before the stock/box checks, since the nugget count already needed to be known there), converts it
  through `NuggetConversion.nuggets(orbXp, NUGGET_XP)` instead of `offer.getXp()`, and wraps
  `villager.notifyTrade(offer)` in `ModDrivenTrade.begin()` / `try` / `finally { ModDrivenTrade.end();
  }`. `NuggetConversion` itself is unchanged and stays pure (still just "xp / nuggetXp, rounded up");
  only its caller's input changed.
- The existing `aMatchedOfferDrawsPaysAndAdvancesUsesOneVisitAtATime` game test asserted an exact
  nugget count derived from the offer's xp (`EXPECTED_NUGGETS = ceil(10 / 3) = 4`); that assertion is
  now wrong on its face once nuggets stopped tracking offer xp, so it was changed to a 1..2-per-unit
  range check (matching the new source of truth) rather than left passing by coincidence, plus a
  no-orb assertion.
- Two new game tests in `TransactionGameTest`: `aModDrivenUnitSpawnsNoOrbAndItsNuggetsCarryTheOrbsRoll`
  (20 single-unit offers, one at a time so each unit's own nugget delta — not just the 20-unit total —
  is checked against 1..2; villager xp checked to rise by the offer's xp every unit; no
  `ExperienceOrb` present anywhere at the end) and `aDirectNotifyTradeOutsideTheExecutorStillSpawnsItsOrb`
  (calls `villager.notifyTrade(offer)` directly, bypassing `TransactionExecutor` and its flag entirely,
  and asserts an orb *is* present — proving the mixin's suppression is scoped to a mod-driven unit, not
  global). `just check`: "All 27 required tests passed :)" (25 before this ticket, +2 here).
