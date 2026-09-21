---
schema_version: 1
id: 01M30QZD9TGQD9SB44AEQ5T1G3
key: VC-22
type: docs
title: "Icon: emerald at 70% fit box (icon rulings 2026-09-21)"
created_by: kevin
created_at: 2026-09-21T01:06:07Z
---

## Scope

`tools/icon.py`'s fit box for the vanilla emerald, per Kevin's icon rulings (2026-09-21): pixel-art
items sit at 70% of the previous fit box. `docs/modrinth/icon.png` is regenerated from the new box;
this mod stays on the Create blueprint-grid badge (it is a Create Fly add-on), so the grid itself
is untouched -- only the emerald's size changes. Not the badge colours, the outline/shadow
treatment, or anything else in `docs/modrinth/`.

## Approach

Change `BOX = 320` in `tools/icon.py` to the ruling's 70%: either shrink the constant directly
(`round(320 * 0.7)` = 224) or, to mirror the fleet tool's new interface
(`heimathafen/standards/marketing/modrinth/navy-badge.py`'s `--fit` multiplier, default 1.0,
byte-identical to the previous behaviour), keep `BOX = 320` and apply a `FIT = 0.7` multiplier at
the point the fit box is used, so the ruling reads as a multiplier on the existing box rather than
a new magic number. Regenerate `docs/modrinth/icon.png` with `just icon` (or `python3
tools/icon.py`) and confirm the result against the 70%-fit-box candidate rendered for this ruling
(`emerald @ 70% (224px box)` in the icon-variants contact sheet Kevin reviewed).

## Acceptance criteria

- [x] `tools/icon.py`'s emerald fit box is 70% of its previous value (320 -> 224px), grid theme
      unchanged.
- [x] `docs/modrinth/icon.png` is regenerated from the new box and visually matches the reviewed
      70% candidate.
- [x] `just check` (or at minimum the tools tests and `just map`) passes.
- [x] Committed on `documentation/vc-22-icon-size-70` and pushed; no PR, no `kontor finish`.

## Constraints and prior findings

Kevin's icon rulings, 2026-09-21: pixel-art items sit at 70% of the previous fit box; mods that
are not Create add-ons get a plain navy badge without the blueprint grid. This mod *is* a Create
Fly add-on (its icon shares the blueprint-grid badge with `create_brass_compass` and
`create_metered_motor`), so only the size ruling applies here -- the grid stays. Candidate renders
reviewed at `/private/tmp/claude-501/-Users-kevin-Documents-git-personal-create-civilization/
bb7bc244-01b6-4890-9ca3-da5d384dc21e/scratchpad/icon-variants.png`, section 2 ("Size fix").
Current fit box is `BOX = 320` in `tools/icon.py` (`docs/modrinth-collection-icon.md`'s fleet-tool
default of 272 is a separate, unrelated constant in the heimathafen prototype script).
