---
title: "create_villager_customers spec — testing"
type: "spec"
category: "create_villager_customers"
---

# Testing (`TEST`)

| Layer | What | Where |
|---|---|---|
| Unit | The match rule (cloth vs. offer, including the two-cost-item skip); the xp-nugget conversion arithmetic; the chance roll given a fixed random source — all pure, no Minecraft imports needed, checked by `verifyPurePackage` as the siblings do | `src/test` |
| Game tests | A mock villager with a known offer against a mock (or real) table cloth and stock ticker network: the match finds or correctly skips a candidate; the direct draw reduces the network's stock and inserts price plus nuggets into the payment box; `increaseUses`/`notifyTrade` fire once per unit; the "stock too low" and "cash register full" refusals stop the loop and leave earlier units standing; the POI search respects village membership; the mixin actually adds the behaviour to a real `Villager`'s `WORK` activity | `src/gametest`, Loom `runGameTest` |
| Client / Kevin's checklist | The walking itself: whether a villager visibly detours to a shop, arrives, pauses convincingly, and returns to `WORK` — pathfinding and brain timing are not meaningfully testable headless, so this is a release checklist item, not a game test | release checklist |
| Development tool | `/villager_customers debug trip <villager>` forces a chance-roll success and prints the search result (candidate found, distance, matched offer) for a targeted villager, for screenshots and behaviour checks; registered only when Fabric reports a development environment, as the siblings' debug commands are | `villager_customers.debug`, `just client` |

**What is genuinely hard here, stated plainly:** the mod's core behaviour lives inside a real
`Villager`'s brain and Create Fly's real block entities — there is no pure-logic substitute for
"does the villager actually walk there." Game tests can drive a `Villager` and tick it far enough
to prove the search, the match and the transaction are each correct in isolation; only a human
watching `just client` can confirm the whole trip looks right end to end.

`TEST-REQ-001`: every `CUSTOMER-REQ`, `TRANSACTION-REQ` and `SHOP-REQ` names its test in the ticket
that implements it.
`TEST-REQ-002`: a deliberate-break proof for the pure-package check, once.
`TEST-REQ-003`: a game test proves the mixin coexists with at least one other mod that also
mixins `Villager.registerBrainGoals`, given `ARCH-FAIL-004`.
