---
title: "create_villager_customers DEC-010 — Shops are found across the whole village, not only near the villager"
type: "spec"
category: "create_villager_customers"
---

# `DEC-010` — Shops are found across the whole village, not only near the villager

**Status:** decided by Kevin, 2026-09-20, in the long-distance retest: "the villager only detects
shops very close by; I would want them to detect a shop within village borders."

## The rule

A village has no border of its own in vanilla; what it has is a meeting point (the bell a
villager remembers as `MEETING_POINT`) and the point-of-interest cluster around it. The search
therefore runs from the village, not from the villager:

- The search origin is the villager's remembered meeting point when it has one, else its own
  position.
- The search radius is **128 blocks horizontally** from that origin (four times vanilla's 48-block
  job-site scan), which covers a full vanilla village and the fields around it; vertically the
  point-of-interest index's own section range applies.
- The trip's existing limits stay: the walk gives up after 2,400 ticks (`CUSTOMER-REQ-004`), so a
  shop that is inside the radius but unreachable in that time is abandoned as before, and nearest
  wins among matches (`SHOP-DEC-001`).

Cost: one larger point-of-interest range query per successful restock roll, once per villager
per restock; the index query is chunk-section based and cheap at this frequency. Configurable
through the mod's config as `shop_search_radius` with the default 128.

## Why not a real border

Computing a village polygon from POI density would be a second concept next to vanilla's bell,
harder to explain to a player than "the whole village", and wrong for scattered villages. The
radius from the bell is what a player will predict.
