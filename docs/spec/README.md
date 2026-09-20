---
title: "create_villager_customers spec — index"
type: "spec"
category: "create_villager_customers"
repo: "create_villager_customers"
---

# Create Fly: Villager Customers — specification

A small Create Fly add-on for Minecraft 26.2 on Fabric: during their working hours, a villager
with an offer that still has uses left may, with a random chance rolled per restock, walk to a
nearby table-cloth shop that mirrors that offer and execute it — putting the cloth's price into
the shop's payment box and taking the cloth's goods straight out of the shop's network stock,
consuming one use, gaining its own trade xp, and crediting the shop with the same xp as Create's
own experience nuggets. Buying and selling are one mechanism, since the table cloth defines which
side is goods and which is price (`decisions/DEC-005-one-mechanism.md`). The third of the small
add-ons built to gain the experience `create_civilization` needs; it is nonetheless a distributed
product with real users and is specified as one (`decisions/DEC-001-classification.md`).

This spec is the distributed-product spec sheet in the chunked format. The sheet's sections map to
files as follows; a section marked *out of scope* says why in the file that would have held it.

| Sheet section | File |
|---|---|
| §1 Document control | this file: identifiers, state, decisions |
| §2 Executive summary and business context | `00-context.md` |
| §3 Product architecture and runtime topology | `04-architecture.md` |
| §4 Domain-driven functional specifications | `01-actors.md`, `02-journeys.md`, `03-glossary.md`, `domains/customer.md`, `domains/transaction.md`, `domains/shop.md` |
| §5 Interface contracts and integration | `contracts/platform-matrix.md`, `contracts/public-surface.md`, `contracts/data-contract.md` |
| §6 Compliance, security and governance | `operations/compliance.md` |
| §7 Release engineering, distribution and support | `operations/release.md`, `operations/testing.md` |
| §8 Migration, compatibility and out of scope | `00-context.md` §What it will not do, `contracts/data-contract.md` |
| Appendix: technical blueprints | `04-architecture.md` §Shape |

## Files and state

| File | Domain prefix | State |
|---|---|---|
| `00-context.md` | — | written |
| `01-actors.md` | `ACTORS` | written |
| `02-journeys.md` | `UC` | written |
| `03-glossary.md` | — | written |
| `04-architecture.md` | `ARCH` | written |
| `domains/customer.md` | `CUSTOMER` | written |
| `domains/transaction.md` | `TRANSACTION` | written |
| `domains/shop.md` | `SHOP` | written |
| `contracts/platform-matrix.md` | `PLATFORM` | written |
| `contracts/public-surface.md` | `SURFACE` | written |
| `contracts/data-contract.md` | `DATA` | written |
| `operations/compliance.md` | `COMP` | written |
| `operations/release.md` | `REL` | written |
| `operations/testing.md` | `TEST` | written |

## Identifiers

`<DOMAIN>-<KIND>-<NNN>`: `CUSTOMER-REQ-004`, `TRANSACTION-UC-001`, `SHOP-FAIL-001`, `ARCH-DEC-002`.
Permanent; a withdrawn item keeps its number.

## Verifications

Every row is a claim in this sheet traced to
`vault/technical/minecraft/create-fly-shops-and-villager-brain-26-2.md` (2026-09-19, read via
`javap` against the Create Fly jar, the merged Minecraft jar, and the Fabric API module jars — no
sources jars existed for either mod). A claim not in this table and not in that note is marked "to
verify at the first ticket" where it appears.

