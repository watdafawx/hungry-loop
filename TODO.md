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
      ([component/](src/main/java/dev/watdafawx/hungryloop/component/))
- [x] `toSlotList()` / `withItems()` view helpers (fixed-size padded list, trailing-empty trim)
- [x] Per-sub-slot **slot type resolution** — `ContentForwarding.resolveSlot()` via
      `CuriosApi.getItemStackSlots`, preferring a specific type over generic `curio`
- [x] Sub-slot validation — menu slots accept any stack with a Curios capability except another loop
- [x] Duplicates allowed (per the design call — N rings in one loop is fine)
- [x] Drop-on-shrink safety: contents past capacity are ejected to the inventory (or dropped if it is full), never deleted — `curio.LoopOverflow`, every 20 ticks, worn + inventory loops
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
- [x] `CurioSlotMixin` — a mirror slot is `isActive() = false` (never rendered, hovered or
      clicked) and read-only. **This is the whole hiding mechanism.** It must stay a pure
      render/interaction flag: the Curios container's slot *list* is index-addressed by vanilla's
      container sync, so it has to be identical on client and server.
- [x] **Never trim the slot list.** An earlier `setPage` `getSlots()` redirect subtracted the mirror
      count — but mirrors lag on the client, so mid-equip the two sides built different-length slot
      lists (server `19→23→46→19`, client `19→20→21→23→…`). Vanilla then wrote the loop's own stack
      into whatever slot sat at that index client-side and it stuck there: the "ghost loop" with a
      full working tooltip. Removed, along with its `setPage` HEAD sync (which also fired ~20×/s).
- [x] `CuriosPanelLayoutMixin` — after `setPage`, rewrites *presentation only* (slot `x`/`y`,
      `grid`, `panelWidth`) so hidden slots leave no gaps and the player's own curios don't shuffle.
      Needs an AT to drop `final` from `Slot#x`/`#y`.
- [x] **Ghost slots on multi-page Curios panels.** The layout mixin used to skip panels with more
      than one page, so in packs with many slot types (TNP Limitless 8) every mirror on the panel
      was an empty, unclickable cell. It now compacts within the page `setPage` built. A page can
      show fewer than `maxSlotsPerPage` slots; re-paging around mirrors would change the menu's slot
      list, which must match the server's. SelfTest `reopen` group checks every page for dead cells
      and grid holes (mutation-tested). Run it with `maxSlotsPerPage = 12` in
      `run/defaultconfigs/curios-server.toml` to force paging in the dev world.
- [x] `CuriosRebuildGate` covers paged panels: it compares the on-screen slots with the exact
      (type, index) window `setPage` would build for the current page, plus the page count. Before,
      a paged panel rebuilt ~5×/100 idle ticks with Relics worn, loop or not (SelfTest `idle` at
      12/page failed; passes now).
- [~] **Accessories + its Curios compat layer** (`integration/accessories`, own mixin config gated by
      `AccessoriesMixinPlugin`). The compat layer backs Curios with Accessories containers, drops
      every Curios client packet and swaps in Accessories' screens, so none of the Curios-side
      hiding or sync applies:
      - mirror rows hidden + read-only in both Accessories screens (`AccessoriesInternalSlotMixin`),
        original screen's column compacted (`AccessoriesMenuLayoutMixin`), experimental grid drops
        them (`AccessoriesExperimentalMenuMixin`);
      - layout changes pushed with Accessories' full `SyncEntireContainer` + server-side menu reopen,
        instead of the dropped `SPacketSyncCurios` (client sizes lagged the server otherwise).
      - SelfTest runs the `acc-*` scenarios instead of the Curios ones when Accessories is loaded.
      - Loop slot count: the compat layer ignores `size` on Curios slot types new to Accessories
        (`CuriosCompat` only applies it when a builder already existed), so our size-0 `loop` type
        became 1 and players got config+1 loop slots. `LoopSlotProvider` now adds
        `config - base size` instead of a flat `config`. SelfTest checks the count on both paths.
- [x] **Push the mirror layout as one `SPacketSyncCurios`, never as per-slot deltas.** Curios'
      `SPacketSyncStack` addresses a slot by raw index while `SPacketSyncModifiers` resizes the list
      those indices address; the loop resizes constantly, so the two raced. That race caused the
      "Slot N not in valid range" disconnect, the ghost item on loop moves, and the stale screen
      after equip. It used to be patched client-side three ways: `PendingCurioStackSync` (defer and
      replay early stack syncs — replaying into a since-changed layout *was* the move ghost),
      `CuriosClientPacketsMixin` (per-packet screen rebuild, first gated by a memo that went stale on
      resize), and a rebuild gate on `getVisibleSlots()`. All three are **deleted**: `reconcile`
      sends the full inventory (sizes + stacks + client rebuild, atomically) once per real layout
      change. `client.SelfTest` fails without it (mutation-tested).
