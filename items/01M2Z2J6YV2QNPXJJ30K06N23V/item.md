---
schema_version: 1
id: 01M2Z2J6YV2QNPXJJ30K06N23V
key: VC-16
type: chore
title: ComponentPredicateGameTest is not registered in the gametest entrypoint
created_by: kevin
created_at: 2026-09-20T09:32:40Z
---

## Scope

`ComponentPredicateGameTest` (VC-15) is on `development` but not listed in `src/gametest/resources/fabric.mod.json`'s gametest entrypoint, so VC-15's three tests never ran on `development`. Register it and prove the count; the merge tooling now registers every `@GameTest` class after each merge.

## Approach

Add the class to the entrypoint list, `just check`.

## Acceptance criteria

- [x] Every `@GameTest` class under `src/gametest/java` is listed; `just check` runs all of them.
- [x] Merged through a Forgejo pull request into `development`.

## Constraints and prior findings

Same gap as `create_firearms` FA-20: parallel branches each adding their own entry, the merges keeping one side.
