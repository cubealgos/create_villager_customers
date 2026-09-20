---
schema_version: 1
id: 01M308654B7ATC6S80D8QF3QRZ
key: VC-20
type: feat
title: Adult nitwits seek a free seat next to a keeperless stock ticker and sit as its keeper
created_by: kevin
created_at: 2026-09-20T20:30:11Z
---

## Scope

<fill this in before committing>

## Approach

<fill this in before committing>

## Acceptance criteria

- [ ] <fill this in before committing>

## Constraints and prior findings

<fill this in before committing>

## Scope

Kevin, 2026-09-20: "a grown up nitwit should walk to a free seat next to a shop ticker that has no
keeper yet; if none are available they just behave like normal nitwits." Ruled as
`decisions/DEC-011-nitwit-keepers.md` and `domains/keeper.md` `KEEPER-REQ-003`–`013`: while adult
and idle, an on-timer, unconditional (not chance-gated) seek across the same village-wide radius
`CUSTOMER`/`SHOP` already search (`shop_search_radius`, `DEC-010`), for a Create seat adjacent to a
stock ticker with no keeper present, unoccupied and unclaimed; claim it, walk to it reusing
`CUSTOMER`'s trip behaviour (2400-tick give-up), and let Create's own `SeatBlock.onEntityMovement`
auto-seat it — no mod call. Also in scope: the ejection/broken-seat cooldown path
(`KEEPER-REQ-011`, `012`) and the no-eligible-seat fallback (`KEEPER-REQ-013`). Not in scope: the
breeding roll that supplies nitwits at all (VC-19, independent — this ticket can be built and
tested against worldgen nitwits alone) and any preference in `CUSTOMER`'s own search for a
nitwit-kept shop (`domains/keeper.md` §7, explicitly deferred, `KEEPER-REQ-015`).

## Approach

Second `Behavior<Villager>` via the same mixin as `CUSTOMER`'s trip behaviour
(`04-architecture.md` `ARCH-DEC-002`, `006`), gated to profession `NITWIT` and `!isBaby()`, added
to `Activity.IDLE` (not `WORK` — a nitwit's `acquirableJobSite` predicate can structurally never
match, so it is not confirmed a nitwit ever reaches `WORK`; verify this first and record the
finding, since it changes which `Activity` the behaviour is added to but not its shape). Two new
memory module types: a seek-cooldown `Long` and a claimed-seat `GlobalPos`, mirroring `CUSTOMER`'s
trip-target and cooldown memories (`ARCH-DEC-005`). For the claim itself, first check whether
vanilla's `PoiManager` take/free reservation ticket can be POI-registered over eligible seats and
reused directly (it already prevents two villagers claiming one job site or bed); if no clean hook
exists, fall back to a lightweight non-persistent per-server map from seat `GlobalPos` to claiming
villager UUID, checked during search and cleared on arrival, cancellation, timeout, ejection or a
broken seat. Reuse `CUSTOMER`'s `SetWalkTargetFromBlockMemory` pattern and give-up timeout for the
walk itself; do not reimplement it. Detecting "unseated" (ejected or seat/ticker broken): poll
whether the nitwit is still riding its `SeatEntity` each tick the behaviour is active, or find a
`Mob.stopRiding`/passenger-changed hook — confirm at implementation time. Add
`keeper_seek_cooldown_ticks` (default 24000, clamp e.g. 200..100000) as the one config key reused
for every wait (idle interval, failed-seek retry, post-ejection cooldown) per `KEEPER-DEC-003`.
Game tests: an adult nitwit with one eligible seat in range claims and walks to it and ends up
seated (`isKeeperPresent()` true afterward); two adult nitwits and one eligible seat — exactly one
ends up seated, the other returns to idle; a baby nitwit never seeks; an unemployed (`NONE`)
non-nitwit villager never seeks; right-clicking a seated nitwit's seat ejects it and it re-seeks
only after the cooldown; breaking the seat block under a seated nitwit unseats it the same way; no
eligible seat anywhere leaves the nitwit idle with no error. Sync `docs/spec` from the vault.

## Acceptance criteria

- [ ] An adult `NITWIT` villager with no active claim or seat periodically (on
      `keeper_seek_cooldown_ticks`) searches for an eligible seat within `shop_search_radius` of
      the village's meeting point (or its own position with none), unconditionally — no chance
      roll gates the search itself.
- [ ] An eligible seat is: a Create seat within `isKeeperPresent()`'s own adjacency to a stock
      ticker, that ticker reporting no keeper present, the seat unoccupied, and unclaimed by
      another nitwit.
- [ ] A found seat is claimed before the walk starts, so a second nitwit's concurrent seek cannot
      also claim it; nearest eligible seat wins among candidates.
- [ ] The nitwit walks to its claimed seat reusing `CUSTOMER`'s trip give-up (2400 ticks); arriving
      results in Create's own auto-seat with no explicit seat-entry call from this mod.
- [ ] A seated nitwit takes no further mod action to stay seated; ejection (right-click) or the
      seat/ticker breaking clears its claim and starts `keeper_seek_cooldown_ticks` before its next
      seek; a raid or other panic while seated is left alone (position pin already prevents
      movement).
- [ ] A baby nitwit and a non-nitwit unemployed (`NONE`) villager never run this behaviour.
- [ ] No eligible seat anywhere leaves the nitwit idle, no error, retried at the next timer.
- [ ] Game tests for: single nitwit claims and seats; two nitwits contend for one seat and only one
      seats; baby nitwit never seeks; `NONE` villager never seeks; ejection triggers cooldown then
      re-seek; broken seat triggers cooldown then re-seek; zero eligible seats leaves the nitwit
      idle. Existing tests stay green; `just check` green.
- [ ] Config key `keeper_seek_cooldown_ticks` exists, defaults to 24000, clamped to a sane range.
- [ ] Spec synced from the vault; merged through a Forgejo pull request into `development`;
      Kevin's client check: an idle nitwit near an unclaimed seat walks over and the shop's
      `isKeeperPresent()` flips true with no interaction from Kevin.

## Constraints and prior findings

- `SeatBlock.onEntityMovement` auto-seats any `LivingEntity` passing `canBePickedUp` (excludes
  `Shulker`, `Player`, `IGNORE_SEAT`-tagged, hostile unless configured) — no profession or age
  check anywhere, confirmed for a nitwit specifically.
- `StockTickerBlockEntity.isKeeperPresent()` scans two below-offsets × four horizontal directions
  from the ticker for any occupied `SeatEntity` — that geometry, not a new one, is what "adjacent"
  means for seat eligibility.
- Villagers cannot be leashed (`AbstractVillager.canBeLeashed()` = `false`) and have no
  `TemptGoal`; the walk-target mixin is the only way to move one, already proven by `CUSTOMER`.
- `Mob.serverAiStep()` ticks the brain unconditionally, with no passenger/vehicle gate — a seated
  nitwit's brain keeps running; only `SeatEntity.positionRider` pins its visible position, so no
  special panic/raid handling is needed.
- Right-clicking an occupied non-player seat ejects the occupant and seats the player instead
  (`SeatBlock.useItemOn`) — this is the player's own eviction path, not something this ticket adds.
- Largest open risk carried into this ticket: whether a nitwit ever reaches `Activity.WORK` (it
  may not, changing only which `Activity` this behaviour is added to) and whether vanilla's
  `PoiManager` reservation can be reused for the claim instead of a structure of this mod's own.
