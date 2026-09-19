---
schema_version: 1
id: 01M2W6ECEBD6X9T1MCRQSKEVTW
key: M3
title: Tests, playable check, development tool
status: todo
created_at: 2026-09-19T06:42:45Z
---

## Goal

Every `CUSTOMER`, `TRANSACTION` and `SHOP` requirement is named by a test, the whole loop is confirmed playable by a human, the listing assets are ready ahead of the cut, and a development tool exists for forcing and inspecting a trip without waiting on chance.

## Scope

In: the development-only `/villager_customers debug` command (`VC-5`), the requirement-to-test table and three green `just check` runs plus Kevin's client checklist (`VC-6`), the Modrinth listing assets — badge icon, body, gallery shot list — produced ahead of the release build (`VC-7`). Out: anything that changes behaviour — this milestone only proves, tools around, and prepares to ship what `M1` and `M2` built.

## Exit criteria

- The debug command forces a roll, prints a search result, and triggers a trip, registered only in a development environment.
- The requirement-to-test table covers every `CUSTOMER-REQ`, `TRANSACTION-REQ` and `SHOP-REQ`, plus `COMP-REQ-001`.
- `just check` green three consecutive runs; Kevin's client checklist done and recorded.
- The Modrinth listing assets exist and `just icon` works.

## Tickets

- VC-5 — Development-only /villager_customers debug command
- VC-6 — Requirement-to-test table, three green just check runs, client checklist
- VC-7 — Modrinth listing assets ahead of the release: badge icon, paste-ready body and settings, gallery shot list

## Depends on

M2: VC-5 and VC-6 are blocked by VC-4 — the shopping-trip behaviour they debug and prove must exist first. VC-7 is blocked by VC-1 only (the bootstrap's `tools/icon.py` and `docs/modrinth/` layout), so it can run in parallel with M1/M2.
