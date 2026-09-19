---
schema_version: 1
id: 01M2W6ETM2M7Y19ZMAYAJFB6RT
key: VC-3
type: feat
title: "The transaction: match rule, direct draw, payment, xp nuggets"
created_by: kevin
created_at: 2026-09-19T06:43:00Z
---

## Scope

The rule that decides whether an offer and a table cloth mirror each other, and what happens when a villager executes that match (`docs/spec/domains/transaction.md`, `TRANSACTION-REQ-001..010`; `04-architecture.md` `ARCH-DEC-004`; `decisions/DEC-005-one-mechanism.md`, `DEC-006-direct-draw.md`, `DEC-007-xp-nuggets.md`). The match rule lives in the pure package `villager_customers.model`: the cloth's goods stack equals the offer's cost stack (item and count), the cloth's price stack equals the offer's result stack, and an offer whose cost has a second item (`getItemCostB()` present) never matches (`TRANSACTION-REQ-001`, `008`; `DEC-005`, one mechanism covers both trade directions) — with unit tests. The execution against a real shop: a stock check through the stock ticker's summary (`TRANSACTION-REQ-002`), the direct draw of the goods stack from the network stock with no package and no address (`TRANSACTION-REQ-004`; `DEC-006`) — first step: find the Create-internal call that removes items from a logistics network's inventories without producing a package, record the finding in this ticket, and add an accessor mixin into `create` only if no public call exists (`ARCH-DEC-004`, the mod's largest open risk) — payment of the price stack plus xp nuggets into `receivedPayments` via `Container.insert` (`TRANSACTION-REQ-005`) with the "cash register full" refusal when the box has no room (`TRANSACTION-FAIL-003`), `increaseUses()` and the villager's trade xp granted exactly once per unit (`TRANSACTION-REQ-006`), demand and reputation left untouched (`TRANSACTION-REQ-009`), and the nugget count computed as the offer's xp divided by one nugget's value rounded up — read `AllItems.EXP_NUGGET`'s xp value at this ticket (`TRANSACTION-REQ-010`; `DEC-007`). The loop repeats per visit while uses and stock allow, stopping at the first refusal with earlier units standing (`TRANSACTION-REQ-007`). Out of scope: how the villager got here (`VC-4`) and what makes a table cloth count as a shop in the first place (`VC-2`).

## Approach

First thing to verify, and the ticket's largest risk: the exact Create-internal call that removes a unit's goods from the network stock. Read `StockTickerBlockEntity` and its packager-network/`InventorySummary` neighbours with `javap -p -c` alongside `getAccurateSummary()`/`getRecentSummary()` (both confirmed present by the research note) for a parallel public removal method; if none is public, add a narrow accessor mixin into the relevant Create Fly class rather than widening the target, and record which path was taken here for `04-architecture.md`'s benefit. Classes: `villager_customers.model.MatchRule` (pure, no Minecraft imports, checked by `verifyPurePackage`) taking cost/result/goods/price as plain value shapes; `villager_customers.model.NuggetConversion` (pure, the rounding-up division); `villager_customers.transaction.TransactionExecutor` (the per-unit loop against a `Shop` from `VC-2` and a `MerchantOffer`, driving the removal call, `Container.insert`, `increaseUses()`, `notifyTrade()`). Second thing to verify: `AllItems.EXP_NUGGET`'s actual xp value in this Create Fly build (proposed default 3, per upstream Create, per `DEC-007`) — read it here rather than assume it.

## Acceptance criteria

- [ ] Unit tests for the match rule: cloth goods = offer's cost stack and cloth price = offer's result stack matches; any mismatch in item or count does not match; an offer whose cost has a second item never matches, regardless of the first item (`TRANSACTION-REQ-001`, `008`).
- [ ] Unit tests for the xp-nugget conversion: the offer's xp divided by one nugget's confirmed value, rounded up, including an exact-multiple case and a remainder case (`TRANSACTION-REQ-010`).
- [ ] `verifyPurePackage` passes on `villager_customers.model` with `MatchRule` and `NuggetConversion` inside it.
- [ ] Game test with a mock villager and a small real shop network (table cloth, stock ticker, a packager with a chest of goods): a unit completes — the goods leave the chest, the payment box holds the price and the xp nuggets, the offer's uses increase by one, the villager's trade xp is granted exactly once (`TRANSACTION-REQ-004..007`).
- [ ] Game test: refusal when stock is short — the loop stops, units already completed stand, the offer keeps its remaining uses, no error (`TRANSACTION-FAIL-002`).
- [ ] Game test: refusal when the payment box is full — the loop stops ("cash register full"), earlier units stand (`TRANSACTION-FAIL-003`).
- [ ] Demand (`MerchantOffer.getDemand()`/`updateDemand()`) and villager-player reputation confirmed untouched by a mod-driven unit, proven by asserting both are unchanged across a completed unit in a game test (`TRANSACTION-REQ-009`).
- [ ] The removal-call finding (public call used, or the accessor mixin added and what it targets) is written into this ticket's Constraints section before it is marked done.

## Constraints and prior findings

`04-architecture.md` `ARCH-DEC-004`: "the largest open risk in this mod" — the research pass confirmed `getAccurateSummary()`/`getRecentSummary()` (stock reading) and `Container.insert(List<ItemStack>)` (payment writing, already used by the real player checkout) as public, but did not trace a parallel removal call; `ARCH-FAIL-002` names the fallback (a further mixin into Create Fly's network or summary classes) if none is public. `DEC-006`: no `ShoppingList`, no `PackageOrder`, no address, no asynchronous delivery — goods vanish with the villager exactly as in vanilla trading, the same way a villager already "receives" goods in a vanilla trade. `DEC-007`: nugget count is a data constant (`contracts/public-surface.md`) once the per-nugget xp value is read here; experience bottles are the fallback only if nuggets prove unusable. `TRANSACTION-DEC-002`: demand and reputation stay untouched by design (Kevin, by omission from the rulings), not an oversight — a mod-driven trade never calls `updateDemand()` and touches no player-villager relationship, since no player is involved. Blocked by `VC-1` (the Gradle project, toolchain and `villager_customers.mixins.json` scaffold this ticket's accessor mixin, if needed, is added to).
