---
schema_version: 1
id: 01M2W6F11RB885N2FMSSGGPG11
key: VC-5
type: feat
title: Development-only /villager_customers debug command
created_by: kevin
created_at: 2026-09-19T06:43:06Z
---

## Scope

The development-only debug tool `operations/testing.md`'s "Development tool" row names: `/villager_customers debug trip <villager>` forces a chance-roll success and prints the search result (candidate found, distance, matched offer) for a targeted villager, for screenshots and behaviour checks — as the siblings' debug commands, registered only when Fabric reports a development environment. Covers three actions against a targeted villager: forcing its restock chance roll to succeed, printing what `ShopSearch` (`VC-2`) finds for it (matched shop, distance, matched offer, or "none"), and triggering a full trip end to end. Out of scope: any behaviour this command exercises rather than provides — the roll, search, walk and transaction all already exist from `VC-2` through `VC-4`.

## Approach

First thing to verify: `create_metered_motor`'s `MM-8` (`/metered_motor debug`) and `create_brass_compass`'s own debug command for the exact Fabric dev-environment registration gate (`FabricLoader.getInstance().isDevelopmentEnvironment()`) and command-tree shape, copied rather than reinvented. Class `villager_customers.debug.DebugCommand`, registered via `CommandRegistrationCallback.EVENT` only inside that dev-environment guard. Subcommands: `roll <villager>` (forces the next restock chance roll to succeed for the targeted entity), `search <villager>` (runs `ShopSearch` for the villager's eligible offers and prints the result without moving it), `trip <villager>` (forces the roll and lets the mixin's normal behaviour take over from there, for a full end-to-end screenshot run).

## Acceptance criteria

- [ ] `/villager_customers debug roll <villager>` forces the next restock chance roll to succeed for the targeted villager.
- [ ] `/villager_customers debug search <villager>` prints the search result for the targeted villager: candidate shop found (position, distance) and matched offer, or "no match" with the reason category (no offer with uses left, or no shop matches any eligible offer).
- [ ] `/villager_customers debug trip <villager>` triggers a full trip end to end (roll, search, walk, transaction) for a targeted villager, usable for screenshots and manual behaviour checks.
- [ ] The command is registered only when Fabric reports a development environment; a game test (or equivalent registration-time check) proves it is absent when not.
- [ ] Game test proving the command executes correctly in a development environment against a mock villager and a real shop.

## Constraints and prior findings

`operations/testing.md`, "Development tool" row: "registered only when Fabric reports a development environment, as the siblings' debug commands are." Modelled directly on `create_metered_motor`'s `MM-8` (`/metered_motor debug`, blocked by the block entity it targets there) and `create_brass_compass`'s own debug command shape. Blocked by `VC-4` — the shopping-trip behaviour, its memory modules and its restock hook must exist before a debug command can force or inspect any of them.
