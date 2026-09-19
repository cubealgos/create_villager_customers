---
title: "create_villager_customers spec — platform matrix"
type: "spec"
category: "create_villager_customers"
---

# Platform matrix (`PLATFORM`)

| Row | Value | How it is checked |
|---|---|---|
| Minecraft | 26.2 (`~26.2` in `fabric.mod.json`) | `just doctor`, game tests on a dedicated server |
| Fabric Loader | ≥ 0.19.5 | `fabric.mod.json` |
| Fabric API | ≥ 0.160.0 (the game-test entrypoint and the development command only — no Fabric event carries this mod's core hook, `04-architecture.md` `ARCH-DEC-002`) | `fabric.mod.json` |
| Create Fly | `26.2-rc-2-6.0.9-1`, mod id `create`, declared version `6.0.9-1`, `implementation` coordinate `maven.modrinth:create-fly`, pinned | `fabric.mod.json` depends `create`; `NOTICE` |
| Java | 25 | `just doctor` |
| Gradle / Loom | 9.5.1 wrapper / 1.17 | wrapper properties |
| Operating systems | macOS, Linux, Windows: the JVM's | not tested separately; nothing native |
| Client and server | Server only: every hook (`Villager`, `TableClothBlockEntity`, `StockTickerBlockEntity`) is server-side; the client runs no code of this mod's own | game tests (server) |
| Villager brain | 26.2's `EnvironmentAttribute<Activity>` schedule system and `Brain.addActivity`; no `Schedule`/`ScheduleBuilder` class exists in this jar (research §B) | game test spinning up a real `Villager` |
| Mixin targets | `Villager.registerBrainGoals(Brain)` (private, both `minecraft`), `TableClothBlockEntity`, `StockTickerBlockEntity` (`create`) | mixin config, `just check` |

`PLATFORM-REQ-001`: **If** any row moves, **then** `just doctor` fails naming the row.
`PLATFORM-REQ-002`: **If** a Create Fly release renames or removes a method this mod's mixins
target, **then** the build fails at compile time rather than at runtime, since mixin targets are
resolved against the pinned jar.
