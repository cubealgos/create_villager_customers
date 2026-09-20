---
title: "create_villager_customers spec — KEEPER: nitwits as shopkeepers"
type: "spec"
category: "create_villager_customers"
---

# `KEEPER` — nitwit villagers as shopkeepers

## 1. Purpose

How a nitwit enters the population, how an adult nitwit finds and claims a free seat next to a
keeperless stock ticker, how it walks there and gets seated, and every way that can end. Not the
shopping trip a trading villager takes (`domains/customer.md`), and not what makes a table cloth
count as a shop for a *customer's* purposes (`domains/shop.md`) — this domain only supplies the
`isKeeperPresent()` gate those two already depend on (`SHOP-REQ-003`). `decisions/DEC-011-nitwit-keepers.md`
records the ruling this domain implements.

## 2. Dimensions

| Dimension | Answer |
|---|---|
| **Actors** | The nitwit villager (`01-actors.md` `ACTORS-009`) carries the seek and the walk; the server (`ACTORS-004`) rolls the breeding chance, runs the periodic seek, resolves claims, and owns the seek-cooldown memory; Create Fly (`ACTORS-005`) supplies the seat block, its auto-seat trigger, and `isKeeperPresent()` — passively, exactly as it is passive toward `CUSTOMER` until a walk-target lands on its block. |
| **Over time** | Bred baby → (10% roll at birth) nitwit, still a baby → grows up (vanilla ageing, untouched by this mod) → adult nitwit, idle → periodic seek → seat found and claimed → walking → seated (stays day and night; the brain keeps running underneath but `SeatEntity` pins its position, research §G.1) → ejected or its seat/ticker breaks → cooldown → idle again. |
| **Multiplicity** | One nitwit, at most one active claim or seat at a time. Zero eligible seats in range means the seek finds nothing and the nitwit "behaves like normal nitwits" (Kevin, 2026-09-20) — no error, no special idle animation. Many eligible seats means one is claimed (nearest, mirroring `SHOP-DEC-001`). |
| **Unwanted** | Two nitwits claiming the same seat from concurrent seeks; a baby nitwit seeking; a non-nitwit unemployed villager seeking; the seat or its ticker breaking out from under a seated nitwit; a raid's panic state fighting the seat's position pin. |
| **Not-you** | A player who never breeds villagers, or breeds them and never gets a nitwit in ten tries, sees nothing new. A player with an idle nitwit standing around a shop with an unclaimed seat sees it eventually walk over on its own, with no interaction of their own required — a deliberate contrast with `CUSTOMER`'s villager, which the player never has to place either, but which at least trades because vanilla already gave it offers. |

## 3. Enumerations

### A keeper's state

`idle` below is this domain's name for what the ruling calls "an unemployed nitwit" behaving
normally — chosen to match `domains/customer.md`'s own `idle` state name rather than overload
"unemployed," which `01-actors.md` already uses for a villager with profession `NONE`, a
different actor that never enters this table at all (`KEEPER-REQ-014`).

| from ↓ / to → | `idle` | `seeking` | `walking` | `seated` | `cooldown` |
|---|---|---|---|---|---|
| **`idle`** | — | ✅ seek timer elapses, nitwit is an adult (`KEEPER-REQ-004`) | — | — | — |
| **`seeking`** | ✅ no eligible seat found in range | — | ✅ an eligible seat is found and claimed (`KEEPER-REQ-007`) | — | — |
| **`walking`** | — | — | — | ✅ walks onto the seat block; Create's own `SeatBlock.onEntityMovement` auto-seats it, no mod call (`KEEPER-REQ-009`) | ✅ walk timeout, or the claimed seat/ticker loses eligibility before arrival |
| **`seated`** | — | — | — | — (stays seated day and night; brain runs, position pinned) | ✅ ejected (player right-click) or the seat/ticker breaks under it (`KEEPER-REQ-011`, `012`) |
| **`cooldown`** | ✅ `keeper_seek_cooldown_ticks` elapses | — | — | — | — |

