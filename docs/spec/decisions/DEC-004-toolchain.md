---
title: "create_villager_customers DEC-004 — Java 25, Gradle 9.5.1, Loom 1.17, Kotlin DSL, version catalog, one Gradle project, two mixin targets"
type: "spec"
category: "create_villager_customers"
---

# `DEC-004` — Java 25, Gradle 9.5.1, Loom 1.17, Kotlin DSL, version catalog, one Gradle project, two mixin targets

**Status:** decided by Kevin, 2026-09-19.

The same toolchain as `create_brass_compass` and `create_metered_motor`, verified in the vault's
Minecraft notes: Java 25, Gradle 9.5.1, Loom 1.17, Kotlin DSL with a version catalog. One Gradle
project, for the same reason the motor gives — no sim layer to keep pure, and `verifyPurePackage`
(`operations/testing.md`) checks the match rule and the xp-nugget arithmetic stay free of
Minecraft imports without a second module.

Unlike either sibling, this mod's mixin config targets two upstreams: `minecraft` (`Villager`) and
`create` (`TableClothBlockEntity`, `StockTickerBlockEntity`), since no Fabric event exists for
either the villager-brain hook or a non-player shop transaction (`04-architecture.md`
`ARCH-DEC-001`, `ARCH-DEC-002`, `ARCH-DEC-004`).

Alternative considered: splitting a pure module from the Minecraft-facing one, as
`create_civilization` does. Rejected for the same reason as the motor: the pure surface (the match
rule, the nugget conversion, the chance roll) is small enough that a package check gives the same
guarantee at a fraction of the build complexity. Cost if wrong: the pure package grows large enough
that a module split becomes a ticket, not a rewrite.
