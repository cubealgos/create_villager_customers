---
schema_version: 1
id: 01M2W6F66WSJCH9W1R81RJ8121
key: VC-8
type: chore
title: "Release preparation and cut: 1.0.0+26.2"
created_by: kevin
created_at: 2026-09-19T06:43:12Z
---

## Scope

Release preparation and the cut itself, as `create_metered_motor`'s `MM-10` and `create_brass_compass`'s own release ticket: the version `1.0.0+26.2` (`operations/release.md`, `<mod>+<mc>` SemVer over `contracts/public-surface.md`), a `CHANGELOG.md` entry, `just release` building the jar and checksum from a clean checkout at a tag (`REL-REQ-001`), a security pass (`operations/compliance.md`), a release branch cut from `development`, the production merge through a Forgejo pull request, the tag itself, and the resulting dist artifact handed to Kevin. Out of scope: any behavioural change — this ticket only proves and ships what `VC-1` through `VC-7` built.

## Approach

Mirror `MM-10`'s release shape exactly: bump the version in `gradle.properties`/`fabric.mod.json` to `1.0.0+26.2`, write the `CHANGELOG.md` entry from the spec's requirement set, run `just release` from a clean checkout at the intended tag, re-run the security pass checklist (`operations/compliance.md`: no network call reconfirmed via `SourceSurfaceTest`, `NOTICE` credits current, licence unchanged), cut a release branch from `development`, open the Forgejo pull request into `production`, tag `1.0.0+26.2` once merged, and hand the built jar and checksum to Kevin alongside the Modrinth listing from `VC-7`.

## Acceptance criteria

- [x] `CHANGELOG.md` carries a `1.0.0+26.2` entry.
- [ ] `just release` builds the jar and checksum from a clean checkout at the tag (`REL-REQ-001`).
- [x] Release notes list the Minecraft, Fabric Loader and Create Fly versions tested (`REL-REQ-002`) and the default values in force for the chance per restock, the xp-nugget conversion, the walk timeout and the cooldown (`REL-REQ-003`; `contracts/public-surface.md`).
- [x] Security pass recorded: no network call of any kind (`COMP-REQ-001`, `SourceSurfaceTest` green), `NOTICE` credits Create Fly (CC0), Create (MIT) and Fabric (Apache-2.0), MIT `LICENSE` unchanged.
- [ ] Release branch cut from `development`, merged into `production` through a Forgejo pull request, tagged `1.0.0+26.2`.
- [ ] Dist jar and checksum handed to Kevin; Modrinth listing published using `VC-7`'s assets, with the five gallery shots actually taken on this release build.

## Constraints and prior findings

`operations/release.md` `REL-REQ-001..003`; `operations/compliance.md`: no signing at 1.0 (open, deferred rather than decided), vulnerability disclosure via the public GitHub issues mirror only, no private channel. Mirrors `create_metered_motor`'s `MM-10` shape directly. Blocked by `VC-6` (every `CUSTOMER-REQ`, `TRANSACTION-REQ` and `SHOP-REQ` proven by a named test, three green `just check` runs, and Kevin's client checklist done) and `VC-7` (the Modrinth listing assets ready to publish alongside the cut).
