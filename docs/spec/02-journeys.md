---
title: "create_villager_customers spec — journeys: the use cases end to end"
type: "spec"
category: "create_villager_customers"
---

# 02 — Journeys

Every step names who acts. `UC` ids are flat across the project; domain files reference them.
Every trade in these journeys follows one rule: the table cloth's goods equal the matched offer's
cost stack, and the table cloth's price equals the matched offer's result stack
(`domains/transaction.md` `TRANSACTION-REQ-001`). "Goods" and "price" name the cloth's two sides;
which one is currency and which is a raw good depends only on what the offer says.

### `UC-001` — A farmer buys wheat

Actor: farmer villager (`ACTORS-001`) · Goal: execute its own "20 wheat for 1 emerald" offer

| Step | Actor | Action |
|---|---|---|
| 1 | server | The villager's offers restock; the server rolls the configured chance and it succeeds (`CUSTOMER-REQ-002`). |
| 2 | server | Searches the village's shop POIs for a table cloth whose goods are 20 wheat and whose price is 1 emerald, matching the offer's cost and result; finds one (`SHOP-REQ-001`–`003`). |
| 3 | server | Remembers the shop's position; walks the villager to it (`CUSTOMER-REQ-004`). |
| 4 | server | On arrival, checks the stock ticker's summary covers 20 wheat and the payment box has room for 1 emerald plus xp nuggets (`TRANSACTION-REQ-002`, `003`). |
| 5 | server | Draws 20 wheat from the network stock, inserts 1 emerald and the xp nuggets into the payment box, increases the offer's uses, grants the villager's trade xp (`TRANSACTION-REQ-004`–`007`). |
| 6 | server | Repeats step 5 while the offer still has uses and the shop still has stock; stops when either runs out. |
| 7 | villager | Resumes ordinary `WORK` behaviour. |

Fails when: no matching shop is found → the villager stays idle (`CUSTOMER-FAIL-004`).

### `UC-002` — A librarian sells a book (the reverse direction as the same act)

Actor: librarian villager · Goal: execute its own "N emeralds for a book" offer

| Step | Actor | Action |
|---|---|---|
| 1 | server | Restock roll succeeds. |
| 2 | server | Finds a table cloth whose goods are N emeralds and whose price is 1 book, matching the offer's cost (emeralds) and result (a book). |
| 3 | server | Walks the villager to it. |
| 4 | server | On arrival, checks the stock ticker's summary covers N emeralds and the payment box has room for 1 book plus xp nuggets. |
| 5 | server | Draws N emeralds from the network stock, inserts the book and the xp nuggets into the payment box, increases the offer's uses, grants trade xp. |
| 6 | villager | Resumes `WORK`. |

Same `TRANSACTION-REQ` steps as `UC-001`, run with goods and price swapped: here currency leaves
the shop's stock and a crafted good enters the payment box — the reverse flow, one mechanism
(`decisions/DEC-005-one-mechanism.md`).

### `UC-003` — The shop runs out of stock

Actor: server · Goal: stop cleanly when a trip's shop can no longer supply the goods

| Step | Actor | Action |
|---|---|---|
| 1 | server | Villager arrives with a matched offer that still has uses left. |
| 2 | server | Executes units one at a time, draining the shop's stock. |
| 3 | server | At some unit, the stock ticker's summary no longer covers the goods stack. |
| 4 | server | Stops the loop; units already completed stand; the offer keeps its remaining uses. |
| 5 | villager | Resumes `WORK` with no error (`TRANSACTION-FAIL-002`). |

### `UC-004` — The payment box is full

| Step | Actor | Action |
|---|---|---|
| 1 | server | Villager arrives and begins executing units. |
| 2 | server | At some unit, the payment box has no room for the price plus the xp nuggets. |
| 3 | server | Refuses that unit ("cash register full"); stops the loop; earlier units stand (`TRANSACTION-FAIL-003`). |
| 4 | player | Empties the payment box on their own schedule, as with any Create Fly shop. |
| 5 | villager | Resumes `WORK`; may return on a later restock. |

### `UC-005` — Restock and the random decision

Actor: server · Goal: decide, once per restock, whether this villager goes shopping at all

| Step | Actor | Action |
|---|---|---|
| 1 | server | The villager's offers restock (vanilla `Villager.restock()`/`shouldRestock()`). |
| 2 | server | Rolls the configured chance once for this restock (`decisions/DEC-009-chance-per-restock.md`). |
| 3 | server | On failure, nothing happens; the villager continues `WORK` normally. |
| 4 | server | On success, searches for a matching shop as in `UC-001` step 2. |

### `UC-006` — The owner reads what happened

Actor: shop owner (player) · Goal: see that villagers have been trading, with no new UI

| Step | Actor | Action |
|---|---|---|
| 1 | player | Opens the stock ticker's own request or stock screen; sees the network's stock lower than before. |
| 2 | player | Opens the payment box; sees emeralds, goods, or books accumulated alongside xp nuggets. |
| 3 | player | Empties the payment box by hand, exactly as with any Create Fly shop (`SHOP-REQ-005`). |

### `UC-007` — A shop is removed while a villager walks to it

Actor: server, shop owner · Goal: fail the trip without a stuck villager or a half-done trade

| Step | Actor | Action |
|---|---|---|
| 1 | server | A villager has a trip target remembered and is walking to it. |
| 2 | player (or anything) | Breaks the table cloth, or unlinks or removes its stock ticker, before arrival. |
| 3 | server | On arrival (or an earlier recheck), finds the target no longer counts as a shop (`SHOP-REQ-006`). |
| 4 | server | Cancels the trip, clears the trip memory, starts the cooldown (`CUSTOMER-REQ-007`). |
| 5 | villager | Resumes ordinary `WORK` behaviour with no error and no partial transaction. |
