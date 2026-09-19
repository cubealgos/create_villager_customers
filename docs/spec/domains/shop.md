---
title: "create_villager_customers spec — SHOP: what counts as a shop and how villagers find one"
type: "spec"
category: "create_villager_customers"
---

# `SHOP` — finding a table cloth that counts

## 1. Purpose

What makes a table cloth a shop for this mod's purposes, how villagers discover one, how far a
search reaches, and what the owner sees. Not the match against a specific offer
(`domains/transaction.md`) and not the trip that gets a villager there (`domains/customer.md`).

## 2. Dimensions

| Dimension | Answer |
|---|---|
| **Actors** | The shop owner (`ACTORS-002`) builds and stocks it, exactly as for any Create Fly shop; the villager (`ACTORS-001`) reads it as a search target; the server (`ACTORS-004`) maintains the POI registry and the live shophood check; Create Fly (`ACTORS-005`) implements the table cloth, the stock ticker and the network underneath. |
| **Over time** | A table cloth's block state is POI-registered the moment it exists in the world; it only counts as an active shop once it has a price and goods and a keeper-present stock ticker linked; it stops counting the moment any of those breaks, without needing to be unregistered as a POI. |
| **Multiplicity** | Zero to many table cloths per village; zero means every search fails and villagers stay idle (`CUSTOMER-FAIL-004`); many that match the same offer means one is chosen (`SHOP-DEC-001`, tie-break). A table cloth outside any village is invisible to this search, by design. |
| **Unwanted** | A table cloth mid-construction, with no price yet; a linked stock ticker whose keeper is absent; a table cloth the player built far outside any village's reach. None of these are errors — they are simply not candidates. |
| **Not-you** | A player building outside a village's bounds gets no customers until a village forms or reaches them — same as vanilla job-site discovery. The owner does nothing new: no setting, no claim, no registration step of their own. |

## 3. Enumerations

### Shop candidacy

| from ↓ / to → | `not-a-shop` | `candidate` |
|---|---|---|
| **`not-a-shop`** (POI-registered block state, nothing more) | — | ✅ price and goods set, linked ticker has a keeper present |
| **`candidate`** | ✅ price cleared, link broken, ticker's keeper leaves, or the block is broken | — |

A `candidate` becoming `not-a-shop` while a villager is mid-trip to it is `UC-007`, handled by
`domains/customer.md` `CUSTOMER-REQ-007`.

### Cardinality

| Relation | Count | At zero | At the top |
|---|---|---|---|
| table cloth → linked stock ticker | 0 or 1, required for candidacy | not a candidate | N/A |
| table cloth → price tag | 0 or 1 item+count, required for candidacy | not a candidate | N/A |
| village → shop POIs | 0..n | every search fails | as many as the player builds |
| offer → matching candidates found in one search | 0..n | villager stays idle | nearest wins (`SHOP-DEC-001`) |

## 4. Use cases

`UC-003`, `UC-006`, `UC-007` in `02-journeys.md`.

## 5. Requirements

| ID | Requirement | Priority | From |
|---|---|---|---|
| `SHOP-REQ-001` | The system shall register a point-of-interest type over Create Fly's table cloth block states, so villagers discover shops through the village POI index used for job sites. The exact registration API and block state set are confirmed at the first ticket. | Must | `decisions/DEC-008-poi.md` |
| `SHOP-REQ-002` | The system shall consider a table cloth's POI a candidate only while it has a non-empty price and goods and is linked to a stock ticker. | Must | Shop candidacy |
| `SHOP-REQ-003` | The system shall further require that linked stock ticker report a keeper present, mirroring Create's own `isKeeperPresent()` check for a real player's checkout. | Must | Research §A |
| `SHOP-REQ-004` | The system shall restrict a villager's shop search to POIs within its own village; no distance beyond vanilla's own village/POI search reach is added. The exact figure is confirmed at the first ticket. | Must | `rulings-2026-09-19.md` |
| `SHOP-REQ-005` | The system shall add no screen, block or item of its own for the owner: the payment box and Create's own stock ticker screens remain the only views onto a shop's activity. | Must | `00-context.md` |
| `SHOP-REQ-006` | If a candidate loses its shophood (price cleared, link broken, keeper absent, or the block broken) between a villager's search and its arrival, then the system shall treat it as `not-a-shop` at arrival and defer to `domains/customer.md`'s cancel path. | Must | `UC-007` |

## 6. Failure modes

| ID | Failure | Response |
|---|---|---|
| `SHOP-FAIL-001` | Table cloth built outside any village's reach | Invisible to search; not a bug, matches vanilla job-site discovery. |
| `SHOP-FAIL-002` | Linked stock ticker's keeper absent | Not a candidate; caught before a villager ever walks (`SHOP-REQ-003`). |
| `SHOP-FAIL-003` | Table cloth removed or unlinked mid-trip | `UC-007`; handled in `domains/customer.md`. |
| `SHOP-FAIL-004` | Multiple candidates match the same offer in one village | Tie-break to nearest (`SHOP-DEC-001`); no error either way. |

## 7. Open questions

| Question | Blocks | Decided by |
|---|---|---|
| Exact `PoiType` registration API and the full table-cloth block state set | `SHOP-REQ-001` | first ticket |
| Vanilla's own POI/village search reach figure | `SHOP-REQ-004` | first ticket |

## 8. Decisions

- `SHOP-DEC-001` — **Nearest matching candidate wins** (Kevin, 2026-09-19):
  when more than one shop in a village matches the same offer, the villager walks to the closest
  one. **Cost if wrong:** a different tie-break (first found, weighted, round-robin) is a small
  change to the search step in `domains/customer.md`, not a redesign.
- `SHOP-DEC-002` — **A live check, not a cached registration** (from `ARCH-DEC-005`'s "no
  persistent state of the mod's own"): shophood is computed from the table cloth's current price,
  goods and linked ticker every time it is checked, never cached across ticks. **Cost if wrong:**
  a cheap recomputation on every check versus a cache that could go stale exactly when `UC-007`
  needs it to be fresh.
