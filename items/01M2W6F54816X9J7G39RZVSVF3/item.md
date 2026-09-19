---
schema_version: 1
id: 01M2W6F54816X9J7G39RZVSVF3
key: VC-7
type: docs
title: "Modrinth listing assets ahead of the release: badge icon, paste-ready body and settings, gallery shot list"
created_by: kevin
created_at: 2026-09-19T06:43:11Z
---

## Scope

Everything for the Modrinth page that can be produced before the release build exists, as `create_metered_motor`'s `MM-11`, so the release ticket (`VC-8`) only uploads: `docs/modrinth/body.md` (project settings — name, slug `villager-customers`, summary, categories, licence, sides, loader, game version, dependencies Create Fly and Fabric API; version settings; the markdown body drawn from `docs/spec/00-context.md` and the three domain files), `docs/modrinth/gallery.md` (five shots with captions, taken later on the release build — a shop with stock, a villager mid-walk, a villager mid-trade, a payment box with price and nuggets, a stock ticker screen showing reduced stock), `tools/icon.py` adapted from the siblings' (the Create blueprint badge; the subject is a villager standing at a table cloth, since this mod adds no block or item sprite of its own to eventually swap in — `00-context.md`'s "no new screen, no new block, no new item"), a placeholder 16×16 sprite under `docs/modrinth/placeholder-villager-customers.png` drawn now, `docs/modrinth/icon.png` rendered from it, `just icon` working.

## Approach

Copy `create_metered_motor`'s `docs/modrinth/body.md`, `gallery.md` and `tools/icon.py` (itself adapted from `create_brass_compass`); rewrite the text from `docs/spec/00-context.md`, `domains/customer.md`, `domains/transaction.md` and `domains/shop.md` — what the mod does (a villager walks to a shop and executes its own offer), who it's for, what it will not do. Draw the placeholder with the same tool `tools/icon.py` uses, in the flat pixel style of Create's own icons (a Create blueprint badge framing a villager silhouette at a table-cloth-shaped sign). Unlike `MM-11`, there is no later ticket that supplies a "real" sprite to swap in — this mod has no block or item of its own (`00-context.md`) — so the placeholder is the icon's final subject, not a stand-in; record that divergence here rather than leaving a stale "replace later" comment in the tool.

## Acceptance criteria

- [ ] `docs/modrinth/body.md`, `docs/modrinth/gallery.md`, `docs/modrinth/icon.png` (512×512, under Modrinth's 256 KiB icon cap) and `tools/icon.py` exist; `just icon` regenerates the icon from `docs/modrinth/placeholder-villager-customers.png`.
- [ ] `body.md`'s project settings section names slug `villager-customers`, MIT licence, server-only side (`contracts/platform-matrix.md`: "Server only"), Minecraft 26.2, and dependencies Create Fly and Fabric API.
- [ ] `body.md`'s markdown body reflects `00-context.md`'s "Who it is for" and "What it will not do" sections without inventing content beyond the spec.
- [ ] `gallery.md` lists five shots with captions, marked as taken later on the release build.
- [ ] Merged through a Forgejo pull request into `development`, verified against `origin/development`.

## Constraints and prior findings

Create add-on icon theme and palette: personal vault `vault/technical/minecraft/create-fly-26-2.md` §"Create add-on icon theme". Modrinth icon cap 256 KiB, gallery 5 MiB (from `MM-11`'s own constraints, reused here since the caps are Modrinth's, not per-mod). `00-context.md`: no new screen, block or item of this mod's own — the icon subject (a villager at a table cloth) has no future in-game sprite to eventually replace the placeholder with, the one respect in which this ticket's shape diverges from `MM-11`'s (whose placeholder motor sprite was later replaced once `MM-7` shipped the real one). Blocked by `VC-1` — `tools/icon.py` and the `docs/modrinth/` layout it writes into come from the bootstrap.
