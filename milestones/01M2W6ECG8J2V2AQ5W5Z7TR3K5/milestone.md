---
schema_version: 1
id: 01M2W6ECG8J2V2AQ5W5Z7TR3K5
key: M4
title: Release 1.0.0+26.2
status: backlog
created_at: 2026-09-19T06:42:45Z
---

## Goal

Version 1.0.0+26.2 published on Modrinth, cut only once every requirement is proven and the listing is ready.

## Scope

In: release preparation and the cut itself — version bump, changelog, `just release`, the security pass, the release branch, the production merge through a Forgejo pull request, the tag, the dist artifact handed to Kevin (`VC-8`). Out: any behavioural change — this milestone only ships what `M1` through `M3` built and proved.

## Exit criteria

- Tag on `production`, jar and checksum attached, the Modrinth listing live, `SUPPORT.md` present.
- Release notes list the tested Minecraft/Fabric Loader/Create Fly versions and the default values in force for the chance per restock, the xp-nugget conversion, the walk timeout and the cooldown.

## Tickets

- VC-8 — Release preparation and cut: 1.0.0+26.2

## Depends on

M3: VC-8 is blocked by VC-6 (every requirement proven, three green `just check` runs, Kevin's client checklist done) and VC-7 (the Modrinth listing assets ready to publish alongside the cut).
