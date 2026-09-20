---
schema_version: 1
id: 01M2YJF4J3P0ESQSH0Y3S7TYY3
key: VC-13
type: bug
title: "Long-distance trip: goods leave the stock but the payment never lands in the ticker's box"
created_by: kevin
created_at: 2026-09-20T04:51:22Z
---

## Scope

On a long-distance trip in a real village (Kevin, 2026-09-20): the villager arrived and the goods (wheat) left the stock, but the price and the nuggets never appeared in the stock ticker's payment box. The short-distance case works. A unit shall be atomic: either the goods leave and the payment lands, or nothing moves (`TRANSACTION-REQ-004..007`, `TRANSACTION-FAIL-003`).

## Approach

Two parts. (1) Robustness, certain: `TransactionExecutor.execute` ignores the list `ContainerExtension.insert(payment)` returns; if anything comes back, the payment was not (fully) stored. Reorder and verify: draw the goods; insert the payment and check the leftovers; if any, put the leftovers back? No: put the drawn goods back (the existing `returnDrawnStack` path) and remove what did land (or insert the payment first, then draw, and on a short draw remove the payment again: pick the order whose rollback is simplest and prove it). Also call `ticker.setChanged()`/`notifyUpdate()` after a successful insert so the box persists and syncs. (2) Cause, to find: reproduce with the villager starting far away (a game test cannot unload chunks, so reason from code): candidates: the `Shop` view's ticker block entity being a stale instance after the ticker's chunk was unloaded and reloaded during the walk (`Shop.at` is recomputed at arrival, so check whether `TransactionExecutor` receives the fresh ticker or one captured earlier by `ShoppingTripBehavior`/`CustomerHooks.Search`); `countSpace` passing while `insert` refuses (read `ContainerMixin.insert` for `StockTickerInventory`: does it honour `canPlaceItem`/slot filters, is the payment box limited to certain items, does it need the ticker to be linked?); a second stock ticker on the same frequency receiving the payment instead (the box the cloth points at versus the one Kevin looked at); the `receivedPayments` container being replaced by a `read()` during the interaction. Record the finding in the ticket; add a game test that reproduces whichever it is if a game test can (e.g. two tickers on one frequency: the payment must land in the cloth's ticker).

## Acceptance criteria

- [ ] `TransactionExecutor` verifies the payment insert; on leftovers the unit is rolled back (goods returned, partial payment removed) and reported as `BOX_FULL`; a game test forces a partial insert and asserts nothing moved.
- [ ] The cause of the long-distance loss is identified in the ticket's Findings with evidence, fixed, and covered by a game test where possible.
- [ ] `just client`: the long-distance trip lands price and nuggets in the cloth's ticker (Kevin's check).
- [ ] Merged through a Forgejo pull request into `development`.

## Constraints and prior findings

VC-3's Findings (draw path, `countSpace`, `insert`), VC-11's (walk), VC-12's (orb). Create Fly's `StockTickerBlockEntity.receivedPayments` is a `StockTickerInventory`; the ticker's own click hands the box's contents to the player.
