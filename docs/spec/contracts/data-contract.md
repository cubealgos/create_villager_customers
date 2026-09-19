---
title: "create_villager_customers spec — data contract: the memory modules and the cooldown"
type: "spec"
category: "create_villager_customers"
---

# Data contract (`DATA`)

## What this mod persists

Nothing of its own beyond two memory modules on the villager's brain, exactly as `ARCH-DEC-005`
scopes it:

```
villager_customers:shopping_trip_target   MemoryModuleType<GlobalPos>   the shop being walked to
villager_customers:shopping_cooldown      MemoryModuleType<Long>        game time of the next allowed roll
```

Both are ordinary vanilla-shaped memory modules, saved and loaded exactly as vanilla's own
`HOME`/`JOB_SITE`/`CANT_REACH_WALK_TARGET_SINCE` are, through the brain's own memory persistence.
Neither carries a schema of this mod's own to version.

## Rules

| ID | Rule |
|---|---|
| `DATA-REQ-001` | `shopping_trip_target` shall be present only while a trip is `walking` (`domains/customer.md` §3); it shall be cleared on arrival, cancellation, or timeout. |
| `DATA-REQ-002` | `shopping_cooldown` shall be present only after a cancelled or timed-out trip; a roll shall not occur while the current game time is before it. |
| `DATA-REQ-003` | A malformed or unreadable memory value (e.g. after a downgrade that does not recognise the module) shall be treated as absent: the villager is simply idle, with no error and no crash. |
| `DATA-REQ-004` | Neither module shall be read or written by anything outside the mixin behaviour in `04-architecture.md`; no other block, item or command touches them. |

## Versioning: not applicable, and why

Unlike `create_metered_motor`'s `stats` component, these two values carry no economic identity
that must survive forever unchanged — they are transient coordination state, disposable the moment
a trip ends or a save is loaded into a build that no longer defines them. A missing or unreadable
memory degrades to "no trip in progress," never to an item, a stat, or an emerald count that could
be silently wrong. There is therefore no migration path to design and no `version` field to carry.

## Out of scope (sheet §8)

No import of another mod's villager-trading state. No record of past trades kept anywhere: the
only evidence a trade happened is the shop's own stock and payment box, exactly as `00-context.md`
scopes it.
