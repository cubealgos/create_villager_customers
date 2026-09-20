---
title: "create_villager_customers spec — TRANSACTION: the match rule and its execution"
type: "spec"
category: "create_villager_customers"
---

# `TRANSACTION` — executing a matched offer against a shop

## 1. Purpose

The rule that decides whether an offer and a table cloth mirror each other, and what happens when
a villager executes that match: the stock check, the direct draw, the payment, the xp nuggets, and
every way a unit can be refused. Not how the villager got there (`domains/customer.md`) and not
what makes a table cloth count as a shop in the first place (`domains/shop.md`).

## 2. Dimensions

| Dimension | Answer |
|---|---|
| **Actors** | The villager (`ACTORS-001`) executes; the server (`ACTORS-004`) runs the check-and-transfer loop; Create Fly (`ACTORS-005`) owns the network stock and the payment box the loop reads and writes. |
| **Over time** | Candidate found → matched → per unit: stock checked, box capacity checked → drawn, paid, xp'd, uses increased → loop continues or stops (out of uses, out of stock, box full) → offer's state left however the loop ended. |
| **Multiplicity** | One villager visit may execute 1..n units of one matched offer, until the offer's uses or the shop's stock or the payment box runs out, whichever first. Zero units execute if the very first check fails. |
| **Unwanted** | An offer whose cost has a second item (never matches, `TRANSACTION-REQ-008`); the payment box full mid-loop; the network stock too low mid-loop; a table cloth whose linked stock ticker has no keeper present (not a candidate at all, `SHOP-REQ-003`). |
| **Not-you** | The shop owner never interacts with a single unit; they see the results later, through Create's own screens (`UC-006`). A player watching a live trade sees it happen with no menu opening, unlike a player's own checkout. |

## 3. Enumerations

### Match rule

| Offer shape | Cloth shape | Matches? |
|---|---|---|
| single-item cost, any result | goods = offer's cost stack, price = offer's result stack | ✅ |
| single-item cost, any result | goods or price differ in item or count | ❌ |
| cost has a second item (`getItemCostB()` present) | any | ❌, never considered (`TRANSACTION-REQ-008`) |
| offer out of uses | any | ❌, not a candidate |

### Per-unit execution

| from ↓ / to → | `checking` | `done` | `refused` |
|---|---|---|---|
| **`checking`** | — | ✅ stock and box both sufficient | ✅ stock too low or box full |
| **`done`** | ✅ another unit begins, if uses and stock remain | — | — |
| **`refused`** | — | — | — (loop stops; earlier `done` units stand) |

## 4. Use cases

`UC-001`, `UC-002`, `UC-003`, `UC-004` in `02-journeys.md`.

## 5. Requirements

