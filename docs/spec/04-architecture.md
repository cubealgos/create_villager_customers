---
title: "create_villager_customers spec — architecture: a mixin into the villager brain, a POI, a direct transaction"
type: "spec"
category: "create_villager_customers"
---

# 04 — Architecture

Sheet §3. Everything here follows `vault/technical/minecraft/create-fly-shops-and-villager-brain-26-2.md`;
where the research left a gap, the gap is named and marked "to verify at the first ticket" rather
than guessed at.

## Shape

```
 world / POI                       server                                       Create Fly
 ┌──────────────────┐   discover   ┌──────────────────────────────────┐  reads   ┌────────────────┐
 │ PoiType over      │─────────────►│ mixin: Villager.registerBrainGoals│─────────►│ TableClothBE    │
 │ table cloth block │              │  (Brain, @Inject RETURN)          │          │  priceTag,      │
 │ states            │              │   Brain.addActivity(WORK, ...)    │          │  requestData    │
 └──────────────────┘              │                                    │◄─────────│ StockTickerBE   │
                                    │ ShoppingTripBehavior (Behavior<V>) │  draw +  │  receivedPayments│
                                    │  roll → search → SetWalkTarget-   │  insert  │  getAccurateSummary│
                                    │  FromBlockMemory → arrive → run   │─────────►│  Container.insert│
                                    │  TRANSACTION loop                 │          └────────────────┘
                                    │                                    │
                                    │ memory: trip target (GlobalPos),  │          no client code,
                                    │         cooldown (Long)           │          no new packet,
                                    └──────────────────────────────────┘          no new screen
```

**Nothing here is the mod's own container or screen.** The only new server-side objects are a
`PoiType`, a `Behavior<Villager>`, two memory module types, and the mixin that wires the behaviour
into `Activity.WORK`. Every item movement happens inside Create Fly's own blocks.

## `ARCH-DEC-001` — a Fabric mod on Create Fly, one jar, Java 25

Same toolchain and layout as `create_brass_compass` and `create_metered_motor`
(`decisions/DEC-004-toolchain.md`): Loom 1.17, Gradle 9.5.1, Kotlin DSL with a version catalog, one
Gradle project. Unlike the siblings, this mod carries a mixin into `minecraft` (`Villager`, for
the brain hook); a second, accessor-only mixin into `create` exists only if the direct draw from
the network stock (`ARCH-DEC-004`) finds no public call, decided at the first transaction ticket. **Cost if wrong:** two upstreams to track instead of one; a Minecraft version bump can
break the villager-brain mixin independently of a Create Fly bump breaking the shop-reading code.

## `ARCH-DEC-002` — one behaviour added to `WORK` through a mixin, since no Fabric event exists

`Villager.registerBrainGoals(Brain<Villager>)` is private and called from both
`Villager.makeBrain(Brain.Packed)` and `Villager.refreshBrain(ServerLevel)`; it is the narrowest
target both paths funnel through. The mixin injects at `RETURN` and calls
`brain.addActivity(Activity.WORK, ImmutableList.of(Pair.of(priority, shoppingTripBehavior)),
requiredMemories, memoriesToClearOnStop)` — the same public method vanilla itself uses to add
priority-bucketed behaviours to an activity. No Fabric API event fits: `fabric-entity-events-v1`,
`fabric-lifecycle-events-v1` and `fabric-events-interaction-v0` were checked exhaustively and carry
no villager, brain or schedule callback (research §C).

**Alternatives considered:** mixing into `Brain`'s static `BRAIN_PROVIDER` lambda, or into
`makeBrain`/`refreshBrain` directly (rejected: both are wider surfaces than `registerBrainGoals`
for the same result). **Cost if wrong:** a private-method mixin target can move between Minecraft
versions; bounded the same way every Create Fly add-on already accepts its Minecraft pin.

**To verify at the first ticket:** the exact priority slot and the `MemoryModuleType` entry
condition and clear-on-stop sets to declare — vanilla's own `WORK` behaviours were not traced
field-by-field (research §B, Open).

## `ARCH-DEC-003` — shops are POIs

A `PoiType` registered over Create Fly's table-cloth block states, so villagers discover shops
through the same village POI index vanilla uses for job sites, rather than a block scan this mod
would have to invent and keep in sync itself (`decisions/DEC-008-poi.md`). Create Fly registers no
`PoiType` of its own and reserves no villager-related behaviour (research §D), so there is nothing
to conflict with.

**To verify at the first ticket:** the exact `PoiType` registration API and the full set of table
cloth block states to register over.

## `ARCH-DEC-004` — the transaction is the mod's own, not Create's player checkout

`TableClothBlockEntity.useShop(Player)` and `StockTickerInteractionHandler.interactWithShop`
both require and mutate a real `Player.getInventory()`; no overload takes an `IdentifiedInventory`
or any non-player payer (research §A, "Non-player checkout"). A villager cannot drive this code as
written. Instead: stock is read from `StockTickerBlockEntity.getAccurateSummary()`/
`getRecentSummary()`; price and xp nuggets are inserted into `receivedPayments` via the same
`Container.insert(List<ItemStack>)` the real checkout uses (confirmed public); the offer's uses and
the villager's trade xp advance through `MerchantOffer.increaseUses()` and
`AbstractVillager.notifyTrade(MerchantOffer)`.