- [x] **Skip Curios' redundant screen rebuilds soundly (`curio.CuriosRebuildGate`).** Relics refreshes
      its items' data every few ticks — worn directly *or* through a loop (SelfTest's idle-churn
      control proved it isn't loop-specific) — and each change makes Curios send
      `SPacketSyncModifiers`, whose client handler always `resetSlots()`: every `Slot` object is
      replaced and immersive-ui's hover animation (keyed on slot identity) restarts. An earlier gate
      on the visible-slot *total* was unsound (can't see one type grow while another shrinks → ghosts)
      and was removed. The current gate only acts inside that handler, and only when the rebuild
      would reproduce the slot list exactly (per-slot backing handler + index, per-type counts,
      parking, single page, not cosmetics). `applySyncTag` replaces render/active lists, so a skip
      rebinds each slot to the new lists (`CurioSlotMixin` via `CurioSlotRebind`). Mutation-tested:
      no gate → idle rebuilds; no rebind → slots read stale render lists.
- [x] **`client.SelfTest`** — `build.bat runSelftest` recreates a flat scratch world, provisions
      loops per scenario (equip/unequip under an open screen, same-tick + carried moves, nested loop,
      two loops, overflow), diffs client vs server slot by slot after each step (singleplayer shares
      the JVM, so a ghost is detected directly), screenshots every step, writes
      `run/hungryloop-selftest.txt`, quits. `/hlselftest` runs the non-destructive moves in any world.
      Dev-only. Before claiming a sync fix or deleting sync code, run it — and mutation-test new
      scenarios (break the fix, confirm FAIL).
- [x] Nested loop was granted a slot the mirror pass never fills (stray visible slot) —
      `HungryLoopCurio` now skips loops exactly like `LoopSlotMirror`.
- [x] Loop render toggle lived on the Curios *slot* (per-index flag), so moving the loop reset it —
      the component's `selfRenders` is now pushed into whatever slot the loop occupies; the loop slot
      type has `render_toggle: false`. Moves defer the all-mirror purge a tick (leave fires before
      enter, so mid-move looked like an unequip).
- [x] Contents beyond capacity (lowered cap, loot/commands) were hidden in the loop screen yet still
      equipped, and the next edit deleted them — the screen now shows them take-only.
- [x] **Capacity policy: eject.** Decided 2026-11-06. Same path as the shrink guard; SelfTest `overflow` + GameTest `overflowIsEjectedNotLost` (mutation-tested).
- [x] **Affixed items mirror into the slot Apotheosis reads.** ApothicAttributes' `CurioEquipmentSlot`
      reads a category's affix stacks from exactly one Curios handler, and apothic_compats assigns
      categories by curio tag — so wildcards tagged `curios:curio` (valid in every type) only fire
      their affixes from `curio`, which `resolveSlot` used to skip in favour of `back`.
      `integration/apotheosis/ApothSlots` now wins when Apotheosis is loaded; `loop` is never a
      candidate. Verified by SelfTest's parity scenarios (item worn in its best slot vs in the loop,
      every attribute compared) — they fail with the old rule.