| # | Claim | Section |
|---|---|---|
| 1 | The table cloth stores only a price tag, a request and an owner — no `Container`; stock lives in the packager network, payment lands only in the stock ticker's `receivedPayments`, never the cloth | §A |
| 2 | No non-player checkout API exists: `TableClothBlockEntity.useShop` and `StockTickerInteractionHandler.interactWithShop` both require and mutate a real `Player`'s inventory | §A |
| 3 | `StockTickerBlockEntity.getAccurateSummary()`/`getRecentSummary(): InventorySummary` read live stock; `receivedPayments` is reached through `Container.insert(List<ItemStack>)`, confirmed used by the real checkout | §A |
| 4 | `TableClothBlockEntity.isShop()` = a non-empty request; `getPaymentItem()`/`getPaymentAmount()` read the price tag; a linked `StockTickerBlockEntity.isKeeperPresent()` gates stock checks | §A |
| 5 | 26.2 removed `Schedule`/`ScheduleBuilder`; the villager schedule is now `EnvironmentAttribute<Activity>`, and `Brain.addActivity(Activity, ImmutableList<Pair<Integer,BehaviorControl>>, Set<Pair<MemoryModuleType,MemoryStatus>>, Set<MemoryModuleType>)` is the public way to add behaviours to an activity | §B |
| 6 | `Villager.registerBrainGoals(Brain)` (private) is called from both `makeBrain` and `refreshBrain`, the narrowest shared target for a mixin | §B |
| 7 | No Fabric API event exists for villager brain, schedule or activity construction, checked exhaustively across `fabric-entity-events-v1`, `fabric-lifecycle-events-v1`, `fabric-events-interaction-v0` | §C |
| 8 | `SetWalkTargetFromBlockMemory.create(MemoryModuleType<GlobalPos>, float, int, int, int): OneShot<Villager>` is the generic "walk to a remembered position" behaviour vanilla's own job-site behaviours use | §B |
| 9 | `MerchantOffer` exposes `getItemCostA()`, `getItemCostB(): Optional<ItemCost>`, `getResult()`, `getUses()`, `increaseUses()`, `getDemand()`/`updateDemand()`; `AbstractVillager.notifyTrade(MerchantOffer)` and its own inventory exist | §B |
| 10 | No vanilla `Behavior` autonomously executes a `MerchantOffer`; `TradeWithVillager` is inferred (not traced) to be surplus-food gifting, not offer execution | §B |
| 11 | Create Fly registers no `PoiType` and reserves no villager-related behaviour anywhere in its jar; its two `villager_job_sites` tags re-list only vanilla blocks | §D |

## Divergences from heimathafen standards

| Standard | Divergence | Recorded in |
|---|---|---|
| `default-license-apache-2-cla` | MIT, no CLA | `decisions/DEC-003-licence.md` |
| `naming-theme` | Descriptive English under the Create add-on convention | `decisions/DEC-002-name.md` |
| "no remote unless justified later" | Public on Forgejo under `cubealgos` from the bootstrap, mirrored to GitHub with the issue tracker there, as both siblings ended up | `decisions/DEC-003-licence.md` |

## Decisions

| ID | Decision | State |
|---|---|---|
| `DEC-001` | Distributed product, full spec sheet | written |
| `DEC-002` | `create_villager_customers`, mod id `villager_customers`, "Create Fly: Villager Customers" | written |
| `DEC-003` | MIT; public under the cubealgos organisation from the first commit | written |
| `DEC-004` | Toolchain as the siblings, plus a second mixin target (`create`, alongside `minecraft`) | written |
| `DEC-005` | Both directions — villager buys, villager sells — are one act, driven by the match rule | written |
| `DEC-006` | Direct draw from the network stock; no packages, no player checkout | written |
| `DEC-007` | The shop earns the trade's xp as `create:experience_nugget`; conversion default proposed | written |
| `DEC-008` | Shops are found through a point-of-interest registration over table cloth block states | written |
| `DEC-009` | A random chance per restock decides whether a villager goes shopping; default 50% proposed | written |
| `DEC-010` | Shops are found across the whole village: 128 blocks from the meeting point, config `shop_search_radius` | written |

## Open questions gathered

All proposals of the first draft were ruled on by Kevin on 2026-09-19: a 50 % chance rolled once per
restock, a 2,400-tick walk timeout and a 2,400-tick cooldown after a cancelled trip, the nearest
matching shop wins, nuggets equal to the offer's xp over a nugget's value rounded up. What remains
open is technical and belongs to the first tickets: the exact call that draws goods from the
network stock, the `PoiType` registration API and block state set, vanilla's POI search reach, the
`WORK` priority slot and memory sets for the mixin, and a nugget's xp value in Create Fly.
