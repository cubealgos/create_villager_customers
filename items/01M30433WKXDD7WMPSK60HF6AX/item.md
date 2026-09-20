---
schema_version: 1
id: 01M30433WKXDD7WMPSK60HF6AX
key: VC-17
type: bug
title: A ticker holding a mod-inserted payment shows no goggle tooltip although right-click withdraws it
created_by: kevin
created_at: 2026-09-20T19:18:37Z
---

## Scope

<fill this in before committing>

## Approach

<fill this in before committing>

## Acceptance criteria

- [ ] <fill this in before committing>

## Constraints and prior findings

<fill this in before committing>

## Scope

Kevin, 2026-09-20, long-distance retest on development 826f5d4: "right clicking retrieved the items from the ticker, but looking at the ticker I couldn't see a goggle tooltip." The payment is in `receivedPayments` server-side (withdraw proves it), but the client shows no Create tooltip for it, while a payment that a player's own purchase leaves behind does show one. `TransactionExecutor` calls `ticker.setChanged()` and `ticker.sendData()` after the insert, so either the client packet does not carry `receivedPayments` (Create Fly's `StockTickerBlockEntity.write(ValueOutput, clientPacket)` may only write it in the non-client branch, and the tooltip may read a client-only snapshot such as `newlyReceivedStockSnapshot` that Create fills through its own purchase packet), or the tooltip requires a state our path never sets. Find the exact mechanism in the jar (`javap -p -c`), then make the mod-driven payment visible the same way Create's own purchase makes it visible; a game test asserting the client-bound data (or the snapshot list) contains the payment after a unit.

## Acceptance criteria

- [ ] The mechanism Create uses to show received payments on the ticker is identified with evidence and recorded in Findings.
- [ ] After a mod-driven unit, the ticker shows the payment tooltip as a player purchase would; covered by a game test where the data is testable.
- [ ] `just check` green; merged through a Forgejo pull request into `development`; Kevin's client check.
