# The Hungry Loop — design

## The pitch

One curio that holds other curios. In a big modpack you never have enough curio slots; adding more
by command or config is fiddly and unbalanced. The Hungry Loop is a single item you equip once, and
then everything you put *inside* it works as though it were equipped in its own proper slot — a ring
acts like it's in a ring slot, a Relics relic like it's in its relic slot, an amulet like an amulet.

Name is after the ring from Path of Exile ("The Hungry Loop" — it eats what you socket into it).

## Core rules

1. **The loop grants nothing itself.** All effects come from its contents.
2. **Each sub-slot adapts.** Place a ring and that sub-slot *is* a ring slot: same validation, same
   slot-type identity for anything that checks "is this equipped as a ring?".
3. **Effects forward completely** — attribute modifiers, Apotheosis affixes + gem sockets, Relics
   abilities (only if already unlocked), Artifacts effects, Curios tick/equip/unequip callbacks.
4. **Storage grows.** 4 sub-slots by default. Feed the loop XP to add more; each added slot costs
   more than the last. Loot-found "shiny" loops start with 4–8.
5. **Starting slots affect cost.** A loop that starts with more slots pays more per *added* slot, so
   total investment to reach a given size is roughly flat regardless of where you started.
6. **Only Curios is required.** Apotheosis, Relics, Artifacts are all optional and independent.

## How "adaptive slot" works (plan)

Curios already keys everything off **slot type strings** (`"ring"`, `"necklace"`, `"charm"`, …). An
item declares which slot types it fits (`ISlotType` predicates / `CuriosApi.getItemStackSlots`, item
tags like `curios:ring`). So:

- When a stack enters a loop sub-slot, resolve its slot type(s) once and store the chosen type on
  the sub-slot.
- For validation, the sub-slot reuses the same predicate a native slot of that type would.
- For effect forwarding, anything that asks Curios "what slot is this in?" needs to see the sub-slot
  type, not `hungry_loop`. This is the crux — Apotheosis slot-gated affixes, Relics slot checks and
  cosmetic rendering all go through this. Likely approaches: a `SlotContext` that reports the
  sub-slot type, and/or wrapping the loop's `ICurio` forwarding so each contained item is ticked
  with a synthesized `SlotContext(subSlotType, index, wearer)`.

## Cost curve (starting point, tune later)

```
totalSlots     = baseSlots + bonusSlots
costForNext    = BASE_COST * growth^(totalSlots - 4) + BASE_PENALTY * (baseSlots - 4)
```

- `BASE_COST` ≈ 5 XP levels, `growth` ≈ 1.6, `BASE_PENALTY` ≈ 3 levels.
- 4-slot loop → 5th slot costs ~5 levels; 8th costs ~20.
- 8-slot shiny → 9th slot costs ~20 + 12 penalty ≈ 32 levels.
- Numbers exposed in config.

## Open questions

| # | Question | Leaning |
|---|----------|---------|
| 1 | Loop equips in generic `curio` slot or its own `hungry_loop` slot type? | Own slot type, added by datapack, so it never competes with normal curios |
| 2 | One item per slot type inside a loop, or unlimited duplicates? | Match native Curios — if you have 2 ring slots you can wear 2 rings; a loop with 4 slots could hold 4 rings. Simpler and no special-casing. |
| 3 | Total slot cap? | 12 |
| 4 | Shiny = more slots only, or slots + a quality/affix bonus? | Slots only for v1 |
| 5 | Craftable? | Loot-only for v1; revisit |
| 6 | Nested loops (a loop inside a loop)? | Blocked |
| 7 | Do contained cosmetics render on the player? | No for v1 |

## Non-goals

- Not a bag / not general item storage — curios only.
- Not a way to bypass Relics research or Apotheosis rarity gates.
- Not trying to support every curio mod's edge cases in v1 — Curios core, Apotheosis, Relics,
  Artifacts first.
