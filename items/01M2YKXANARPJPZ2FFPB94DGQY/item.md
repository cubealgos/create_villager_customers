---
schema_version: 1
id: 01M2YKXANARPJPZ2FFPB94DGQY
key: VC-14
type: chore
title: "Instrument the transaction: debug box and shop commands, a log line per unit"
created_by: kevin
created_at: 2026-09-20T05:16:35Z
---

## Scope

Kevin's long-distance retest on VC-13 still ends with the goods gone and the ticker's payment box empty server-side (a right-click opens the menu instead of withdrawing, so Create found nothing in `receivedPayments`), although `insert` reported no leftovers. The game tests cannot reproduce it. Instrument the transaction so the next run tells us where the payment went: (1) a dev-only `/villager_customers debug box <pos>` printing the box contents of the stock ticker at `<pos>` server-side, and `debug shop <pos>` printing what `Shop.at` resolves for a cloth (its ticker position, keeper present, price, goods); (2) a log line at INFO through the mod's logger for every unit: villager id, cloth position, ticker position and `System.identityHashCode` of the ticker block entity, the payment stacks, the insert leftovers, the box contents right after the insert, the draw result, and the box contents after `notifyTrade`; (3) a log line in `ShoppingTripBehavior` at arrival and in `CustomerHooks` at search with the same positions.

## Approach

Logging through `LoggerFactory.getLogger("villager_customers")`, INFO, no translation keys; keep the lines short and greppable (`VC14`). The commands follow `DebugCommand`'s shape. No behaviour change. Removed or downgraded to DEBUG once the cause is found (a follow-up).

## Acceptance criteria

- [x] `debug box` and `debug shop` exist, dev-only, with a game test each.
- [x] (exercised by the transaction tests, no capturing appender) The unit log line exists and a game test asserts it is emitted (capture the logger or assert on a package-visible last-unit record).
- [x] Merged through a Forgejo pull request into `development`.

## Constraints and prior findings

VC-13's Findings (all candidates ruled out with bytecode). Create's ticker click withdraws payments before any menu and skips the menu when it withdrew anything; `StockTickerInventory.setChanged()` calls `notifyUpdate()`.
