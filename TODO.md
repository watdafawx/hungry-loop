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
- [x] `apothic_compats` in `run/mods/` — datapack-only mod, no build.gradle entry needed (we never
      call its API); it is what teaches Apotheosis to affix/socket curio items. See Phase 3.
- [x] `scripts/fetch-testmods.ps1`
- [x] First green build — `build.bat build` (`BUILD SUCCESSFUL`, 2026-09-09)
- [x] First dev client launch — `build.bat runClient`, all 13 mods load (0 requirements missing),
      loop shows up in-world (2026-09-09)
- [x] `git init` + initial commit
- [ ] **Real item texture** — pixel-art it from the reference ring art (currently a generated
      16×16 placeholder). Path: `src/main/resources/assets/hungryloop/textures/item/hungry_loop.png`.
      Also want a distinct "shiny" variant/overlay for loot loops.

## Phase 1 — The stack: data model & adaptive inventory

- [x] `LoopContents` data component on the ItemStack — `baseSlots`, `bonusSlots`, `shiny`,
      `storedXp`, `List<ItemStack> items`; Codec + StreamCodec; registered `DataComponentType`
      ([component/](src/main/java/dev/mtop/hungryloop/component/))
- [x] `toSlotList()` / `withItems()` view helpers (fixed-size padded list, trailing-empty trim)
- [x] Per-sub-slot **slot type resolution** — `ContentForwarding.resolveSlot()` via
      `CuriosApi.getItemStackSlots`, preferring a specific type over generic `curio`
- [x] Sub-slot validation — menu slots accept any stack with a Curios capability except another loop
- [x] Duplicates allowed (per the design call — N rings in one loop is fine)
- [ ] Drop-on-shrink safety (if a slot count ever decreases — not possible yet, XP only grows it)
- [ ] Resolve-once and cache the slot type on the sub-slot? (currently recomputed each tick — fine
      for now, revisit if it shows up in a profile)

## Phase 2 — Curios integration (real hidden slots)

Approach (decided 2026-09-09): forwarding `ICurio.curioTick` is **not enough** — Relics and Artifacts
scan the player's real Curios inventory to find equipped items and drive their abilities centrally.
So the loop **grants real Curios slots** and mirrors its contents into them.

- [x] Register the loop as an `ICurioItem`. **Dedicated slot** `hungryloop:loop` (datapack
      `curios/slots/loop.json` size 0 + `curios/entities/player.json`), count driven by config
      `loopSlots` (default 2, live-reload) via a transient slot modifier from `LoopSlotProvider`.
      `canEquip` restricts the loop to that slot only. (Was "any slot via #curios:curio" — Curios'
      per-tick slot recompute + `loseStacks` on shrink made a loop in a *resizing* type churn
      endlessly; a fixed-size dedicated slot sidesteps that entirely.)
- [x] `HungryLoopCurio.getAttributeModifiers` returns **only** `SlotAttribute` grants — one extra
      slot per contained item, of that item's resolved type. Curios grows/shrinks them with the
      loop's equip state.
- [x] **`LoopContents` value `equals`/`hashCode`** — the record's generated `equals` compared the
      `List<ItemStack>` by reference (ItemStack has no value equals), so Curios' per-tick
      `ItemStack.matches(loop, prevCopy)` was always false and it re-equipped the loop every tick.
      Root cause of the flicker / phantom / ejected-curios reports.
