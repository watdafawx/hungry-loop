# AGENTS.md

Guidance for AI agents working in this repository.

## What this is

**The Hungry Loop** — a NeoForge **1.21.1** mod. A single curio item (`hungryloop:hungry_loop`) that
holds other curios and grants their effects as if each were equipped in its own native slot. Storage
grows by feeding it XP. See [docs/DESIGN.md](docs/DESIGN.md) for the concept and [TODO.md](TODO.md)
for the roadmap and what's done.

**Curios is the only hard dependency.** Apotheosis, Relics and Artifacts are optional, independent
integrations. The mod must load and behave correctly with any subset installed — this is the single
most important invariant. See [Optional dependencies](#optional-dependencies).

## Building

```bat
build.bat build
```

`build.bat` points `JAVA_HOME` at a portable JDK in `toolchain\` if present, otherwise at an
Eclipse Adoptium JDK 21 under `C:\Program Files`. Only call `gradlew` directly if you know a JDK 21
is on `PATH`.

Two environment facts that will otherwise waste your time:

- **NeoForm recompiles Minecraft and needs a full JDK** (`javax.tools`). A JRE gives a confusing
  `JavaCompiler is null` NPE. This machine has Adoptium JDK 21 installed, so `build.bat` finds it;
  `toolchain\` is the fallback if that ever changes.
- **The Maven hosts publish AAAA records but IPv6 does not route here.** `gradle.properties` sets
  `systemProp.java.net.preferIPv4Stack=true`; without it dependency resolution stalls until timeout.

## Local setup

**Compiling needs nothing manual.** Every dependency resolves from a real Maven repo:
Shadows-of-Fire's maven (Apotheosis, Placebo, ApothicAttributes), TheIllusiveC4's maven (Curios),
Architectury's maven, and Modrinth's mirror (`https://api.modrinth.com/maven`, group
`maven.modrinth`, artifact = slug, version = Modrinth **version id**) for Relics, Artifacts and
OctoLib. NeoForge 1.21.1 mod jars ship in Mojang mappings, so a production jar works as a compile
dependency with no remapping.

