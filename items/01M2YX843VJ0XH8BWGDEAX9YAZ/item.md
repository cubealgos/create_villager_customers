---
schema_version: 1
id: 01M2YX843VJ0XH8BWGDEAX9YAZ
key: VC-15
type: feat
title: The match rule honours an offer's component predicate so a villager buys the exact configuration
created_by: kevin
created_at: 2026-09-20T07:59:46Z
---

## Scope

Cross-project requirement from `create_firearms` (its spec, DEC-016): a villager offer's cost can carry a component predicate (`ItemCost.components`, a `DataComponentPredicate`), e.g. a Master weaponsmith wanting "an AKM with a suppressor and a 4x". The match rule compares item id and count only (`StackShape`), so such an offer would match any AKM and the villager would pay for the wrong weapon. The match shall honour the offer's cost predicate: the shop's goods stack must satisfy `ItemCost.test(ItemStack)` (id, count and component predicate), and the transaction shall draw only stacks that satisfy it (`TRANSACTION-REQ-001`, `TRANSACTION-REQ-004`; a new `TRANSACTION-REQ-011`).

## Approach

Read `MerchantOffer.getItemCostA()`/`ItemCost.test`/`DataComponentPredicate` in the 26.2 jar. Keep `MatchRule` pure by adding a predicate hook: `matches(offerCost, secondCost, result, goods, price, Predicate<StackShape>?)` is not enough, since components are outside `StackShape`; instead let `TransactionExecutor.matches` check `offer.getItemCostA().test(goodsStack)` on the cloth's real goods stack after the pure shape match, and make the draw request the exact goods stack (with components) so `ContainerExtension.extract` matches components (verify `extract`'s matching: `ItemStack.isSameItemSameComponents`?). Spec: add `TRANSACTION-REQ-011` to the vault spec (cubealgos layer, `vault/projects/create_villager_customers/spec/domains/transaction.md`) and `just spec-sync`.

## Acceptance criteria

- [x] Game test: an offer whose cost carries a component predicate (e.g. a named potion or an enchanted book component; use a vanilla component the test can set) matches a shop whose goods stack satisfies it and refuses one whose stack does not (`NO_MATCH`).
- [x] Game test: the draw takes only matching stacks from the network when both variants are in stock.
- [x] Vault spec carries `TRANSACTION-REQ-011`; `docs/spec/` synced.
- [x] Merged through a Forgejo pull request into `development`.

## Constraints and prior findings

VC-3's Findings (draw path); VC-13's (atomic unit). `create_firearms` DEC-016 names this requirement.