- [x] `LoopSlotMirror` — server tick (every 3t) + on menu close + on Curios-screen rebuild: fills
      the **trailing** granted slots with marked mirror copies (packed at the high indices, matching
      slots reused so unrelated items don't blip); clears stray mirrors on unequip / slot-modifier
      update / login
- [x] `ContentForwarding.resolveSlot` deterministic (sorted candidates) so `getAttributeModifiers`
      and `LoopSlotMirror` always pick the same slot type for a multi-slot item
- [x] `CurioSlotMixin` — mirror slots are read-only in the Curios screen (no dupe by yanking a copy)
- [x] `CuriosContainerMixin` — hides the granted mirror slots from the Curios screen by trimming
      `setPage`'s per-type `getSlots()` (and `getVisibleSlots()` for panel sizing) by the trailing
      mirror count; a HEAD inject reconciles mirrors first so they're hidden on first render
      (fresh worlds clean; a pre-fix save needs the loop re-equipped once to drop phantom slots)
- [ ] Live-hide when the loop is equipped *while the Curios screen is already open* (rare) — new
      slots show until reopen; matches how Curios handles other slot-granting items
- [ ] Verify each: Apotheosis affix abilities, Relics abilities, Artifacts abilities all fire from
      a loop-held item
- [x] Contained cosmetics render on the body (the mirrors are really equipped). Per-item + master
      render toggle in the loop screen (`LoopContents.renderMask` / `selfRenders` → the granted
      slot's Curios render flag, synced with `SPacketSyncRender`). Hiding the loop's own Curios
      slot cascades to all contents.
- [ ] Multiple loops, loop-in-loop guard under the new model
- [x] Right-click opens the menu (hand + in-GUI, deferred); **keybind** (`H`, rebindable) opens it
      from anywhere incl. creative inventory (`net.OpenLoopPayload`)
- [x] **Passthrough select** — shift-scroll on a held loop cycles a selected contained item; the
      loop's name/tooltip show it and right-click forwards to that item's `use`/`useOn`
      (`net.CycleLoopPayload`, `LoopContents.selected`). Caveat: items that read the held stack
      mid-use won't behave perfectly
- [x] **Relic "+" badge** — one green `+` on the loop when any contained relic has an ability to
      research or level up (`RegisterItemDecorationsEvent` → `integration/relics/RelicsBadge`)

## Phase 3 — Apotheosis / affix support

- [ ] Contained items keep their affixes, gem sockets and attributes active while in the loop
- [ ] Slot-gated affixes: an affix that only fires "as a ring" must see the sub-slot as a ring —
      make Apotheosis' slot/`LootCategory` lookups resolve against the sub-slot type, not the loop
- [ ] Apotheosis `SocketHelper` / affix bonus application driven from the loop's forwarding
- [ ] Check XP-drop / loot affixes actually fire. Requires `apothic_compats` in the pack: it ships
      the datapack that gives curio items a `LootCategory`, without which Apotheosis refuses to
      affix or socket any of them. It's a runtime-only datapack mod — nothing to compile against.
      Known load-order parse bug on ~11 of its entries (harmless, see AGENTS.md).
- [ ] `client/compat` — show contained-item affixes in the loop tooltip / overlay

## Phase 4 — Relics & Artifacts support

- [ ] Relics: contained relics tick and their abilities work; `IRelicItem` "equipped in slot X"
      checks resolve against the sub-slot
- [ ] Relics: ability must be **unlocked** (research done) before it works from inside the loop —
      don't bypass the unlock gate
- [ ] Relics: level/XP on a contained relic still accrues
- [ ] Artifacts: contained artifacts behave as equipped (Artifacts uses Curios slots already, so
      this should mostly fall out of Phase 2 — verify each artifact type)
- [x] `integration/` package established: `apotheosis/ApothLoot`, `relics/RelicsUnlock` — all
      Apotheosis/Relics symbols confined here, reached only from `ModList`-guarded call sites, no
      `@EventBusSubscriber`
- [ ] Guard the rest of the Relics/Artifacts references (ability ticking, slot checks) the same way

## Phase 5 — XP-fed slot growth

- [x] "Feed XP" button in the screen (click = 1 level, shift-click = enough for the next slot)
- [x] Cost curve `LoopContents.slotCost(base, bonus)` = `round(5 · 1.7^(have−4)) + 3·(base−4)` —
      steep per-slot growth + a flat surcharge per starting slot, so a 4-slot and an 8-slot shiny
      converge on similar total investment
- [x] Cap: 12 by default (`LoopContents.slotCap`), raised by feeding Nether Stars from the cursor
      at the Feed button — `Config.SLOTS_PER_STAR` (2) each, up to `Config.ABSOLUTE_MAX_SLOTS` (20)
- [x] Shift-click Feed feeds every XP level you have (was: only enough for one slot)
- [x] Config (SERVER, live-reload + synced): `normalMaxSlots` (12), `shinyMaxSlots` (20),
      `slotsPerStar` (2), `absoluteMaxSlots` (24). Cap is derived live so a config change hits
      existing loops (`LoopContents.capBonus` stores only the star bonus).
- [x] Click a locked slot to unlock it; hover shows the cumulative XP-level cost
- [x] Shift-scroll cycles the passthrough item on a held **or equipped** loop; hotbar overlay
      message shows the new selection
- [x] Consume player XP levels, bank partial progress in `storedXp`, grow `bonusSlots`, level-up
      sound, reopen menu to show new slots
- [x] Progress persists on the stack (component); loop is `stacksTo(1)`
- [ ] Particles on slot-add; balance the curve against a real modpack

## Phase 6 — UI

- [x] Custom `AbstractContainerMenu` + `Screen` (`HungryLoopMenu` / `HungryLoopScreen`) —
      procedural: obsidian panel + copper bevel, iridescent divider echoing the ring band, real +
      dimmed "future" slot wells up to the cap, XP-feed bar + button, shiny = aqua bevel
- [x] **Right-click the loop where it sits in any inventory GUI** (`overrideOtherStackedOnMe`,
      cursor empty + secondary) — player inventory, hotbar, or equipped in a curio slot
- [x] Also opens from the loop in hand (`use`)
- [x] `LoopLocator` (hand / inventory index / curio id+index) — survives the menu swap and the
      network round trip; the menu writes back to the real stack wherever it is
- [x] Menu accepts any curio, rejects another loop (no nesting), 1 per slot, writes back to the
      component on every change and on close
- [ ] Right-click on the loop inside a **non-player** container (a chest) currently just swallows
      the click — decide whether to support that or fall through
- [ ] Open **as an overlay** — without closing the current screen (push a screen that renders the
      parent behind it, or a widget layer)
- [ ] Keybind under a "The Hungry Loop" category + a C→S open packet (open without a GUI already up)
- [ ] Slot rendering shows each sub-slot's resolved type (ring/amulet/etc. background icon)
- [x] Rich tooltip: custom `ClientTooltipComponent` — a card per contained item (its **icon** +
      its own tooltip: affixes, attributes, gem sockets), 3 cards/page; scroll over the loop in a
      GUI to page. Apotheosis `SOCKET_MARKER` filtered → real per-socket lines (`ApothTooltip`).
- [x] Menu is MAX-sized (54) with inactive locked slots → growing never rebuilds/reopens the menu
      (fixed cursor-warp-to-centre)
- [x] `HungryLoopScreen.render()` calls `renderTooltip()` — `AbstractContainerScreen`'s NeoForge
      rewrite doesn't, so contained-item tooltips were missing in the loop GUI
- [ ] Drag items in/out **through the tooltip** (still just the menu)
- [ ] Gem icons (not just text) in the socket lines
- [ ] Pannable/infinite canvas for the snake when the cap is pushed past ~24 via config
- [x] "Feed XP" button + `stored / cost XP levels` readout in the screen
- [x] Shiny loops visually distinct (aqua bevel + badge colour)
- [x] GUI: sub-slots trace a **snake constellation** (serpentine + undulation); faint Bresenham
      lines join every point on the path; bigger panel (218×246); no drop-shadow
- [ ] GUI still-wanted: true infinite/pannable canvas for the slot area (for big caps); loop icon
      in the header; a "maw" motif for the feed area; real art for slot wells + panel
- [x] Passthrough-selected item shown in the loop's name / tooltip

## Phase 7 — Loot & the shiny variant

- [x] Shiny loot variant via a Global Loot Modifier (`ShinyLoopModifier`) — 6% chance in ~15
      structure chest tables; target tables + chance are codec fields checked against
      `LootContext.getQueriedLootTableId()`. File at `data/hungryloop/loot_modifiers/` (plural).
- [x] Shiny loot loops roll 6–8 base slots ("double slots unlocked"), `shiny=true`, loot-only
- [x] Shiny = slots only for v1 (no quality bonus)
- [ ] Weight the base-slot roll toward 6; tune the chance and table list per pack
- [ ] Datagen the loot-modifier + recipe instead of hand-written JSON
- [ ] Loot tables via datagen

## Phase 8 — Test command

- [x] `/hungryloop gen [count] [sockets]` — fill the inventory with random curio items:
  - [x] pull from every registered item that fits a Curios slot (covers apothic_compats' curios,
        Relics, Artifacts, and every Curios addon)
  - [x] roll Apotheosis affixes + gems when Apotheosis is present (`ApothLoot`, lifted from
        foxs-tweaks' `ApothicTestGear`)
  - [x] Relics: `RelicsUnlock` completes research + sets ability level ≥1 (may need to run after
        Relics inits the stack — see `/hungryloop unlock`)
  - [x] diagnostic line per item: `name — sub-slot type — loot category — affix/gem note`
  - [ ] `[?]` option to drop the generated items straight into a loop instead of the inventory
- [x] `/hungryloop unlock` — sweep inventory + curio slots + loop contents, unlock every relic
      (reliable post-hoc version, since gen-time unlock can be too early)
- [x] `/hungryloop give [slots] [shiny]` — a loop with a chosen slot count / shiny flag
- [x] op-gated (`LEVEL_GAMEMASTERS`); `HungryLoopCommands` is **hand-registered** from the mod
      ctor (not `@EventBusSubscriber`) because `gen` reaches into Apotheosis/Relics behind
      `ModList` guards — `integration/apotheosis/ApothLoot`, `integration/relics/RelicsUnlock`

## Phase 9 — Polish & release

- [ ] Config (COMMON): base slot count, cost curve knobs, slot cap, loot weights, feature toggles
- [ ] Balance pass with a real modpack's curio set
- [ ] Advancement / first-craft or first-find trigger `[?]`
- [x] Recipe — normal (4-slot) loop is craftable: copper ingots (corners) + ender pearls (edges) +
      amethyst shard (centre). Shiny is loot-only.
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
