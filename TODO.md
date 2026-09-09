# The Hungry Loop — TODO

Working roadmap. Check items off as they land. Newest decisions go in [docs/DESIGN.md](docs/DESIGN.md).

Legend: `[x]` done · `[~]` in progress · `[ ]` not started · `[?]` needs a decision

---

## Phase 0 — Project setup

- [x] NeoForge 1.21.1 project scaffold (ModDevGradle `2.0.146`, `neo_version=21.1.250`)
- [x] Gradle wrapper (`8.12.1`), `build.bat` with JDK-21 fallback, `.gitignore`
- [x] Maven repos wired: NeoForged, Shadows-of-Fire, TheIllusiveC4, Architectury, Modrinth mirror
- [x] Curios as the one hard dependency (`implementation`)
- [x] Apotheosis / Placebo / ApothicAttributes / Relics / Artifacts / OctoLib as `compileOnly`
- [x] `neoforge.mods.toml` — curios `required`, apotheosis/relics/artifacts `optional`
- [x] Main `@Mod` class, item + creative-tab registration, placeholder item model + lang
- [x] Test-client mod jars in `run/mods/` (copied from foxs-tweaks + Artifacts pulled fresh)
- [x] `scripts/fetch-testmods.ps1`
- [ ] First green build — `build.bat build`
- [ ] First dev client launch — `build.bat runClient`, confirm the loop appears in its creative tab
- [ ] `git init` + initial commit
- [ ] **Real item texture** — pixel-art it from the reference ring art (currently a generated
      16×16 placeholder). Path: `src/main/resources/assets/hungryloop/textures/item/hungry_loop.png`.
      Also want a distinct "shiny" variant/overlay for loot loops.

## Phase 1 — The stack: data model & adaptive inventory

- [ ] `HungryLoopData` data component on the ItemStack:
  - [ ] `int baseSlots` (4, or 4–8 for shiny)
  - [ ] `int bonusSlots` (earned via XP)
  - [ ] `boolean shiny`
  - [ ] `int storedXp` / progress toward next slot
  - [ ] `List<ItemStack> contents` (size = baseSlots + bonusSlots)
