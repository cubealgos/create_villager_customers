---
title: "create_villager_customers spec — compliance, security and governance"
type: "spec"
category: "create_villager_customers"
---

# Compliance, security and governance (`COMP`, sheet §6)

A distributed product carries the same obligations as its siblings, landing in different places
(`workflows/session/start-new-project.md`).

| Area | Position |
|---|---|
| GDPR: what leaves the user's machine | Nothing. No telemetry, no update check, no outbound network call of any kind (`COMP-REQ-001`). No player names in the mod's data; the only saved state is two memory-module values per villager (`contracts/data-contract.md`). |
| Hosted parts we run | None. Modrinth hosts the file and its page. |
| Impressumspflicht | Attaches to a public web presence; there is none beyond the platform pages. Revisit if a site exists. |
| Licence and notices | MIT (`decisions/DEC-003-licence.md`); `NOTICE` credits Create Fly (CC0), Create (MIT), Fabric (Apache-2.0). No Minecraft or Create Fly textures are copied. |
| Supply chain and release integrity | Builds from a tagged commit with pinned dependencies; the release checksum is in the release notes; no signing at 1.0. |
| Vulnerability disclosure | The public issue tracker only, on the GitHub mirror (`https://github.com/cubealgos/create_villager_customers/issues`); no private channel, no e-mail address published. Forgejo stays the source of truth for code. |
| Server trust boundary | Every action a villager takes runs entirely server-side; there is no menu, no packet, and no client input anywhere in the trade path — a stricter boundary than a mod with a screen, since there is nothing for a client to influence at all. |
| AI Act, GoBD, sector regulation | Not applicable: no AI component, no financial records, no regulated sector. |

`COMP-REQ-001`: the mod shall make no network call of its own; a source-scan test asserts it
(`SourceSurfaceTest`, as `create_brass_compass` and `create_metered_motor`).

Open: release signing (minisign) before 1.0 or after.
