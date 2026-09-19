---
title: "create_villager_customers DEC-003 — MIT, no CLA, public under cubealgos from the first commit"
type: "spec"
category: "create_villager_customers"
---

# `DEC-003` — MIT, no CLA, public under cubealgos from the first commit

**Status:** decided by Kevin, 2026-09-19.

MIT, following Create's own licence and the add-on norm, with no CLA; `NOTICE` credits Create Fly,
Create and Fabric (`operations/compliance.md`). The repository is public under the `cubealgos`
organisation on Forgejo from the first commit, mirrored to GitHub with the issue tracker there —
the same place `create_brass_compass` and `create_metered_motor` ended up, so this project starts
there instead of retracing that path. The commit identity is `scheeren@cubealgos.de`.

This diverges from two heimathafen defaults: `default-license-apache-2-cla` (Apache-2.0 plus a
CLA) and "no remote until justified" (every repo starts local-only). Both divergences are the ones
the compass already made and both siblings recorded; this project inherits them rather than
deciding them fresh. Cost if wrong: MIT and a public remote are hard to walk back once someone has
forked.
