---
title: "create_villager_customers DEC-007 — The shop earns the trade's xp, as experience nuggets"
type: "spec"
category: "create_villager_customers"
---

# `DEC-007` — The shop earns the trade's xp, as experience nuggets

**Status:** decided by Kevin, 2026-09-19; the conversion ratio is this sheet's proposal.

A villager's own trade xp is not the only xp a mod-driven trade produces: the shop earns the
trade's xp too, inserted into the payment box as `create:experience_nugget`
(`AllItems.EXP_NUGGET`, confirmed present in the Create Fly jar) — the same container the price
lands in, so a full box refuses the whole unit rather than dropping nuggets on the ground
(`domains/transaction.md` `TRANSACTION-REQ-003`, `TRANSACTION-FAIL-003`). Experience bottles are
the fallback only if nuggets prove unusable at the first ticket.

Because a table cloth's price is one item, the xp cannot ride on the trade stack itself — it must
be a second stack inserted alongside the price (`TRANSACTION-REQ-005`, `010`). **This sheet
proposes a default conversion of the offer's xp divided by one nugget's value, rounded up (3 xp per nugget in upstream Create, read at the first ticket)**, regardless of the offer's own xp
value, as the simplest starting point that cannot be gamed by picking high-xp offers. The exact xp
one nugget represents was not traced in the research pass and is confirmed at the first ticket
(`domains/transaction.md` §7); if it turns out to represent much more or much less xp than a
typical vanilla trade, the ratio is Kevin's to set once that number is known.

Alternative considered: scaling nugget count to the offer's `xp` field exactly. Deferred rather
than rejected: it needs the per-nugget xp value first, which is an open question, not a design
choice. Cost if wrong: a data constant (`contracts/public-surface.md`), not a rewrite.

**Amended 2026-09-20 (Kevin, from the client check):** the nuggets carry the value of the player's
xp orb the trade would have dropped (`3 + random(4)` xp, one or two nuggets), and that orb is
suppressed for a mod-driven unit; the offer's levelling xp stays the villager's own. The earlier
"offer's xp" reading over-paid (up to ten nuggets on a master trade) and doubled the xp with the
orb. Ticket VC-12.
