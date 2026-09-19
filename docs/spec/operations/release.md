---
title: "create_villager_customers spec — release engineering and distribution"
type: "spec"
category: "create_villager_customers"
---

# Release engineering, distribution and support (`REL`, sheet §7)

| Item | Position |
|---|---|
| Version scheme | `<mod>+<mc>`, SemVer on the mod part over `contracts/public-surface.md`: `1.0.0+26.2` |
| Branches | gitkontor's: `development`, `production`; releases are tags on `production` |
| Channels | Modrinth only; CurseForge deferred, as `create_brass_compass` and `create_metered_motor` |
| CI | `just check` on every merge: lint, unit tests, game tests, by the Woodpecker file, live from the first push since the repo is public on Forgejo from the bootstrap (`https://git.cubealgos.de/cubealgos/create_villager_customers`, GitHub mirror `https://github.com/cubealgos/create_villager_customers`, which carries the public issue tracker) |
| Always a playable build | `just client` boots with Create Fly at every merge, and a villager with a matching offer visibly shops given time to reach `WORK` and a restock |
| Support | Issue tracker only; no SLA; a `SUPPORT.md` says so |
| Ports | A new Minecraft version is a new `+<mc>` build from a port branch; the mixin targets are re-verified against both the new Minecraft jar and the tested Create Fly build on each port |

`REL-REQ-001`: every release jar is built by `just release` from a clean checkout at a tag.
`REL-REQ-002`: the release notes list the Minecraft, Fabric Loader and Create Fly versions tested.
`REL-REQ-003`: the release notes state the default values in force for the chance per restock, the
xp-nugget conversion, the walk timeout and the cooldown (`contracts/public-surface.md`).
