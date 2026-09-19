---
schema_version: 1
id: 01M2W6ECAGTSYHC7M4H7QP5G42
key: M1
title: Shops as points of interest and the transaction
status: todo
created_at: 2026-09-19T06:42:45Z
---

## Goal

A table cloth becomes a discoverable, live-checked shop and its match rule and execution loop exist and are individually provable, ahead of any villager ever walking to one.

## Scope

In: the `PoiType` registration over Create Fly's table-cloth block states, the `Shop` view class and the village POI search with nearest-first tie-break (`VC-2`); the match rule in the pure `villager_customers.model` package, the direct draw from the network stock, payment and xp-nugget insertion, and the per-unit execution loop with its refusals (`VC-3`). Out: how or whether a villager ever decides to walk to a shop at all — that is `M2`.

## Exit criteria

- Game tests prove a table cloth is discoverable through the POI index, and that shophood flips false the moment price, link or keeper breaks.
- Unit tests prove the match rule (including the two-cost-item skip) and the xp-nugget conversion arithmetic.
- Game tests prove a unit completes end to end against a real shop network, and that both refusal paths ("stock too low", "cash register full") stop the loop cleanly with earlier units standing.
- The Create-internal removal call (or its accessor-mixin fallback) is confirmed and recorded, closing this mod's largest open risk (`ARCH-DEC-004`).

## Tickets

- VC-2 — Shops as points of interest: PoiType, Shop view, candidate search
- VC-3 — The transaction: match rule, direct draw, payment, xp nuggets

## Depends on

M0: VC-1 bootstraps the Gradle project, toolchain and mixin scaffold that VC-2 and VC-3 build on; VC-1 blocks both.
