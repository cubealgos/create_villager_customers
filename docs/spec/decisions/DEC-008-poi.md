---
title: "create_villager_customers DEC-008 — Shops are found through a point-of-interest registration"
type: "spec"
category: "create_villager_customers"
---

# `DEC-008` — Shops are found through a point-of-interest registration

**Status:** decided by Kevin, 2026-09-19 ("no reason not to").

The mod registers a `PoiType` over Create Fly's table cloth block states, so a villager finds a
shop through the village POI index exactly as it finds a job site — `domains/shop.md`
`SHOP-REQ-001`, walked to via the same `SetWalkTargetFromBlockMemory` pattern vanilla's own
`GoToPotentialJobSite`/`WorkAtPoi` use (research §B). Create Fly registers no `PoiType` of its own
and reserves no villager-related behaviour anywhere in its code (research §D), so there is nothing
existing to collide with.

Alternative considered: a raw block scan around each villager on a timer. Rejected: it would
reinvent the indexing, chunking and village-membership logic vanilla's `PoiManager` already
maintains for exactly this purpose, and would need to be kept in step with every table cloth
placed or broken by hand instead of getting that for free from the POI system. Cost if wrong: the
exact registration API and block state set are unconfirmed until the first ticket
(`04-architecture.md` `ARCH-DEC-003`); worst case, a different vanilla discovery mechanism is
substituted, but the POI shape of "one registered type per candidate block" is very unlikely to be
wrong given job sites already work this way.
