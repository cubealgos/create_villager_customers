# create_villager_customers

A Create Fly add-on for Minecraft 26.2 on Fabric: during their working hours, villagers with an
offer they can still fulfil walk to the player's own Create shop and execute that offer against
it, buying or selling straight against the shop's network stock and payment box.

**This file routes. It does not hold content.** The specification is `docs/spec/`.

## Read this before you do that

| about to… | read first |
|---|---|
| anything at all | `docs/spec/README.md`, then the one domain file you need |
| find where something lives | `docs/map.md`; generated, never edited |
| touch `villager_customers.model` | it has no Minecraft imports; the build's `verifyPurePackage` enforces it |
| touch the villager's shopping behaviour | `docs/spec/domains/customer.md`, `docs/spec/04-architecture.md` `ARCH-DEC-002` |
| touch the shop transaction | `docs/spec/domains/transaction.md`, `docs/spec/04-architecture.md` `ARCH-DEC-004` |
| touch the shop discovery or POI | `docs/spec/domains/shop.md` |
| add or change a mixin | `docs/spec/04-architecture.md` `ARCH-DEC-002`, the mixin config |
| add a dependency | `docs/spec/decisions/DEC-003-licence.md` (MIT) and heimathafen's dependency policy |
| commit | scope `villager_customers`, the ticket key (`VC-N`) in the subject |

## Working here

```
kontor claim VC-N
kontor branch new VC-N <slug>
just check
```

`just --list` shows the task surface; `just spec-sync` refreshes `docs/spec/` from the vault; `just map` regenerates the map.

## Standing rules

- The spec is authoritative; `docs/spec/` is a copy of heimathafen's vault.
- A design question the spec does not answer is asked, never decided inline.
- Nothing leaves the player's machine: no telemetry, no network calls (`docs/spec/operations/compliance.md`).
- Always keep a playable build: `just client` boots with Create Fly at every merge.
