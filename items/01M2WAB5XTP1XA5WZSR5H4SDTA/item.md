---
schema_version: 1
id: 01M2WAB5XTP1XA5WZSR5H4SDTA
key: VC-10
type: docs
title: Modrinth body aligned with the live listing conventions
created_by: kevin
created_at: 2026-09-19T07:50:55Z
---

## Scope

Align `docs/modrinth/body.md` with the listing conventions Kevin set by hand on the brass compass's live Modrinth project (Kevin, 2026-09-19: "the live listing wins"): the title prefix `Create:` instead of `Create Fly:`; primary categories Equipment, Technology, Utility and secondary Adventure, Game Mechanics, Management, Optimization (adapted per mod where a category plainly does not fit, said so in the change); the Modrinth source link on the GitHub mirror (Modrinth users expect GitHub), the issues link on GitHub, Forgejo named as the canonical origin in the body's Support paragraph. The listing tool `modrinth-publish.py` (cubealgos heimathafen, `standards/marketing/modrinth/`) reads this file, so its `check` must show no diff on title, categories and links against the live project afterwards.

## Approach

Edit only `docs/modrinth/body.md` (and `README.md` if it repeats the title). Verify with `python3 <heimathafen>/standards/marketing/modrinth/modrinth-publish.py check --repo .` where a live project exists.

## Acceptance criteria

- [ ] `body.md`'s Project settings table carries the `Create:` title, the category set and the GitHub source and issues links.
- [ ] Where a live project exists, `modrinth-publish.py check` reports no diff on title, categories, additional categories, source and issues; only the body text may differ.
- [ ] Merged through a Forgejo pull request into `development`.

## Constraints and prior findings

The live compass project (id `WNVXPPbU`, status processing) is the template. The tool's `update` pushes body text; run it only after this lands.