**The dev client still needs the real jars in `run/mods/`.** FML's mod discovery only scans
`run/mods/` and jar-in-jar — never the general runtime classpath. ModDevGradle's
`additionalRuntimeClasspath` does *not* make a mod load (it was tried in the sibling `foxs-tweaks`
project and doesn't work — the mod never appears and its dependents fail with "Missing or
unsupported mandatory dependencies"). The jars are committed-adjacent but gitignored; `run/mods/`
currently holds, copied from `foxs-tweaks` plus a fresh Artifacts pull:

```
curios-neoforge-9.5.1+1.21.1.jar
Apotheosis-1.21.1-8.7.0.jar  Placebo-1.21.1-9.9.2.jar  ApothicAttributes-1.21.1-2.10.1.jar
apothic_compats-0.2.4.2.jar          (curio LootCategory support - see known bug below)
relics-1.21.1-0.12.8.jar  OctoLib-NEOFORGE-0.6.2+1.21.jar  architectury-13.0.11-neoforge.jar
artifacts-neoforge-13.2.5.jar
```

`scripts/fetch-testmods.ps1` re-fetches the Modrinth ones. Versions must match `gradle.properties`.

**Accessories testing.** Real packs (TNP Limitless 8) may run Accessories + its Curios compat layer,
which replaces Curios' inventory, screen and sync. Its three jars
(`accessories-neoforge-*`, `accessories_compat_layer-neoforge-*`, `owo-lib-neoforge-*`) are
installed in `run/mods/`, so the dev client runs the Accessories path (SelfTest runs its `acc-*` and
parity scenarios). Move them to `run/mods-off/` to test the plain Curios path (the full Curios
suite). Test both before claiming a UI or sync fix.

**Multi-page Curios panel.** The dev world has few slot types, so the Curios panel is one page.
Packs with many types paginate, and the ghost-slot bug only showed there. Set
`maxSlotsPerPage = 12` in `run/defaultconfigs/curios-server.toml` and `run/config/curios-server.toml`
to force paging.

**`apothic_compats` is deliberately not in `build.gradle`.** It's a datapack-only mod — it ships
Apotheosis datapacks for ~30 mods (curio items among them) and exposes no API we call. It only needs
to be *present at runtime* so Apotheosis will affix and socket curio items; nothing links against
it. Keep it in `run/mods/`, keep it out of the dependency block.

To test optional-dependency behaviour, **remove** mods from `run/mods/` and relaunch — the mod must
still load. This is the only way to catch a violation; it always compiles either way.

## Architecture

### Optional dependencies

FML loads `@EventBusSubscriber` classes **whether or not their dependencies exist**. A class that
imports an Apotheosis / Relics / Artifacts type and is loaded eagerly will hard-crash any pack
without that mod (`NoClassDefFoundError`).

Rules:

- **All Apotheosis / Relics / Artifacts / Accessories references live under
  `dev.watdafawx.hungryloop.integration.`** and nowhere else. Accessories mixins are in their own
  config (`hungryloop.accessories.mixins.json`), whose plugin skips them when Accessories is absent.
- **No `@EventBusSubscriber` on any integration class.** Register their handlers by hand from
  `HungryLoop`'s constructor, each behind `ModList.get().isLoaded("apotheosis" | "relics" |
  "artifacts")`. Keep client-only integration (screens, tooltips) in a separate class from
  common integration so it only classloads on a client that has the mod.
- **Curios may be referenced freely** — it's a required dependency.
- Method bodies classload lazily, so a guarded call into an integration class is safe; a field of
  an integration type on an eagerly-loaded class is not.

### The stack is the source of truth

The loop's entire state (slot count, shiny flag, stored XP, contents) is a **data component** on the
`ItemStack`. No block entities, no world-saved data. This keeps loops droppable, tradeable and
client-syncable for free. Contents are `List<ItemStack>` in the component.

### Slot-type forwarding is the crux

Everything hard about this mod is one problem: when a contained item asks Curios (or Apotheosis, or
Relics) "what slot am I in?", it must hear the **sub-slot's resolved type** (`ring`, `necklace`, …),
not `hungry_loop`. See [docs/DESIGN.md](docs/DESIGN.md) § "How adaptive slot works". Get this right
once and affixes, abilities and validation mostly fall out of it.

### Client vs server

Aim to keep the mod joinable to a server that doesn't have it (client-only install works in
singleplayer via the integrated server). If a feature needs server authority (XP feeding, slot
growth), gate it on `ServerPlayer` and document it here.

## Conventions

- Match the surrounding style: blank line after a guard clause, `var` where the type is obvious.
- Comments explain **why**, not what — especially anywhere the code looks odd.
- Reuse Curios' / Apotheosis' / Relics' own helpers, textures and formatting rather than
  reimplementing lookalikes.
- Keep [TODO.md](TODO.md) current — check items off in the same change that completes them.

## Known upstream bugs (not ours)

- **apothic_compats load order.** ~11 `affix_loot_entries` for its curio items fail to parse
  ("Items without a valid loot category are not permitted") because Apotheosis parses reloadable
  registries before item tags bind. Parse-time only; runtime affixing works. Without apothic_compats
  at all, curio items resolve to no `LootCategory` and Apotheosis refuses to affix them.
- **Curios screen tooltips** (`TheIllusiveC4/Curios#536`). While `CuriosScreen` is open, hovering a
  slot doesn't deliver the stack to `RenderTooltipEvent.Pre#getItemStack()` — it calls a
  `renderTooltip` overload that drops the `ItemStack`. Relevant if the loop's tooltip/overlay is
  shown over the Curios screen. `foxs-tweaks` works around the reachable half by reading the
  AT-widened `AbstractContainerScreen#hoveredSlot`.