- [x] **Loop slots come first in the Curios panel** (`loop.json` order 10, below `curio`'s 20), and
      only loops fit them. The slot's `hungryloop:loop_only` validator keeps `getItemStackSlots`
      honest, but Curios' `isStackValid` hardcodes "valid in `curio` ⇒ valid in every type", so
      wildcards could still be clicked or right-click-equipped in — and ordered first, right-click
      picks the loop slot before any real one. `LoopSlotProvider` denies non-loops for the loop type
      in `CurioCanEquipEvent`, which every equip path consults. SelfTest `order` group covers all three.
- [x] Right-click equip swapping with a mirror: not reachable — Curios swaps with the first
      extractable slot and base slots always precede granted ones. SelfTest `rightclick-swap` forces
      the swap path (every slot full) as a guard.
- [x] Verify Relics / Artifacts see loop items through their own equipped lookups
      (`EntityUtils.findEquippedCurio`, `EquipmentHelper.iterateEquipment` - what their abilities run on),
      SelfTest `abilities`, both Curios and Accessories paths. Apotheosis affixes: parity scenarios.
- [x] **Mirror write-back.** Mirrors are what other mods tick and level; their data was never copied
      back, so relic XP/levels/research (and durability, charges) made while worn were lost on unequip.
      `LoopSlotMirror.writeBack` copies changed mirrors into the stored items every 40 ticks and before the
      loop menu opens. SelfTest `writeback` + GameTest `relicProgressWrittenBackOnUnequip` (mutation-tested).
- [x] Contained cosmetics render on the body (the mirrors are really equipped). Per-item + master
      render toggle in the loop screen (`LoopContents.renderMask` / `selfRenders` → the granted
      slot's Curios render flag, synced with `SPacketSyncRender`). Hiding the loop's own Curios
      slot cascades to all contents.
- [ ] Multiple loops, loop-in-loop guard under the new model
- [x] Right-click opens the menu (hand + in-GUI, deferred); **keybind** (`H`, rebindable) opens it
      from anywhere incl. creative inventory (`net.OpenLoopPayload`)
- [x] ~~Passthrough select~~ — shift-scroll cycled a selected contained item so the loop acted as
      it; removed (never worked reliably, and its tooltip clipped off-screen). No replacement
      trigger for a relic's own ability from inside the loop yet
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
- [x] Open **as an overlay** (`client.LoopOverlay`): from the inventory, creative, Curios or Accessories
      screen the parent stays drawn (dimmed) behind the loop, and closing returns to it. Curios/Accessories
      are server menus the server closes first, so a just-closed parent is adopted within 20 ticks.
      SelfTest `overlay`.
- [ ] Keybind under a "The Hungry Loop" category + a C→S open packet (open without a GUI already up)
- [x] Each contained item's tooltip in the loop screen says "Acts as: <slot>" (empty slots have no type; a filled one is covered by its item)
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

## Phase 7 — Loot & the shiny variant

- [x] Shiny loot variant via a Global Loot Modifier (`ShinyLoopModifier`) — 6% chance in ~15
      structure chest tables; target tables + chance are codec fields checked against
      `LootContext.getQueriedLootTableId()`. File at `data/hungryloop/loot_modifiers/` (plural).
- [x] Shiny loot loops roll 6–8 base slots ("double slots unlocked"), `shiny=true`, loot-only
- [x] Shiny = slots only for v1 (no quality bonus)
- [ ] Weight the base-slot roll toward 6; tune the chance and table list per pack
- [x] Datagen (`datagen.HungryLoopDataGen`, `build.bat runData`, own `run-data/` with only Curios): recipe, loot modifier, `curios:loop` tag, item model. Lang + Curios slot/entity JSON stay hand-written. CI fails if `src/generated` drifts.
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
- [x] **No item-render animation for the loop icon.** An `ItemRenderer#render` mixin used to bob and
      breathe the loop in GUI slots. Don't bring it back. It mutated the caller's `PoseStack` without
      a `pushPose`/`popPose`, so the leaked transform corrupted every item drawn after it in the same
      screen — not just the loop. Found via ImmersiveUI (OctoStudios), whose
      `AbstractContainerScreenMixin.renderSize` wraps `GuiGraphics.renderItem` inside
      `AbstractContainerScreen#renderSlot` with its own hover scale; our un-popped matrix poisoned
      its pose and the whole Curios screen rendered wrong. Animate our own screen only, and leave
      inventory/curio slot item rendering alone.
      (Our `isActive()`-based slot hiding *is* compatible with it: vanilla only calls `renderSlot`
      for active slots, and ImmersiveUI injects inside that gate.)
- [ ] Advancement / first-craft or first-find trigger `[?]`
- [x] Recipe — normal (4-slot) loop is craftable: copper ingots (corners) + ender pearls (edges) +
      amethyst shard (centre). Shiny is loot-only.
- [ ] Datagen: model, lang check, loot, tags
- [ ] `en_us` full pass; other locales later
- [ ] README with screenshots, CurseForge/Modrinth listing text
- [ ] Test matrix: none / Curios-only / +Apotheosis / +Relics / +Artifacts / all — client & server,
      singleplayer & dedicated, and client-only-on-server where it matters

---

## Ideas for later

- [x] Animated loop screen frame (`client.LoopFrame`, PoE-unique inspired) + ring layout around the
      wearer; fits every GUI scale (SelfTest `scales`, `look`). 0.8.0.
- [x] Animated item sprite: 32×32, 16 interpolated frames (`.png.mcmeta`) - a glint runs round the
      band, the pearl pulses. Shiny loops swap to an aqua strip via the `hungryloop:shiny` item property
      (model override, datagen). Frames generated from `art/drafts/hungry_loop_32.png`.
- [x] The worn loop on the main hand: third person via a Curios renderer (`LoopCurioRenderer`), first
      person on the empty-hand arm via `RenderArmEvent` (`LoopFirstPerson`, smaller). Both respect the
      master eye. SelfTest `body` screenshots. Still the flat sprite, not a sculpted 3D model.

## Open design questions (tracked in DESIGN.md)

- [?] Loop hosted in generic `curio` slot vs its own `hungry_loop` slot type
- [?] One-per-type contents, or unlimited duplicates
- [?] Total slot cap and exact cost curve
- [?] Shiny = slots only, or slots + quality
- [?] Craftable vs loot-only
- [?] Nested loops — allowed, or explicitly blocked (probably blocked)
