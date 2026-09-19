---
title: "create_villager_customers DEC-001 — Distributed product, full spec sheet"
type: "spec"
category: "create_villager_customers"
---

# `DEC-001` — Distributed product, full spec sheet

**Status:** decided by Kevin, 2026-09-19.

This is the third small Create Fly add-on built on the way to `create_civilization`, started in
parallel with `create_metered_motor` (`rulings-2026-09-19.md`). Like the compass and the motor
before it, it ships to real users on Modrinth, not only into the civilization mod, and follows the
same process: the full sheet, so the release path, the compliance table and the testing layers are
decided once, up front. The pipeline is the same as the motor's: Sonnet subagents draft, Claude
reviews, Kevin reviews the findings.

Alternative considered: treating it as internal tooling and skipping §5–§7 (interface contracts,
compliance, release engineering), since `create_civilization` is the real goal. Rejected for the
same reason as its siblings: a mod that changes villager behaviour on a public server invites bug
reports about villagers "doing something weird," whether or not the reporter ever touches the
civilization mod. Cost if wrong: an evening of spec for a mod with no block and no item of its own.