- [ ] Codec + StreamCodec for the component; register a `DataComponentType`
- [ ] `LoopInventory` view wrapping the component — get/set/resize, drop-on-shrink safety
- [ ] Per-sub-slot **slot type resolution**: when a stack is placed, resolve which Curios slot type
      it belongs to (`CuriosApi.getItemStackSlots` / item's declared slots / tag). The sub-slot
      "becomes" that type for validation and for effect forwarding.
- [ ] Sub-slot validation mirrors a real Curios slot (`ICurio.canEquip`-style checks, slot-type
      predicate, size-1)
- [ ] `[?]` One item per type only? Or allow N rings in one loop? (design decision — see DESIGN.md)

## Phase 2 — Curios integration (the loop as a curio)

- [ ] Register the loop as an `ICurio` / `ICurioItem` (Curios capability)
- [ ] Decide the loop's own host slot(s): `curio` generic, or its own registered `hungry_loop` slot
      type added via a slot-type datapack entry
- [ ] `curios()` slot providers so the loop grants the player nothing by itself — the contents do
- [ ] Forward **attribute modifiers** of every contained stack as though equipped
      (`ICurio.getAttributeModifiers` aggregation, unique modifier ids per sub-slot)
- [ ] Forward Curios tick callbacks to contents: `curioTick`, `onEquip` / `onUnequip`,
      `onEquipFromUse`, damage/break handlers
- [ ] Contents equip/unequip when the loop itself is equipped/unequipped or its inventory changes
- [ ] Render nothing on the player body for contained cosmetics (or `[?]` render the first one?)
- [ ] Verrrify: a plain Curios ring with an attribute (e.g. `+HP`) works identically in a loop slot
      vs a native ring slot

## Phase 3 — Apotheosis / affix support

- [ ] Contained items keep their affixes, gem sockets and attributes active while in the loop
- [ ] Slot-gated affixes: an affix that only fires "as a ring" must see the sub-slot as a ring —
      make Apotheosis' slot/`LootCategory` lookups resolve against the sub-slot type, not the loop
- [ ] Apotheosis `SocketHelper` / affix bonus application driven from the loop's forwarding
- [ ] Check XP-drop / loot affixes actually fire (needs `apothic_compats` for curio `LootCategory`,
      see AGENTS.md known-bug note)
- [ ] `client/compat` — show contained-item affixes in the loop tooltip / overlay

## Phase 4 — Relics & Artifacts support

- [ ] Relics: contained relics tick and their abilities work; `IRelicItem` "equipped in slot X"
      checks resolve against the sub-slot
- [ ] Relics: ability must be **unlocked** (research done) before it works from inside the loop —
      don't bypass the unlock gate
- [ ] Relics: level/XP on a contained relic still accrues
- [ ] Artifacts: contained artifacts behave as equipped (Artifacts uses Curios slots already, so
      this should mostly fall out of Phase 2 — verify each artifact type)
- [ ] Guard every Relics/Artifacts reference behind `ModList.isLoaded` in an `integration/` class

## Phase 5 — XP-fed slot growth

- [ ] "Feed XP" action (from the overlay, and/or right-click with XP in hand?)
- [ ] Cost curve: `cost(nextSlot) = f(baseSlots, currentSlots)` — higher base ⇒ higher per-slot cost
      (so a 4-slot loop and an 8-slot shiny converge in total investment). Numbers in DESIGN.md.
- [ ] Hard cap on total slots `[?]` (12? 16?)
- [ ] Consume player XP levels/points, grow `contents`, feedback message + sound/particles
- [ ] Persist progress on the stack; survive stacking rules (loop is `stacksTo(1)` already)

## Phase 6 — UI

- [ ] Custom `AbstractContainerMenu` + `Screen` for the loop's inventory
- [ ] Open **as an overlay** — right-click the item, or a keybind — without closing the current
      screen (push a screen that renders the parent behind it, or a widget layer)
- [ ] Keybind registered under a "The Hungry Loop" category; default `[?]` (`R`? `V`?)
- [ ] Open from: hotbar right-click, inventory, and while equipped as a curio
- [ ] Slot rendering shows each sub-slot's resolved type (ring/amulet/etc. background icon)
- [ ] Rich tooltip on the item: list contents, drag items in/out **through the tooltip**
- [ ] "Feed XP" button + slot-cost readout in the screen
- [ ] Shiny loops visually distinct in the UI

## Phase 7 — Loot & the shiny variant

- [ ] Loot-table injection: the loop appears in dungeon/structure loot
- [ ] Shiny roll on loot-spawned loops: 4–8 base slots, weighted toward 4
- [ ] `[?]` Shiny = just more base slots, or also a stat/quality bonus?
- [ ] Loot tables via datagen

## Phase 8 — Test command

- [ ] `/hungryloop gen [count] [affixes] [abilities]` — spawn random curio items:
  - [ ] pull from all registered curio items (vanilla-slot + Relics + Artifacts + Curios addons)
  - [ ] roll Apotheosis affixes + gems when Apotheosis is present
  - [ ] Relics: unlock the relic's abilities (research complete) so they're usable immediately
  - [ ] doubles as a diagnostic — report each item's resolved slot type + `LootCategory`
        (mirror foxs-tweaks' `testhotbar`)
- [ ] `/hungryloop give [slots] [--shiny]` — a loop with a chosen slot count
- [ ] op-gated (`LEVEL_GAMEMASTERS`), registered from `RegisterCommandsEvent` (not `@EventBusSubscriber`
      if it touches Relics/Artifacts types)

## Phase 9 — Polish & release

- [ ] Config (COMMON): base slot count, cost curve knobs, slot cap, loot weights, feature toggles
- [ ] Balance pass with a real modpack's curio set
- [ ] Advancement / first-craft or first-find trigger `[?]`
- [ ] Recipe `[?]` (craftable, or loot-only?)
- [ ] Datagen: model, lang check, loot, tags
- [ ] `en_us` full pass; other locales later
- [ ] README with screenshots, CurseForge/Modrinth listing text
- [ ] Test matrix: none / Curios-only / +Apotheosis / +Relics / +Artifacts / all — client & server,
      singleplayer & dedicated, and client-only-on-server where it matters

---

## Open design questions (tracked in DESIGN.md)

- [?] Loop hosted in generic `curio` slot vs its own `hungry_loop` slot type
- [?] One-per-type contents, or unlimited duplicates
- [?] Total slot cap and exact cost curve
- [?] Shiny = slots only, or slots + quality
- [?] Craftable vs loot-only
- [?] Nested loops — allowed, or explicitly blocked (probably blocked)