**To verify at the first ticket, and the largest open risk in this mod:** the exact call that
removes a unit's goods from the network stock. The research pass confirmed the summary-reading and
payment-inserting halves of the transaction but did not trace a parallel removal call; if none is
public, the first ticket may need a further mixin into Create Fly's own network or summary classes.

## `ARCH-DEC-005` — no persistent state of the mod's own beyond a memory module and a cooldown

The trip target (a `MemoryModuleType<GlobalPos>`, the same shape as vanilla's `HOME`/`JOB_SITE`)
and a cooldown value (`MemoryModuleType<Long>`, a game-time tick) live in the villager's brain and
are saved the way vanilla's own memories are (`contracts/data-contract.md`). Nothing else is
written to the world save, and no config file exists beyond the data constants
`decisions/DEC-009-chance-per-restock.md` names. **Cost if wrong:** if custom memory modules of
this shape do not round-trip a save/load the way vanilla's do, a villager could resume mid-trip
after a restart in a way this design did not check.

## `ARCH-DEC-006` — the nitwit keeper-seek behaviour, in `Activity.IDLE`, not `Activity.WORK`

A second `Behavior<Villager>`, added through the same mixin as `ARCH-DEC-002` but gated to
profession `NITWIT` and adult age (`domains/keeper.md` `KEEPER-REQ-004`). It is proposed for
`Activity.IDLE` rather than `WORK`: `VillagerProfession.NITWIT` registers `PoiType.NONE` for both
`heldJobSite` and `acquirableJobSite` (research §G.3), so `AcquirePoi`'s predicate can structurally
never match and a nitwit can never hold a job site — it is not confirmed by the research pass
whether vanilla's own schedule ever grants `Activity.WORK` to a villager with no held job site at
all. `IDLE` is the safer default until this is checked.

Unlike `CUSTOMER-REQ-002`'s chance-gated restock roll, the seek itself is unconditional once its
own timer elapses (`domains/keeper.md` `KEEPER-DEC-002`): only seat availability gates it. Seating
needs no call of this mod's own — `SetWalkTargetFromBlockMemory` onto the claimed seat's position
is enough, since `SeatBlock.onEntityMovement` auto-seats any `LivingEntity` that steps onto it, with
no profession or species check (research §G.1, §G.2) — the same walk-target pattern `CUSTOMER`
already exercises against table cloths, pointed at a seat block instead.

**To verify at the first ticket:** whether a nitwit ever reaches `Activity.WORK` at all; and
whether vanilla's own `PoiManager` take/free reservation ticket — already used to stop two
villagers claiming one job site or bed — can be reused for seat claims by POI-registering eligible
seats, rather than a claim structure of this mod's own (`domains/keeper.md` `KEEPER-REQ-007`).
**Cost if wrong:** moving the behaviour from `IDLE` to `WORK` (or vice versa) is a one-line change
to the `Activity` argument in `Brain.addActivity`, not a redesign.

## `ARCH-DEC-007` — the breeding roll, a mixin on `Villager.finalizeSpawn`'s `BREEDING` branch

`Villager.finalizeSpawn(ServerLevelAccessor, DifficultyInstance, EntitySpawnReason,
SpawnGroupData)` sets a bred baby's profession to `NONE` unconditionally (research §G.3). The
mixin injects into that branch, rolls `nitwit_breeding_chance`, and on success calls the public
`setVillagerData(getVillagerData().withProfession(registryAccess, VillagerProfession.NITWIT))`
instead — the only mixin point research confirmed exists for this; a grow-up-time roll was
considered and rejected for lacking any confirmed hook (`domains/keeper.md` `KEEPER-DEC-001`).
`STRUCTURE`-spawned villagers (worldgen's own nitwit templates) are untouched (`KEEPER-REQ-002`).

## New config keys (`contracts/public-surface.md`)

| Key | Default | Governs |
|---|---|---|
| `nitwit_breeding_chance` | 0.10 | `ARCH-DEC-007`; `domains/keeper.md` `KEEPER-REQ-001` |
| `keeper_seek_cooldown_ticks` | 24000 (one vanilla day, proposed, to confirm) | `ARCH-DEC-006`; every wait in `domains/keeper.md` `KEEPER-DEC-003` |

## Runtime topology (sheet §3.1)

The mod runs inside the Minecraft client and server processes; no process, daemon or file of its
own. All logic is server-side: the client sees only the villager's existing entity sync and Create
Fly's own container syncs for the stock ticker and payment box. No client-side code exists.

## Failure modes with no single owner (sheet §3.6)

| ID | Failure | Response |
|---|---|---|
| `ARCH-FAIL-001` | Create Fly missing or another version | Fabric Loader refuses to start with its dependency message; the mod adds nothing. |
| `ARCH-FAIL-002` | No public direct-draw removal call exists at the first ticket | Blocks `TRANSACTION-REQ-004`; tracked as this mod's largest risk (`ARCH-DEC-004`). |
| `ARCH-FAIL-003` | The villager's chunk unloads mid-trip | Vanilla does not tick unloaded entities; the trip resumes, or times out, once loaded again, same as any vanilla walk-target behaviour. |
| `ARCH-FAIL-004` | Another mod also mixins `Villager.registerBrainGoals` | Coexists: `Brain.addActivity` is additive per activity. A genuine collision (same priority slot) is a mixin-ordering problem shared by that pair of mods, not unique to this one. |
