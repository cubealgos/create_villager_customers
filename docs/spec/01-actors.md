---
title: "create_villager_customers spec — actors: who touches a villager's shopping trip and what each may do"
type: "spec"
category: "create_villager_customers"
---

# 01 — Actors

| ID | Actor | May | May not |
|---|---|---|---|
| `ACTORS-001` | **Villager (customer)** | During working hours, roll the per-restock chance; walk to a matching shop; execute a matched offer against it, repeatedly while uses and stock allow; gain trade xp | Trade outside `WORK`; trade while panicking or at night; open a menu; execute an offer whose cost has a second item |
| `ACTORS-002` | **Shop owner (player, in practice)** | Build and stock a table-cloth shop as for any Create Fly shop; empty the payment box; read Create's own stock and payment screens | Configure this mod: there is no screen or setting of its own to open |
| `ACTORS-003` | **Other players** | Everything the owner may, on any shop they can reach: there is no ownership. Witness a villager mid-trip or mid-transaction | Nothing distinct; a villager's trade is not a player interaction and touches no player's inventory |
| `ACTORS-004` | **Server** | Roll the chance; drive the walk; check the match, the stock and the payment box; draw goods, insert payment and xp nuggets, increase the offer's uses, grant trade xp; own the trip and cooldown memory | Trust a client with any of this: nothing here crosses a network packet the client can influence |
| `ACTORS-005` | **Create Fly** (dependency) | Supply the table cloth, the stock ticker, the packager network, the payment container, the experience nugget item | Be driven through its player-facing checkout (`TableClothBlockEntity.useShop`, `StockTickerInteractionHandler.interactWithShop`): both require a real `Player` (`04-architecture.md` `ARCH-DEC-004`) |
| `ACTORS-006` | **Datapack or resource pack author** | Retune the chance per restock, the xp-nugget conversion, the walk timeout and the cooldown as data constants (`decisions/DEC-009-chance-per-restock.md`) | Change the match rule or the mixin: those are code |
| `ACTORS-007` | **Server operator** | Install and remove the mod; nothing to configure beyond the data constants above | Read or edit a trip in progress from outside the world save |
| `ACTORS-008` | **Contributor** | Build, test and change the mod under MIT | Add telemetry or network calls (`operations/compliance.md`) |

## Findings from writing this

- **`FINDING-1`** There is no menu and no player identity anywhere in the transaction. Every other
  actor in Create Fly's shop system assumes a `Player`; this mod's whole technical shape
  (`04-architecture.md` `ARCH-DEC-004`) exists because that assumption does not fit a villager, and
  no non-player checkout path exists to reuse.
- **`FINDING-2`** The villager is the actor with the random hand, as in `create_metered_motor`, but
  the randomness gates *whether it goes shopping at all* (`decisions/DEC-009-chance-per-restock.md`),
  not what it gets: once it decides to go, it trades as much as its offer and the shop's stock and
  payment box allow.
- **`FINDING-3`** The datapack author's dials are all behavioural — how often, how much xp per
  trade, how long a trip may take. The trade's own economics (what a villager's offer actually
  costs and gives) stay exactly what vanilla or another datapack already rolled; this mod never
  touches an offer's numbers, only whether and how it gets executed.
- **`FINDING-4`** Create Fly's own automation (funnels, arms, chutes feeding the packager network)
  is untouched: the mod reads the network's stock summary and draws from it directly
  (`decisions/DEC-006-direct-draw.md`), never through a transfer path, so the player's existing
  logistics keep working exactly as before this mod is installed.
