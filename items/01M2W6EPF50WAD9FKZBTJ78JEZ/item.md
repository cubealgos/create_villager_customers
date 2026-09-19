---
schema_version: 1
id: 01M2W6EPF50WAD9FKZBTJ78JEZ
key: VC-1
type: chore
title: "Bootstrap: Gradle with Loom and Create Fly, entrypoints, licence, notice, routing, tools, spec copy, smoke game test"
created_by: kevin
created_at: 2026-09-19T06:42:56Z
---

## Scope

The repository as `docs/spec/04-architecture.md` `ARCH-DEC-001` and `docs/spec/decisions/DEC-004-toolchain.md` describe it: one Gradle project on Loom 1.17 with Create Fly `26.2-rc-2-6.0.9-1` pinned (`fabric.mod.json` declaring `6.0.9-1`, `contracts/platform-matrix.md`) and Fabric API 0.160.0, Java 25, Gradle 9.5.1 wrapper, Kotlin DSL and version catalog copied from `create_metered_motor`'s scaffold: `build.gradle.kts`, `settings.gradle.kts`, `gradle/`, `gradlew`, `gradle.properties`, `justfile`, `tools/` (`doctor.py`, `map.py`, `release_notes.py`, `icon.py` with a placeholder sprite), `.ci/install-tools.sh` as it now stands (apt curl, git, python3; `just` fetched as a tarball), `.woodpecker.yml`, `.gitea/default_merge_message/*`, `CLAUDE.md` routing adapted to this repo's three domains (customer, transaction, shop), `.gitignore`. MIT `LICENSE` (`decisions/DEC-003-licence.md`), `NOTICE` crediting Create Fly (CC0), Create (MIT) and Fabric (Apache-2.0) (`operations/compliance.md`), `README.md`, `SUPPORT.md`, `CHANGELOG.md` skeleton, `docs/spec/` as a copy of the vault spec via `just spec-sync` pointed at the cubealgos heimathafen sibling `create_villager_customers`. Main and client entrypoints for mod id `villager_customers` (package `villager_customers`, classes `VillagerCustomers` and `client.VillagerCustomersClient`); a mixin config `villager_customers.mixins.json` with the refmap for `minecraft`, empty of mixins until `VC-3`; `verifyPurePackage` gating the `villager_customers.model` package; a smoke game test proving the mod loads beside Create Fly (`SmokeGameTest`); `SourceSurfaceTest` asserting no networking type is referenced by the mod (`COMP-REQ-001`), established here so later tickets extend it. `fabric.mod.json`'s `contact` block: source `https://git.cubealgos.de/cubealgos/create_villager_customers`, issues `https://github.com/cubealgos/create_villager_customers/issues`, Modrinth slug `villager-customers`.

## Approach

Copy the verified toolchain and repository shape from `create_metered_motor`'s `MM-1` (wrapper, Loom plugin id, Modrinth repository with `exclusiveContent`, the `justfile` recipes, `tools/doctor.py`, `tools/map.py`, `tools/release_notes.py`, `tools/icon.py` and their tests, the `.ci/install-tools.sh` apt/just-tarball fix already folded in from three Woodpecker runs on the compass) into one Gradle project for `create_villager_customers`; adapt `CLAUDE.md`'s routing table to this repo's three domain files (`domains/customer.md`, `domains/transaction.md`, `domains/shop.md`) instead of the motor's (motor, trade, ui). Point `just spec-sync`'s vault path at `../heimathafen/vault/projects/create_villager_customers/spec` (the cubealgos sibling, per `contracts/platform-matrix.md`'s doctor check). Declare the `villager_customers.mixins.json` config with a `minecraft` refmap target and an empty `mixins` array — `VC-3` is the first ticket to populate it (the transaction ticket owns the largest open risk, `ARCH-DEC-004`, and may need an accessor mixin into `create` first; `VC-4` then adds the `Villager.registerBrainGoals` behaviour mixin to the same config). First thing to verify: `just spec-sync` produces a byte-identical `docs/spec/` against the vault source read for this ticket.

## Acceptance criteria

- [ ] `just check` is green, including a smoke game test (`SmokeGameTest`) proving the mod loads on a dedicated server with Create Fly present.
- [ ] `just doctor` is clean: toolchain floors met (Java 25, Gradle 9.5.1, Loom 1.17, Fabric Loader ≥ 0.19.5, Fabric API ≥ 0.160.0), `docs/spec/` identical to the vault, merge templates present on the default branch.
- [ ] `fabric.mod.json`'s `contact` block links the Forgejo repo, the GitHub issues tracker, and the Modrinth slug `villager-customers`.
- [ ] `verifyPurePackage` runs against `villager_customers.model` and passes; a deliberate import proves the check fails once (`TEST-REQ-002`, satisfied here and restated by `VC-6`).
- [ ] `SourceSurfaceTest.noNetworkingTypeIsReferencedByTheMod` passes (`COMP-REQ-001`).
- [ ] `villager_customers.mixins.json` exists with a `minecraft` refmap and an empty `mixins` array; the build succeeds with no mixin yet applied.

## Constraints and prior findings

`docs/spec/contracts/platform-matrix.md` (`PLATFORM-REQ-001`, `PLATFORM-REQ-002`), `docs/spec/decisions/DEC-004-toolchain.md`, `docs/spec/decisions/DEC-003-licence.md`, `docs/spec/operations/compliance.md` (`COMP-REQ-001`). Create Fly coordinate, mod id `create`, declared version `6.0.9-1`, CC0, and the 26.2 toolchain floors (Java 25, Gradle 9.5.1, Loom 1.17, Loader 0.19.5, Fabric API 0.160.0) are verified against the vault (spec `README.md` §5 platform matrix). Unlike either Create Fly sibling, this mod's mixin config ultimately targets two upstreams — `minecraft` (`Villager`, for the brain hook) and possibly `create` (`TableClothBlockEntity`, `StockTickerBlockEntity`, only if `VC-3` finds no public removal call) — `ARCH-DEC-001`; this ticket only stands up the `minecraft` refmap, since nothing is mixed in yet.

`create_metered_motor`'s `MM-1` is the direct model for this ticket's shape and its `just doctor`/`just check` acceptance bar, including the CI installer fix (apt-installs for curl, git, python3, `just` fetched as a tarball — the pipeline image lacks all four) folded in from the start rather than discovered again the hard way. `create_brass_compass`'s `BC-1` is the template one layer further back for `SourceSurfaceTest`'s shape (network scan, later extended for translation-key coverage — not applicable here, since this mod adds no translation keys at all, `contracts/public-surface.md`).
