---
title: "create_villager_customers spec — CUSTOMER: the villager's decision and trip"
type: "spec"
category: "create_villager_customers"
---

# `CUSTOMER` — the villager's decision and trip

## 1. Purpose

When a villager decides to go shopping, how it finds a target, how it walks there, and every way
that trip can end. Not the transaction it runs on arrival (`domains/transaction.md`) and not what
makes a position a valid target (`domains/shop.md`).

## 2. Dimensions

| Dimension | Answer |
|---|---|
| **Actors** | The villager (`ACTORS-001`) carries the trip; the server (`ACTORS-004`) rolls the chance, searches, walks it, and hands off to `TRANSACTION` on arrival. Create Fly is passive here — nothing is read from a shop until arrival. |
| **Over time** | Idle → (restock) chance rolled → fail: idle again until the next restock; success → target remembered → walking → arrived (hands off to `TRANSACTION`) or cancelled/timed out → idle, with a cooldown before the next roll. |
| **Multiplicity** | One villager, at most one active trip at a time. Zero shops in range means the roll finds nothing and the villager stays idle; many candidate shops means one is chosen (`SHOP-REQ-006` for the tie-break). |
| **Unwanted** | Panic mid-walk; night falling mid-walk; the target losing its shophood before arrival (`UC-007`); the offer running out of uses before arrival; the walk exceeding its timeout. |
| **Not-you** | A server admin watching a villager sees nothing new until it detours to a shop. A player who never builds a shop sees no change in villager behaviour at all. A villager with no offers, or only two-cost offers, never rolls into a visible trip. |

## 3. Enumerations

### Trip state

| from ↓ / to → | `idle` | `walking` | `cancelled` |
|---|---|---|---|
| **`idle`** | — | ✅ restock, chance succeeds, a matching shop is found | — |
| **`walking`** | ✅ arrives (hands off to `TRANSACTION`, then clears to `idle`) | — | ✅ panic, night, timeout, or target loses shophood (`UC-007`) |
| **`cancelled`** | ✅ cooldown expires | — | — |

### Cardinality

| Relation | Count | At zero | At the top |
|---|---|---|---|
| villager → active trip memory | 0 or 1 | idle | one, never more (`CUSTOMER-REQ-008`) |
| villager → offers considered per roll | 0..n, only offers with uses left and a single-item cost | none matches: stays idle | all of a villager's offers, in vanilla's own order |
| villager → cooldown | 0 or 1 pending value | may roll on the next restock | bounds retries after a cancelled trip |

## 4. Use cases

`UC-001`, `UC-002`, `UC-005`, `UC-007` in `02-journeys.md`.

## 5. Requirements

| ID | Requirement | Priority | From |
|---|---|---|---|
| `CUSTOMER-REQ-001` | While a villager's current activity is `WORK`, the system shall run the shopping-trip behaviour as one candidate in `WORK`'s priority set (`ARCH-DEC-002`). | Must | `UC-005` |
| `CUSTOMER-REQ-002` | When a villager's offers restock, the system shall roll the configured chance once for that restock: default 50% (Kevin, 2026-09-19; `decisions/DEC-009-chance-per-restock.md`). | Must | `UC-005` |
| `CUSTOMER-REQ-003` | When the roll succeeds, the system shall search the village's shop POIs (`domains/shop.md`) for one whose goods and price match any of the villager's offers with uses remaining, and remember the chosen one's position as the trip target. | Must | `UC-001` **Amended (`DEC-010`, Kevin, 2026-09-20):** the search origin is the villager's remembered meeting point (bell) when it has one, else its position, and the radius is 128 blocks horizontally (config `shop_search_radius`), replacing the 48-block job-site range. |
| `CUSTOMER-REQ-004` | When a trip target is remembered, the system shall walk the villager to it, stopping within 2 blocks of the target, giving up after 2400 ticks (Kevin, 2026-09-19), and considering the target too far beyond the village's own POI reach. | Must | `UC-001` |
| `CUSTOMER-REQ-005` | When the villager arrives within that distance, the system shall hand off to `domains/transaction.md` to execute the matched offer(s), then clear the trip memory. | Must | `UC-001` |
| `CUSTOMER-REQ-006` | While a villager is panicking or its activity is not `WORK`, the system shall not start a trip, and shall cancel and clear any trip in progress. | Must | Unwanted |
| `CUSTOMER-REQ-007` | If the walk exceeds its timeout, or the target loses its shophood before arrival, then the system shall cancel the trip, clear the memory, and start a 2400-tick cooldown (Kevin, 2026-09-19) before the next roll. | Must | `UC-007` |
| `CUSTOMER-REQ-008` | The system shall allow a villager at most one active trip at a time. | Must | Multiplicity |
| `CUSTOMER-REQ-009` | Where a villager has no offer with uses left, or no offer matches a discoverable shop, the system shall leave the villager idle after the roll, with no error and no retry before the next restock. | Must | `UC-005` |

## 6. Failure modes

| ID | Failure | Response |
|---|---|---|
| `CUSTOMER-FAIL-001` | Target shop removed or unlinked mid-walk | `CUSTOMER-REQ-007`; see `UC-007`. |
| `CUSTOMER-FAIL-002` | The matched offer runs out of uses before arrival | Re-checked on arrival by `TRANSACTION`; if no offer still matches, the trip ends with nothing executed, no error. |
| `CUSTOMER-FAIL-003` | Villager panics or night falls mid-walk | Cancelled; no partial transaction, since nothing is drawn until arrival. |
| `CUSTOMER-FAIL-004` | No matching shop found on the roll | Villager stays idle; no error, no retry until the next restock. |

## 7. Open questions

| Question | Blocks | Decided by |
|---|---|---|
| Exact `WORK` priority slot and memory entry-condition/clear sets for the mixin | `CUSTOMER-REQ-001` | first ticket, `04-architecture.md` `ARCH-DEC-002` |

## 8. Decisions

- `CUSTOMER-DEC-001` — **A per-restock chance, not a per-offer one** (this sheet's reading of
  `rulings-2026-09-19.md`: "each restock only gives a random chance that the villager decides to
  wander to a shop at all"). One roll decides whether the villager searches at all; the search
  itself then considers every eligible offer. **Cost if wrong:** if Kevin means one roll per offer,
  `CUSTOMER-REQ-002` and `CUSTOMER-REQ-003` split into a per-offer loop instead — a small rewrite,
  not a redesign.
- `CUSTOMER-DEC-002` — **A cooldown independent of the restock cycle** (`ARCH-DEC-005`): without
  it, a trip cancelled by `UC-007` could retry on the very next restock check against the same
  broken shop. **Cost if wrong:** one extra memory module and a comparison; cheap either way.
