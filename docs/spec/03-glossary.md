---
title: "create_villager_customers spec — glossary"
type: "spec"
category: "create_villager_customers"
---

# 03 — Glossary

| Term | Means |
|---|---|
| **table cloth** | Create Fly's shop sign block: a price tag and a link to a stock ticker, no inventory of its own. What this mod treats as "a shop" when it has both. |
| **stock ticker** | Create Fly's network hub block: holds the payment box (`receivedPayments`) and reports the network's live stock. |
| **packager network** | The wider set of crates and vaults a stock ticker fronts; where a shop's actual stock lives. |
| **payment box** | The stock ticker's `receivedPayments` container: where this mod inserts a completed unit's price and xp nuggets, same as Create's own player checkout does. |
| **keeper present** | The stock ticker's own readiness check (`isKeeperPresent()`); a table cloth is not a shop for this mod unless its linked ticker passes it. |
| **shop, shophood** | A table cloth with a non-empty price and goods, linked to a stock ticker that has a keeper present (`domains/shop.md`). |
| **match rule** | The cloth's goods equal the matched offer's cost stack; the cloth's price equals the offer's result stack (`domains/transaction.md` `TRANSACTION-REQ-001`). |
| **direct draw** | Removing a unit's goods from the network's stock without Create's player checkout or its package/address system; goods vanish with the villager as in vanilla trading (`decisions/DEC-006-direct-draw.md`). |
| **xp nugget** | `create:experience_nugget`; inserted into the payment box alongside a unit's price so the shop earns the trade's xp (`decisions/DEC-007-xp-nuggets.md`). |
| **offer** | A villager's own `MerchantOffer`: a cost stack (and optionally a second cost item), a result stack, and remaining uses. Never created or changed by this mod. |
| **uses / restock** | An offer's remaining trade count; vanilla resets it on a periodic restock (`Villager.restock()`/`shouldRestock()`), which is also when this mod rolls its chance. |
| **working hours** | Vanilla's `Activity.WORK`, the only activity this mod's behaviour runs in. |
| **shopping trip** | One villager's attempt, from a successful chance roll to arrival (or cancellation): remembered as a trip-target memory, cleared on completion or cancellation. |
| **trip target** | The remembered position of the shop a villager is walking to; a custom `GlobalPos` memory module, cleared when the trip ends (`contracts/data-contract.md`). |
| **cooldown** | A per-villager delay before the next chance roll after a cancelled or timed-out trip, independent of the restock cycle (`domains/customer.md`). |
| **unit (of a trade)** | One full execution of the match rule against the shop: one draw, one payment, one uses-increase, one xp grant. A single visit may execute many units of the same offer. |
| **point of interest (POI)** | Vanilla's registry of "interesting" block positions a village indexes; this mod registers table-cloth block states into it so villagers find shops the way they find job sites (`decisions/DEC-008-poi.md`). |
| **village membership** | Vanilla's own notion of which POIs a given villager's village can reach; this mod searches within it rather than a distance this mod invents. |
| **cash register full** | Create's own refusal when the payment box has no room; this mod reuses the name and the behaviour, stopping the trip rather than dropping items. |
| **demand / reputation** | Vanilla mechanisms on `MerchantOffer` (`getDemand()`, `updateDemand()`) and on the player-villager relationship; both left untouched by this mod's trades (`domains/transaction.md` §8). |
