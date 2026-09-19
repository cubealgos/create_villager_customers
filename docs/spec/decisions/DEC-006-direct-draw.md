---
title: "create_villager_customers DEC-006 — Direct draw from the network stock, no packages"
type: "spec"
category: "create_villager_customers"
---

# `DEC-006` — Direct draw from the network stock, no packages

**Status:** decided by Kevin, 2026-09-19.

The mod removes a unit's goods from the shop's packager network itself; they vanish with the
villager exactly as in vanilla trading. No `ShoppingList`, no `PackageOrder`, no address, no
asynchronous delivery through Create's packager network — the mechanism `TableClothBlockEntity
.useShop`/`StockTickerInteractionHandler.interactWithShop` use for a real player
(`04-architecture.md` `ARCH-DEC-004`). Payment and the xp nuggets go straight into the stock
ticker's payment box (`Container.insert`, confirmed public and already used by the real checkout).

Alternative considered: routing a villager's purchase through Create's actual package system —
spawning a `ShoppingListItem`-equivalent pledge and letting the packager network physically produce
and ship a package. Rejected: that path is hard-wired to a `Player` at both steps (research §A,
"Non-player checkout"), asynchronous, and would leave a villager waiting on a package it has
nowhere to receive. A direct draw is also how a villager already "receives" goods in a vanilla
trade — instantly, with nothing physically carried. Cost if wrong: this is the mod's largest
technical risk (`04-architecture.md` `ARCH-FAIL-002`) — no public stock-removal call was confirmed
in the research pass; if none exists, the first ticket needs a further mixin into Create Fly's
network or summary classes.
