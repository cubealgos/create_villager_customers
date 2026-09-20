---
title: "create_villager_customers spec — public surface: what a datapack, resource pack or add-on may rely on"
type: "spec"
category: "create_villager_customers"
---

# Public surface (`SURFACE`)

| Surface | Stable from | What it is |
|---|---|---|
| Mod id `villager_customers` | 1.0 | Fabric mod id |
| POI type id `villager_customers:table_cloth_shop` (proposal, confirmed at the first ticket) | 1.0 | The registered point-of-interest type over table cloth block states |
| Data constant: chance per restock | 1.0 | `decisions/DEC-009-chance-per-restock.md`; a datapack file, overridable |
| Data constant: xp-nugget conversion | 1.0 | `decisions/DEC-007-xp-nuggets.md`; overridable |
| Data constant: walk timeout, close-enough and too-far distances | 1.0 | `domains/customer.md` `CUSTOMER-REQ-004`; overridable |
| Data constant: per-villager cooldown | 1.0 | `domains/customer.md` `CUSTOMER-REQ-007`; overridable |
| Data constant: `nitwit_breeding_chance` | 1.0 | `decisions/DEC-011-nitwit-keepers.md`; `domains/keeper.md` `KEEPER-REQ-001`; default 0.10, overridable |
| Data constant: `keeper_seek_cooldown_ticks` | 1.0 | `domains/keeper.md` `KEEPER-REQ-004`, `KEEPER-DEC-003`; default 24000 (proposed), overridable |
| Translation keys, if any (none at 1.0: no screen, no tooltip, no item of this mod's own) | 1.0 | N/A — see `00-context.md` "What it will not do" |

Not public: the mixin targets and injection points (`04-architecture.md`), the memory module ids
(`contracts/data-contract.md`), the internal search and match implementation, the exact
Create-internal calls used for the direct draw. Versioned by SemVer over the surface above
(`operations/release.md`).

`SURFACE-REQ-001`: a change to a stable surface is a major version.
`SURFACE-REQ-002`: **where** a datapack overrides a data constant outside its documented range
(e.g. a negative chance, a zero timeout), the system shall clamp it to the nearest valid value and
log once, rather than fail to start.
