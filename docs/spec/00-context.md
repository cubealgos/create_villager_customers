---
title: "create_villager_customers spec — context: why, for whom, and what it will not do"
type: "spec"
category: "create_villager_customers"
---

# 00 — Context

## Why this exists

`create_civilization` and its sibling `create_metered_motor` both assume a player who keeps
trading: the metered motor burns emeralds, and every Create machine downstream of it eventually
needs more. Vanilla only lets a *player* walk to a villager. Villager Customers reverses one leg
of that: during their working hours, villagers with an offer they can still fulfil walk to the
player's own Create shop and execute that offer against it — buying goods out of the shop's stock
with emeralds, or selling goods into it for emeralds, whichever direction their own trade runs.
The player builds and stocks the shop once; villagers keep the trade moving without being chased
down one at a time.

It is the third small Create Fly add-on, started in parallel with `create_metered_motor`
(`rulings-2026-09-19.md`). Building it teaches the team a shape none of the others needed: a
mixin into vanilla's villager brain (no Fabric event exists for this), a point-of-interest
registration, and a transaction run against Create Fly's shop internals rather than through its
own player-facing checkout.

## Who it is for

- Players on Minecraft 26.2 with Fabric and Create Fly who have built at least one table-cloth
  shop and want it to see traffic without standing at it.
- Server operators who install it alongside Create Fly and expect nothing to configure.
- Modpack authors who want villager economies and Create's shop system to touch each other, and
  who can retune the behaviour's pacing (`decisions/DEC-009-chance-per-restock.md`) through data.

## Business context

No business model, no revenue, no telemetry. Published on Modrinth under MIT, source on the
cubealgos Forgejo with a GitHub mirror and tracker, public from the first commit
(`decisions/DEC-003-licence.md`) — the same place `create_brass_compass` and
`create_metered_motor` ended up.

## What it will not do

- No new villager profession, no new trades: it executes offers a villager already has, exactly
  as vanilla generated them.
- No change to villager AI outside working hours: panicking, sleeping, playing, raiding and every
  other activity are untouched.
- No packages, no delivery, no address: goods are drawn directly from the shop's network stock and
  vanish with the villager, as in a normal vanilla trade (`decisions/DEC-006-direct-draw.md`).
- No shop protection and no ownership model: any table-cloth shop a villager's village can reach is
  fair game, same as any Create Fly shop is fair game to any player who can reach it.
- No currency of its own: emeralds and Create's own experience nugget only, in containers Create
  Fly already defines.
- No new screen, no new block, no new item: the player reads what happened through Create Fly's
  own stock ticker and payment-box views, exactly as before this mod is installed.

## Success

Kevin builds a table-cloth shop selling wheat for emeralds, walks away, comes back after a
Minecraft day to find the stock lower, emeralds and a few experience nuggets sitting in the
payment box, and a farmer villager that has clearly been shopping — with no menu, no package, and
nothing about the shop itself that looks any different from a shop only players ever used.