| ID | Requirement | Priority | From |
|---|---|---|---|
| `TRANSACTION-REQ-001` | The system shall consider an offer and a table cloth matched when the cloth's goods equal the offer's cost stack (item and count) and the cloth's price equals the offer's result stack (item and count). | Must | `rulings-2026-09-19.md` |
| `TRANSACTION-REQ-002` | Before executing a unit, the system shall confirm the stock ticker's stock summary covers the cloth's goods stack. | Must | `UC-003` |
| `TRANSACTION-REQ-003` | Before executing a unit, the system shall confirm the payment box has room for the cloth's price stack plus the unit's xp nuggets. | Must | `UC-004` |
| `TRANSACTION-REQ-004` | When both checks pass, the system shall draw the cloth's goods stack directly from the network's stock; the goods vanish with the villager, as in vanilla trading. The exact removal call is confirmed at the first ticket (`04-architecture.md` `ARCH-DEC-004`). | Must | `decisions/DEC-006-direct-draw.md` |
| `TRANSACTION-REQ-005` | When both checks pass, the system shall insert the cloth's price stack and the unit's xp nuggets into the stock ticker's payment box (`Container.insert`). | Must | `decisions/DEC-007-xp-nuggets.md` |
| `TRANSACTION-REQ-006` | When a unit completes, the system shall call the offer's `increaseUses()` and grant the villager's trade xp (`notifyTrade` or its equivalent) exactly once. | Must | Research §B |
| `TRANSACTION-REQ-007` | While an offer still has uses left and the shop can still supply a unit, the system shall repeat steps `004`–`006` for the same visit; it shall stop as soon as either runs out or a unit is refused. | Must | `00-context.md` |
| `TRANSACTION-REQ-008` | The system shall never match an offer whose cost has a second item (`getItemCostB()` present): a table cloth's price is one item. | Must | Research §A |
| `TRANSACTION-REQ-009` | The system shall leave `MerchantOffer.getDemand()`/`updateDemand()` and villager-player reputation untouched by a mod-driven unit. | Must | `TRANSACTION-DEC-002` |
| `TRANSACTION-REQ-010` | When a unit completes, the system shall spawn no experience orb (vanilla's `rewardTradeXp` orb, 3 to 6 xp, is suppressed for the mod-driven unit) and shall insert experience nuggets worth that orb's roll instead: `3 + random(4)` xp divided by one nugget's 3 xp, rounded up, so one or two nuggets per unit; the villager's own levelling xp is unchanged (Kevin, 2026-09-19 and 2026-09-20; `decisions/DEC-007-xp-nuggets.md`). | Must | `decisions/DEC-007-xp-nuggets.md` |
| `TRANSACTION-REQ-011` | The match shall honour the offer's cost predicate: the shop's goods stack must satisfy `ItemCost.test` (its item id and its component predicate), on top of the plain shape match, and the draw shall take only stacks that satisfy it. | Must | `create_firearms` `decisions/DEC-016-villager-customers-requirement.md`, Kevin 2026-09-20 |

## 6. Failure modes

| ID | Failure | Response |
|---|---|---|
| `TRANSACTION-FAIL-001` | Offer's cost has a second item | Never matched; not visible as an error, simply never a candidate. |
| `TRANSACTION-FAIL-002` | Stock too low mid-loop | Loop stops; units already completed stand; offer keeps its remaining uses (`UC-003`). |
| `TRANSACTION-FAIL-003` | Payment box full mid-loop | Loop stops ("cash register full"); units already completed stand (`UC-004`). |
| `TRANSACTION-FAIL-004` | Table cloth's stock ticker has no keeper present | Not a candidate at all — caught by `domains/shop.md`, not reached here. |
| `TRANSACTION-FAIL-005` | The stock ticker shows no payments tooltip after a unit | Create Fly's port never renders its `StockTickerTooltipBehaviour` (no tooltip for a player purchase either; Kevin, 2026-09-20, VC-17). Known upstream gap, ruled "do nothing": the payment is in the box and right-click withdraws it; no tooltip of this mod's own. |

## 7. Open questions

| Question | Blocks | Decided by |
|---|---|---|
| The exact Create-internal call that removes goods from the network stock | `TRANSACTION-REQ-004` | first ticket (`ARCH-DEC-004`), largest open risk in the mod |
| The exact xp value one `create:experience_nugget` represents, and whether more than one nugget per unit is ever warranted | `TRANSACTION-REQ-010` | first ticket reads `AllItems.EXP_NUGGET`'s usage in Create Fly |

## 8. Decisions

- `TRANSACTION-DEC-001` — **Direct draw, not a package** (`decisions/DEC-006-direct-draw.md`):
  restated here as the mechanism this domain executes against.
- `TRANSACTION-DEC-002` — **Demand and reputation stay untouched** (Kevin, 2026-09-19, by omission
  from the rulings: only the payment, the stock, the uses and the xp are named). A mod-driven trade
  never calls `updateDemand()` and never touches player-villager reputation, since no player is
  involved. **Cost if wrong:** if Kevin later wants villager trades to feel demand pressure from
  mod-driven trades too, this is one added call, not a redesign.
- `TRANSACTION-DEC-003` — **The offer's component predicate is honoured, not just its plain shape**
  (Kevin, 2026-09-20, citing `create_firearms`'s
  `decisions/DEC-016-villager-customers-requirement.md`): `StackShape`'s id-and-count match alone let
  a shop selling *any* configuration of an item satisfy an offer wanting one specific configuration
  (e.g. a specific enchanted book, or a firearm with specific attachments). `TRANSACTION-REQ-011`
  closes this by checking `offer.getItemCostA().test(...)` against the shop's own real,
  component-bearing goods stack, and by drawing that same real stack rather than one synthesised
  from the offer alone. **Cost if wrong:** none beyond this ticket — this is the
  `create_villager_customers` half of a cross-project requirement `create_firearms` already recorded
  as blocking on it.
