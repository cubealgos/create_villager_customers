---
schema_version: 1
id: 01M2W92QG12AAQ2R493W8CRFZ4
key: VC-9
type: docs
title: "Modrinth icon: the emerald on the cubealgos navy badge"
created_by: kevin
created_at: 2026-09-19T07:28:49Z
---

## Scope

Re-render the Modrinth icon on the cubealgos navy badge with a real game asset (Kevin, 2026-09-19: navy background for all mod icons; the icon is the vanilla emerald item sprite alone, chosen over a villager face). `tools/icon.py` reads `assets/minecraft/textures/item/emerald.png` from the Minecraft client jar in the Gradle cache (never vendored into the repo), draws it on the navy badge a step larger than the old sprite, and writes `docs/modrinth/icon.png`; the hand-drawn `docs/modrinth/placeholder-customer.png` is deleted; `docs/modrinth/body.md`'s icon line updated.

## Approach

Palette from the cubealgos heimathafen layer (`standards/marketing/modrinth-collection-icon.md`): navy `(13, 18, 38)`, disc edge `(9, 12, 27)`, grid `(52, 76, 128)`, white rim. The script locates the Minecraft jar with a glob over `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.2/*.jar` (or the client jar under `fabric-loom/26.2/`), falling back to a `--jar` argument, and fails with a clear message when none is found. Sprite fit box 320 px, nearest-neighbour.

## Acceptance criteria

- [x] `just icon` regenerates `docs/modrinth/icon.png` (512 × 512, under 256 KiB) from the emerald sprite on the navy badge; no Mojang texture is committed.
- [x] `docs/modrinth/placeholder-customer.png` is gone and nothing references it.
- [x] `docs/modrinth/body.md` describes the icon as the emerald on the navy badge.
- [x] Merged through a Forgejo pull request into `development`.

## Constraints and prior findings

Modrinth icon cap 256 KiB. The emerald texture is Mojang's: rendered into the icon, not redistributed as a file.
