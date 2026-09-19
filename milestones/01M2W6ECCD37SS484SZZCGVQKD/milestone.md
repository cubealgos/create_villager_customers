---
schema_version: 1
id: 01M2W6ECCD37SS484SZZCGVQKD
key: M2
title: "The customer: decision, trip, execution"
status: todo
created_at: 2026-09-19T06:42:45Z
---

## Goal

A villager in `Activity.WORK` decides, on its own, whether to go shopping, walks to a matching shop, and hands off to the transaction built in `M1` — the mod's whole loop, closed end to end.

## Scope

In: the mixin on `Villager.registerBrainGoals` adding the shopping-trip behaviour to `WORK`, the two memory modules (trip target, cooldown), the restock-triggered chance roll, the walk via `SetWalkTargetFromBlockMemory`, every cancellation path (panic, leaving `WORK`, lost shophood, timeout), and the arrival hand-off to the transaction loop (`VC-4`). Out: anything that only reads or exercises the finished behaviour rather than building it — the debug command and the requirement sweep are `M3`.

## Exit criteria

- Game tests prove a forced roll executes a trade within the walk timeout, that no trip starts outside `WORK`, and that a removed shop cancels a trip cleanly with no partial transaction.
- A game test proves the mixin coexists with another mod's `Villager.registerBrainGoals` mixin.
- The two memory modules round-trip a save/load exactly as vanilla's own memories do.

## Tickets

- VC-4 — The customer behaviour: WORK mixin, shopping trip, restock roll, walk, hand-off

## Depends on

M1: VC-4 is blocked by VC-2 (the shop search it queries) and VC-3 (the transaction loop arrival hands off to) — it cannot be built, let alone proven end to end, without both.
