# Changelog

All notable changes to this project are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); the version scheme is
`<mod SemVer>+<minecraft version>`.

## [Unreleased]

## [1.0.0+26.2] - 2026-09-20

Tested with Minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.160.0 and Create Fly
26.2-rc-2-6.0.9-1.

Default configuration in force: 50% chance per restock, a 2400-tick walk timeout and a
2400-tick cooldown after a cancelled trip, experience nuggets at 3 xp apiece carrying the
suppressed trade orb's own `3 + random(4)` roll (one or two nuggets per unit), `shop_search_radius`
128 blocks, `nitwit_breeding_chance` 10%, and `keeper_seek_cooldown_ticks` 24000 ticks (one
vanilla day) — every data constant overridable per `contracts/public-surface.md`.

### Added

- Villager shops as points of interest: a table-cloth shop registers through Fabric's
  `PoiHelper`, discovered the same way vanilla POIs are, with a keeper-present check run once
  per candidate.
- The shopping trip: on each offer restock, a per-restock chance (default 50%) sends a working
  villager searching for a matching shop; on success it walks there, trades, and clears its trip
  memory, cancelling cleanly on panic, night, a lost target or the walk timeout, with a cooldown
  before the next roll.
- The transaction: a matched offer draws goods directly from the shop's network stock and pays
  into the stock ticker's payment box, one unit at a time until the offer's uses, the shop's
  stock or the box's space runs out; each unit is atomic — the payment must land before the goods
  ever leave the network, with no partial unit ever applied, and earlier completed units stand if
  a later one is refused.
- Experience nuggets: each completed unit inserts one or two `create:experience_nugget`s into the
  payment box, alongside the price, carrying the value of the trade's own suppressed xp-orb roll;
  the villager's own levelling xp is unchanged.
- Nitwit villagers as shopkeepers: a configured share of bred baby villagers are born nitwits
  instead of vanilla's unconditional none-profession baby, and an adult nitwit periodically seeks
  a free seat next to a keeperless stock ticker and walks to sit as its keeper.
- A development-only `/villager_customers debug` command (`roll`, `search`, `trip`, `box`,
  `shop`, `keeper`) for forcing a roll, inspecting search and trip state, and reading a nitwit's
  keeper state; registered only in a Fabric development environment, never present in a released
  jar.

### Changed

- A mod-driven unit no longer spawns the vanilla trade xp orb; its roll converts into the payment
  box's experience nuggets instead, so the shop earns the trade's xp rather than it landing on
  the ground unclaimed.
- The match now honours the offer's component predicate, not just its item and count, and the
  draw takes only stock that actually satisfies it.
- The shop search now runs village-wide, from the village's remembered meeting point (bell) when
  it has one, radius 128 blocks (`shop_search_radius`) — replacing the original 48-block job-site
  range. A nitwit's keeper seek uses the same search.

### Fixed

- The shopping trip kept losing its walk target to a re-evaluated `WORK` task mid-walk; it now
  holds the target against the work package until arrival.
- Vanilla's `JOB_SITE` requirement for the `WORK` activity was restored, so a villager still
  needs employment to work at all, as vanilla intends; a test now confirms villagers stay
  employed.

### Known issues

- The stock ticker shows no payments tooltip after a mod-driven unit: Create Fly's own port
  never renders its `StockTickerTooltipBehaviour`, for a player's own purchase either. This is an
  upstream gap, not something this mod can fix from outside it; the payment is in the box and a
  right-click withdraws it.
