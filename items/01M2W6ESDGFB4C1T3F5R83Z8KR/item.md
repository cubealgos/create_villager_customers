---
schema_version: 1
id: 01M2W6ESDGFB4C1T3F5R83Z8KR
key: VC-2
type: feat
title: "Shops as points of interest: PoiType, Shop view, candidate search"
created_by: kevin
created_at: 2026-09-19T06:42:59Z
---

## Scope

What makes a table cloth count as a shop and how villagers find one (`docs/spec/domains/shop.md`, `SHOP-REQ-001..006`; `docs/spec/04-architecture.md` `ARCH-DEC-003`; `docs/spec/decisions/DEC-008-poi.md`). Registers a `PoiType` over Create Fly's table-cloth block states so villagers discover shops through the village POI index vanilla already maintains for job sites (`SHOP-REQ-001`). A `Shop` view class that decides shophood live from a table cloth block entity — non-empty request and price, linked stock ticker present, and that ticker reporting a keeper present (`SHOP-REQ-002`, `SHOP-REQ-003`; `SHOP-DEC-002`, a live check, never cached) — and exposes the goods stack, the price stack and the linked stock ticker to `VC-3` and `VC-4`. A search helper over the village POI index for candidate shops matching a given offer, restricted to village membership with no invented distance (`SHOP-REQ-004`), nearest-first tie-break when more than one candidate matches (`SHOP-REQ-006`, `SHOP-DEC-001`). Out of scope: the match rule against a specific offer's cost/result shape (`VC-3`) and the trip that walks a villager here (`VC-4`).

## Approach

First thing to verify — the two open questions `domains/shop.md` §7 and `04-architecture.md` `ARCH-DEC-003` both name as unconfirmed until this ticket: the exact 26.2 `PoiType` registration API (a `javap` read of `net.minecraft.world.entity.ai.village.poi.PoiType`/`PoiTypes` and whether Fabric API's `fabric-registry-sync-v0`/a `PointOfInterestHelper` exists in the 0.160.0 jars, or whether registration is the raw vanilla `Registry.register` plus a `BlockState`→`PoiType` map entry per state), and the full set of Create Fly table-cloth block states to register over (`javap`/jar-listing read of `com.zurrtum.create.content.logistics.tableCloth.TableClothBlock`'s `BlockState` properties — facing variants at minimum). Then: `villager_customers.shop.ShopPoiType` (registration), `villager_customers.shop.Shop` (the view class, wrapping a `TableClothBlockEntity` plus its linked `StockTickerBlockEntity`, exposing `goods()`, `price()`, `stockTicker()`, `isShop()`), `villager_customers.shop.ShopSearch` (queries the village's `PoiManager`/`PoiRecord` set for the requesting villager, filters to `Shop`s whose goods/price match a candidate offer, sorts by distance, returns the nearest). No mod-owned caching anywhere — `SHOP-DEC-002` requires shophood recomputed on every check.

## Acceptance criteria

- [x] `PoiType` `villager_customers:table_cloth_shop` registered over every Create Fly table-cloth block state, confirmed by listing the state set found in the jar read above (`SHOP-REQ-001`).
- [x] `Shop.isShop()` is true only when price and goods are both non-empty and the linked stock ticker reports a keeper present; false the moment any of those breaks, with no unregistration step needed (`SHOP-REQ-002`, `SHOP-REQ-003`, shop candidacy state table in `domains/shop.md` §3).
- [x] `ShopSearch` restricts candidates to the requesting villager's own village POI membership (vanilla's own reach, no invented distance) and, given more than one match, returns the nearest (`SHOP-REQ-004`, `SHOP-DEC-001`).
- [x] No new screen, block or item is added by this ticket — confirmed by review against `SHOP-REQ-005`.
- [x] Game test: a table cloth placed and configured (price, goods, linked stock ticker with a keeper present) in a test world is found through the village POI index by `ShopSearch` (`ShopPoiGameTest`, `ShopSearchGameTest`; named differently from the plan).
- [x] Game test: shophood flips to false — and the POI is no longer returned as a candidate — when the price is cleared, and separately when the linked stock ticker is removed or unlinked, with no world-state left over (`ShopViewGameTest`, `SHOP-REQ-006` precondition; the arrival-time recheck itself is `VC-4`'s).

## Constraints and prior findings

`docs/spec/domains/shop.md` §7 open questions: "Exact `PoiType` registration API and the full table-cloth block state set" and "Vanilla's own POI/village search reach figure" — both resolved by this ticket's first-step verification, not assumed. Research note `vault/technical/minecraft/create-fly-shops-and-villager-brain-26-2.md` §D: Create Fly registers no `PoiType` of its own and reserves no villager-related behaviour anywhere in its code, so nothing existing collides with the new registration. `SHOP-DEC-002`: a live check, not a cached registration — shophood is computed fresh from the table cloth's current price, goods and linked ticker every time it is checked, never cached across ticks, since `UC-007` (a shop removed mid-trip) needs it fresh at arrival. Blocked by `VC-1` (the Gradle project and toolchain this ticket's registration and tests build on).
