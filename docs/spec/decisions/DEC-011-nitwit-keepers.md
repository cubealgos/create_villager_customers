---
title: "create_villager_customers DEC-011 — Nitwit villagers become shopkeepers"
type: "spec"
category: "create_villager_customers"
---

# `DEC-011` — Nitwit villagers become shopkeepers

**Status:** decided by Kevin, 2026-09-20. Cited throughout as `domains/keeper.md`.

## The ruling

> "Create offers a unique opportunity to make nitwits useful: they should be able to work as
> shopkeepers when seated." — Kevin, 2026-09-20

> "breeding chance but only 10%" — Kevin, 2026-09-20, on how nitwits enter the population: ten
> percent of bred babies grow up as nitwits; vanilla Java gives none from breeding at all
> (research §G.3).

> "a grown up nitwit should walk to a free seat next to a shop ticker that has no keeper yet; if
> none are available they just behave like normal nitwits." — Kevin, 2026-09-20. Autonomous, no
> item, no lead: a nitwit cannot be leashed in vanilla 26.2 (`AbstractVillager.canBeLeashed()` is
> hard-coded `false`, research §G.2), so the only way to move one at all is a mod-side walk-target
> behaviour, exactly the shape `domains/customer.md` already built for the shopping trip.

## Why a nitwit at all

`StockTickerBlockEntity.isKeeperPresent()` — the one gate a seated mob gives Create Fly, unlocking
`TableClothBlockEntity.useShop(Player)` — checks only for an occupied `SeatEntity` next to the
ticker; no profession, age or species read anywhere in the method (research §G.1). A nitwit never
trades (`VillagerProfession.NITWIT` registers empty `tradeSetsByLevel`) and can never regain a
profession (`ResetProfession` explicitly skips `NITWIT`, `AcquirePoi`'s predicate can structurally
never match `PoiType.NONE`) — it is the one villager a player's economy has no other use for.
Seating it as a keeper turns dead weight into the one thing Create Fly actually asks a villager to
do at a shop.

## Home

Inside `create_villager_customers` as a new domain (`KEEPER`, `domains/keeper.md`), not a separate
mod: it reuses the same shopping-trip walk-target machinery, the same village-wide search radius
(`DEC-010`), and the same mixin into `Villager.registerBrainGoals` that `CUSTOMER` already
requires — a second mod would duplicate all three for no isolation this project needs.

## Alternatives considered

- **A conversion item or workstation-free hiring interaction** (research §G.3 options (b)/(c)):
  `Villager.setVillagerData` is public, so either is technically straightforward. Not built at
  1.0 — Kevin's ruling names only breeding; recorded as a later idea in `domains/keeper.md` §7,
  not a requirement.
- **Any seated `LivingEntity` as keeper**, matching what `isKeeperPresent()` itself actually
  accepts. Rejected: the ruling is specifically about nitwits; a general "seat anything as keeper"
  feature is a different, unscoped feature this mod is not asked to build.

## Cost if wrong

If 10% turns out too high or low once players see it in play, it is one data constant
(`nitwit_breeding_chance`, `contracts/public-surface.md`), not a redesign. If the birth-time roll
(`domains/keeper.md` `KEEPER-REQ-001`) turns out wrong — Kevin meant the chance rolled at grow-up,
not at birth — the mixin target moves from `Villager.finalizeSpawn`'s `BREEDING` branch to whatever
hook fires on a baby villager's age-up (unconfirmed to exist as of the research pass), a bounded
rewrite of one mixin, not of the domain. If the claim mechanism between competing nitwits
(`KEEPER-REQ-007`) needs a registry of the mod's own rather than reusing vanilla's POI reservation
ticket, that is one more piece of non-persistent server state, not a design change.
