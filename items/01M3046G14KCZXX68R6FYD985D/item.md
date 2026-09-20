---
schema_version: 1
id: 01M3046G14KCZXX68R6FYD985D
key: VC-18
type: feat
title: "Village-wide shop search: 128 blocks from the villager's meeting point, config shop_search_radius"
created_by: kevin
created_at: 2026-09-20T19:20:27Z
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

Kevin, 2026-09-20: "the villager only detects shops very close by; I would want them to detect a shop within village borders." Ruled as `decisions/DEC-010-village-wide-shop-search.md` and the amended `CUSTOMER-REQ-003`: the search origin is the villager's remembered `MEETING_POINT` (bell) when present, else its position; the radius is 128 blocks horizontally, configurable as `shop_search_radius` (default 128); the trip's 2,400-tick give-up and nearest-wins stay.

## Approach

`ShopSearch.VILLAGE_REACH` becomes the config value; `CustomerHooks` (the restock roll's search call) resolves the origin from the brain's `MemoryModuleType.MEETING_POINT` (a `GlobalPos`; only when in the same dimension) before calling `ShopSearch.matching`. Verify `PoiManager.getInRange` accepts a 128 radius efficiently (it iterates sections in range; note the cost in a Javadoc) and that the vertical extent is the section range. Config: add the key to the existing config file with clamp 16..256. Game tests: a shop 100 blocks from the bell is found when the villager stands at the bell; a shop 100 blocks from the villager but 200 from its bell is not; with no meeting point the villager's position is the origin. Sync `docs/spec` from the vault.

## Acceptance criteria

- [ ] Search origin is the meeting point when present, else the villager; radius from config, default 128, clamped.
- [ ] Game tests for the three cases above; existing tests green; `just check` green.
- [ ] Spec synced; merged through a Forgejo pull request into `development`; Kevin's client check: a shop across the village is found.
