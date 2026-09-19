---
title: "create_villager_customers DEC-005 — Both directions are one act"
type: "spec"
category: "create_villager_customers"
---

# `DEC-005` — Both directions are one act

**Status:** decided by Kevin, 2026-09-19, correcting an earlier framing.

A table cloth defines a trade as goods for a price, and a stock ticker's payment box takes every
payment regardless of which item it is; "a villager buys from the shop" and "a villager sells to
the shop" are therefore the same mechanism, not two features. The match rule
(`domains/transaction.md` `TRANSACTION-REQ-001`: cloth's goods = offer's cost stack, cloth's price
= offer's result stack) and the single execution path (`TRANSACTION-REQ-004`–`006`) cover both
directions without branching on which one it is. `02-journeys.md` `UC-001` (a farmer buys wheat:
currency flows in, a raw good flows out) and `UC-002` (a librarian sells a book: a crafted good
flows in, currency flows out) run through the identical rule and the identical loop.

Alternative considered: two separate behaviours, one for "villager buys" and one for "villager
sells," each with its own match check. Rejected: it was the original framing, and Kevin corrected
it during the rulings session precisely because the table cloth and the payment box do not
distinguish direction — building two behaviours would duplicate the entire transaction domain for
no rule that actually differs. Cost if wrong: none identified; this is the simpler design in every
respect the ruling touches.