A baby nitwit never enters this table (`KEEPER-REQ-003`); it sits outside it entirely until it
grows up, at which point it starts at `idle`.

### Seat eligibility

| from ↓ / to → | `not-a-candidate` | `candidate` |
|---|---|---|
| **`not-a-candidate`** (any Create seat) | — | ✅ adjacent to a stock ticker per `isKeeperPresent()`'s own geometry (two below-offsets × four horizontal directions from the ticker, research §G.1), that ticker reports no keeper present, the seat is unoccupied, and no other nitwit's claim covers it |
| **`candidate`** | ✅ another nitwit claims it first, it becomes occupied, its ticker gains a keeper another way, or the seat or ticker is removed | — |

### Cardinality

| Relation | Count | At zero | At the top |
|---|---|---|---|
| village → nitwit population | 0..n | no keepers ever seek; village behaves exactly as before this domain | as many as breeding and worldgen produce |
| nitwit → active claim or seat | 0 or 1 | idle or cooling down | one, never more (mirrors `CUSTOMER-REQ-008`) |
| ticker → seats scanned by `isKeeperPresent()` | up to 8 (2×4, Create's own geometry) | ticker can never gain a keeper | unchanged by this mod; Create's own limit |
| seat → claiming nitwit | 0 or 1 | open to any nitwit's seek | exactly one; a second claim is refused (`KEEPER-REQ-007`) |

## 4. Use cases

`UC-008` in `02-journeys.md`.

## 5. Requirements

| ID | Requirement | Priority | From |
|---|---|---|---|
| `KEEPER-REQ-001` | When a villager baby is spawned by breeding (`EntitySpawnReason.BREEDING`), the system shall roll the configured chance (default 10%, `nitwit_breeding_chance`) and, on success, set the baby's `VillagerData` profession to `NITWIT` instead of vanilla's `NONE`, via the public `setVillagerData` (research §G.3, mixin on `Villager.finalizeSpawn`'s `BREEDING` branch — option (a)). The roll happens at birth, not at grow-up: no mod-side grow-up hook was found in the research pass, and nitwit-hood is permanent once set (`ResetProfession` skips `NONE` and `NITWIT` alike), so there is nothing to gain by waiting. | Must | `decisions/DEC-011-nitwit-keepers.md` |
| `KEEPER-REQ-002` | The system shall never apply the breeding roll to a structure-spawned villager: worldgen's own nitwit templates and pool weights (research §G.3) are untouched. | Must | Not-you |
| `KEEPER-REQ-003` | While a nitwit is a baby, the system shall not run any seeking, walking or claiming behaviour for it. | Must | Ruling: "a grown up nitwit" |
| `KEEPER-REQ-004` | While an adult nitwit is `idle` and its seek timer (`keeper_seek_cooldown_ticks`, proposed default 24000 — one vanilla day, matching `CUSTOMER`'s daily restock cadence, to confirm) elapses, the system shall run a seek: no random chance gates it, unlike `CUSTOMER-REQ-002` — the ruling states the walk unconditionally, gated only by whether a seat exists. | Must | Ruling |
| `KEEPER-REQ-005` | The seek shall search for eligible seats using the same origin and radius as `domains/shop.md`'s search (`decisions/DEC-010-village-wide-shop-search.md`: the village's remembered meeting point when present, else the nitwit's own position; 128 blocks, config `shop_search_radius`, shared with `CUSTOMER`). | Must | `DEC-010` |
| `KEEPER-REQ-006` | The system shall consider a seat eligible only per the Seat eligibility table in §3: adjacent to a ticker with no keeper present, unoccupied, and unclaimed. | Must | Seat eligibility |
| `KEEPER-REQ-007` | When an eligible seat is found, the system shall claim it before walking, so a concurrent seek by another nitwit excludes it; nearest eligible seat wins ties, mirroring `SHOP-DEC-001`. The exact claim mechanism — reusing vanilla's own `PoiManager` take/free reservation ticket by POI-registering eligible seats, versus a lightweight non-persistent per-server map from seat `GlobalPos` to claiming villager UUID — is confirmed at the first ticket. | Must | Unwanted: two nitwits, one seat |
| `KEEPER-REQ-008` | When a seat is claimed, the system shall walk the nitwit to it reusing `domains/customer.md`'s trip behaviour: stopping within 2 blocks, giving up after 2400 ticks (`CUSTOMER-REQ-004`). | Must | Ruling: "walk to a free seat" |
| `KEEPER-REQ-009` | When the nitwit reaches the seat block, the system shall rely on Create's own `SeatBlock.onEntityMovement` to auto-seat it; no mod call inserts it into the seat (research §G.2, confirmed for the same walk-target pattern `CUSTOMER` already exercises). | Must | Research §G.2 |
| `KEEPER-REQ-010` | While a nitwit is seated, the system shall take no further action to keep it seated: `SeatEntity.positionRider` pins its position every tick regardless of activity or schedule state, so it stays seated day and night with no mod logic of its own (research §G.1). | Must | Ruling: "when seated" |
| `KEEPER-REQ-011` | If a player right-clicks an occupied seat (`SeatBlock.useItemOn`, ejecting the occupant) or the nitwit is otherwise unseated, then the system shall clear its claim, and start the `keeper_seek_cooldown_ticks` cooldown before it seeks again. | Must | Unwanted |
| `KEEPER-REQ-012` | If the seat block or its underlying block stops being a `SeatBlock`, or the seat's `SeatEntity` self-discards for any other reason (research §G.1), then the system shall treat the nitwit as unseated exactly as in `KEEPER-REQ-011`. | Must | Unwanted |
| `KEEPER-REQ-013` | Where a seek finds no eligible seat, the system shall return the nitwit to `idle` with no error, waiting for the next timer — "they just behave like normal nitwits" (Kevin, 2026-09-20). | Must | Ruling |
| `KEEPER-REQ-014` | The system shall never run seeking behaviour for a villager whose profession is not `NITWIT` — an unemployed (`NONE`) villager stays outside this domain entirely, distinct from `01-actors.md`'s use of "unemployed" for that same profession value. | Must | Not-you |
| `KEEPER-REQ-015` | The system shall give a customer villager's shop search (`domains/customer.md`, `domains/shop.md`) no preference for a nitwit-seated shop over any other keeper-satisfying seat: `isKeeperPresent()` itself makes no such distinction, and this mod adds none at 1.0 (§7). | Must | `DEC-011`, "customers' own preference" |

## 6. Failure modes

| ID | Failure | Response |
|---|---|---|
| `KEEPER-FAIL-001` | Seat broken while a nitwit is seated | `SeatEntity` self-discards; `KEEPER-REQ-012` applies; cooldown starts. |
| `KEEPER-FAIL-002` | Ticker removed or unlinked while its seat still stands and a nitwit is seated | The seat itself is untouched, so the nitwit stays seated; the shop simply stops being a shop from `domains/shop.md`'s own perspective (`SHOP-REQ-006`). Accepted, not a bug: detecting this from the seat side would need extra polling this domain does not add. |
| `KEEPER-FAIL-003` | A raid or other panic trigger fires while a nitwit is seated | Its brain keeps running and may want to flee, but `SeatEntity.positionRider` pins its position every tick regardless (research §G.1); it visibly stays seated. No special handling. |
| `KEEPER-FAIL-004` | A baby nitwit exists | Excluded entirely by `KEEPER-REQ-003`; becomes eligible the moment vanilla ages it up, with no mod hook needed since its profession is already permanently `NITWIT`. |
| `KEEPER-FAIL-005` | An unemployed (`NONE`, non-nitwit) villager exists | Never seeks (`KEEPER-REQ-014`); behaves exactly as vanilla today. |
| `KEEPER-FAIL-006` | Two nitwits' seek timers elapse close enough to target the same seat | The claim (`KEEPER-REQ-007`) resolves it: whichever claims first wins; the other's seek continues to the next-nearest eligible seat, or finds none and returns to `idle`. |
| `KEEPER-FAIL-007` | No eligible seat found anywhere in range | `KEEPER-REQ-013`; no error, no shorter or longer cooldown than the standard timer. |

## 7. Open questions

| Question | Blocks | Decided by |
|---|---|---|
| Exact brain activity slot for the seek behaviour: `Activity.WORK`, like `CUSTOMER`, or `Activity.IDLE` — a nitwit's `acquirableJobSite` predicate can structurally never match (research §G.3), so it is not confirmed that a nitwit ever enters `Activity.WORK` at all. This domain assumes `IDLE` is the correct slot pending verification; see `04-architecture.md` `ARCH-DEC-006`. | `KEEPER-REQ-004` | first ticket |
| Exact claim mechanism: vanilla `PoiManager` reservation reuse versus a claim structure of this mod's own | `KEEPER-REQ-007` | first ticket |
| `keeper_seek_cooldown_ticks`' default value: this sheet proposes 24000 (one vanilla day) | `KEEPER-REQ-004` | Kevin, to confirm |
| Should `CUSTOMER`'s shop search ever prefer a nitwit-seated shop over one kept by a lit `HEATER` block entity (`isKeeperPresent()`'s other accepted case, research §E) or over any other seated mob? Not a requirement at 1.0 — recorded here as a later idea only (`KEEPER-REQ-015`). | Nothing at 1.0 | unassigned |
| Should a seated nitwit count differently for a village's iron golem spawning or raid-omen logic? | Nothing — ruled no by default | Kevin, 2026-09-20 (`KEEPER-DEC-004`) |
| Item- or workstation-based nitwit conversion (research §G.3 options (b)/(c)), beyond the 10% breeding roll | Nothing at 1.0 | deferred, `decisions/DEC-011-nitwit-keepers.md` |

## 8. Decisions

- `KEEPER-DEC-001` — **The breeding roll happens at birth, not at grow-up** (`KEEPER-REQ-001`):
  the only mixin point research confirmed is `Villager.finalizeSpawn`'s `BREEDING` branch, which
  fires when the baby is created, and nitwit-hood does not decay or reset once set. **Cost if
  wrong:** if Kevin specifically wants the roll deferred to grow-up, the mixin target moves to
  whatever hook fires there — unconfirmed to exist — a bounded rewrite of one mixin.
- `KEEPER-DEC-002` — **The periodic seek is deterministic, not chance-gated**, unlike `CUSTOMER`'s
  per-restock roll (`CUSTOMER-DEC-001`): the ruling describes an unconditional walk once grown,
  gated only by seat availability. **Cost if wrong:** adding a chance roll to `KEEPER-REQ-004` is a
  one-line change, the same shape as `CUSTOMER-REQ-002`.
- `KEEPER-DEC-003` — **One cooldown config key reused for every wait**: the interval between seeks
  while idle, the wait after a failed seek, and the wait after ejection or a broken seat all read
  the same `keeper_seek_cooldown_ticks`, rather than three separate constants. **Cost if wrong:** a
  second config key, not a redesign — the memory module shape (`04-architecture.md` `ARCH-DEC-005`)
  does not change either way.
- `KEEPER-DEC-004` — **No special golem or raid treatment for a seated nitwit** (Kevin, 2026-09-20,
  ruling the open question "no by default"): a seated nitwit is a villager like any other for every
  vanilla system this mod does not name. **Cost if wrong:** untangling this from `KEEPER-REQ-010`'s
  "no mod logic while seated" stance later, should a seated nitwit ever need to be excluded from a
  village's population count.
