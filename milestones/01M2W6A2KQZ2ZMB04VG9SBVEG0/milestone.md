---
schema_version: 1
id: 01M2W6A2KQZ2ZMB04VG9SBVEG0
key: M0
title: Foundation
status: todo
created_at: 2026-09-19T06:40:24Z
---

## Goal

Everything the repository needs before feature work begins: a working Gradle/Loom build against Create Fly, licensing, routing, tooling and CI, and a proof the mod loads at all.

## Scope

In: the Gradle project, main and client entrypoints, `docs/spec/` synced from the vault, `LICENSE`/`NOTICE`, `CLAUDE.md` routing to the three domains, `justfile` and `tools/`, CI wiring, the `villager_customers.mixins.json` scaffold (empty until `VC-3`), a smoke game test. Out: any POI, transaction or brain-mixin code — those start at M1.

## Exit criteria

- `just check` is green, including the smoke game test.
- `just doctor` is clean: toolchain floors, spec copy, merge templates.
- `fabric.mod.json`'s contact block names the Forgejo repo, the GitHub issues tracker and the Modrinth slug.

## Tickets

- VC-1 — Bootstrap: Gradle with Loom and Create Fly, entrypoints, licence, notice, routing, tools, spec copy, smoke game test

## Depends on

Nothing.
