---
schema_version: 1
id: 01M3086528588DZVSA8EYW4H1W
key: VC-19
type: feat
title: Ten percent of bred villagers grow up as nitwits (config nitwit_breeding_chance)
created_by: kevin
created_at: 2026-09-20T20:30:11Z
---

## Scope

<fill this in before committing>

## Approach

<fill this in before committing>

## Acceptance criteria

- [x] <fill this in before committing>

## Constraints and prior findings

<fill this in before committing>

## Scope

Kevin, 2026-09-20: "Create offers a unique opportunity to make nitwits useful: they should be able
to work as shopkeepers when seated," supplied by breeding: "breeding chance but only 10%." Ruled as
`decisions/DEC-011-nitwit-keepers.md` and `domains/keeper.md` `KEEPER-REQ-001`, `002`: on a
villager baby's breeding spawn, roll a configured chance (default 10%, `nitwit_breeding_chance`)
and, on success, set its profession to `NITWIT` instead of vanilla's `NONE`, at birth. Not in
scope: the seek/walk/seat behaviour itself (VC-20, independent), any item- or workstation-based
conversion (`domains/keeper.md` §7, deferred), and anything about `EntitySpawnReason.STRUCTURE`
(worldgen) villagers, which stay untouched.

## Approach

Mixin `Villager.finalizeSpawn(ServerLevelAccessor, DifficultyInstance, EntitySpawnReason,
SpawnGroupData)` at its `BREEDING` branch (`04-architecture.md` `ARCH-DEC-007`; research
`create-fly-shops-and-villager-brain-26-2.md` §G.3, option (a)): after vanilla sets profession
`NONE`, roll `nitwit_breeding_chance` and, on success, call the public
`setVillagerData(getVillagerData().withProfession(registryAccess, VillagerProfession.NITWIT))`
instead. Do not touch the `STRUCTURE` branch or `getBreedOffspring` (`VillagerType` only, untouched
by this ticket). Add the config key with a clamp (0.0–1.0, `SURFACE-REQ-002`-style). Confirm at
implementation time whether `refreshBrain` needs an explicit call after the profession swap — the
research notes it may be needed for non-trivial work packages, though a nitwit's own package is
trivial; note the finding either way. Game tests: breed two villagers a large number of times with
`nitwit_breeding_chance` set to 1.0 and confirm every baby ends up `NITWIT`; set it to 0.0 and
confirm none do; confirm a `STRUCTURE`-spawned villager is never affected by the roll. Sync
`docs/spec` from the vault.

## Acceptance criteria

- [x] A bred baby's profession rolls `nitwit_breeding_chance` (default 0.10) at spawn and becomes
      `NITWIT` on success, `NONE` on failure, exactly as vanilla did before this ticket.
- [x] `STRUCTURE`-spawned (worldgen) villagers are provably unaffected by the roll.
- [x] Config key `nitwit_breeding_chance` exists, defaults to 0.10, is clamped to `[0.0, 1.0]`.
- [x] Game tests: chance=1.0 → every bred baby is a nitwit; chance=0.0 → none are; a worldgen
      nitwit's origin is untouched either way. Existing tests stay green; `just check` green.
- [x] Spec synced from the vault; merged through a Forgejo pull request into `development`.

## Constraints and prior findings

- `AbstractVillager.canBeLeashed()` is hard-coded `false`; not relevant to this ticket's spawn-time
  hook, but rules out any lead-based supply mechanism for a future alternative.
- Nitwit-hood is permanent once set: `ResetProfession` skips both `NONE` and `NITWIT`, so no
  later vanilla system undoes this roll's result.
- `Villager.setVillagerData` is `public` and only clears cached offers if the profession changed —
  cheap to call here.
- No mod-side grow-up hook was found in the research pass, which is why the roll happens at birth
  rather than "at grow-up" despite the ruling's own phrasing (`domains/keeper.md` `KEEPER-DEC-001`);
  flagged to Kevin as a proposal, not a certainty.
