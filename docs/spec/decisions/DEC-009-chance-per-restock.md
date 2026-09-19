---
title: "create_villager_customers DEC-009 — A random chance per restock, default 50%, this sheet's proposal"
type: "spec"
category: "create_villager_customers"
---

# `DEC-009` — A random chance per restock, default 50%, this sheet's proposal

**Status:** decided by Kevin, 2026-09-19 that a per-restock chance is the mechanism; the default
value is this sheet's proposal, to confirm.

A villager trades as much as it can once it decides to go — until an offer runs out of uses or a
shop runs out of stock — but each restock only gives a random chance that it decides to wander to
a shop at all (`rulings-2026-09-19.md`). This keeps villagers from all converging on the nearest
shop the instant one exists, and keeps the pacing tunable without touching code
(`domains/customer.md` `CUSTOMER-REQ-002`, a configurable-in-data constant,
`contracts/public-surface.md`).

**This sheet proposes a default of 50%**: high enough that a player with a working shop sees
regular traffic within a few restocks, low enough that a village of a dozen villagers does not
empty a shop's stock in one tick the first time it restocks. Vanilla's own restock cadence (roughly
once a Minecraft day, `Villager.shouldRestock`) already spaces the rolls out; 50% was picked as a
plausible middle value, not derived from any number in the research note. Kevin, 2026-09-19 or
retune before 1.0.

Alternative considered: a flat 100% (every restock sends the villager shopping if anything
matches). Rejected as a default: it would make every villager in a village with any matching shop
behave identically and simultaneously, which reads as mechanical rather than like a population of
individuals. Cost if wrong: one number in a data file (`contracts/public-surface.md`); a server
operator or datapack author can already override it without a rebuild.
