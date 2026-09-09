# The Hungry Loop

A NeoForge **1.21.1** mod. One curio that holds other curios — and grants their effects as if each
were equipped in its own native slot.

In a big modpack you never have enough curio slots. The Hungry Loop is a single item you equip once;
whatever you socket into it (a ring, an amulet, a [Relics][relics] relic, an [Artifacts][artifacts]
artifact) works exactly as though it were in its proper slot: attributes, [Apotheosis][apotheosis]
affixes and gem sockets, abilities and all. Storage starts at 4 slots and grows as you feed the loop
experience; loot-found "shiny" loops start with 4–8.

Named after the ring from Path of Exile.

## Status

Early scaffold. See [TODO.md](TODO.md) for the roadmap and [docs/DESIGN.md](docs/DESIGN.md) for the
design. Nothing is playable yet beyond the item existing.

## Dependencies

| Mod | Required? |
|-----|-----------|
| [Curios API][curios] | **yes** |
| [Apotheosis][apotheosis] (+ Apothic Attributes) | optional |
| [Relics][relics] | optional |
| [Artifacts][artifacts] | optional |

The mod loads and works with any subset of the optional mods installed.

## Building

```bat
build.bat build          REM compile + jar
build.bat runClient      REM dev client (needs jars in run/mods/ - see AGENTS.md)
```

Requires JDK 21. `build.bat` finds it automatically on this machine.

## Licence

MIT — see [gradle.properties](gradle.properties).

[curios]: https://www.curseforge.com/minecraft/mc-mods/curios
[apotheosis]: https://www.curseforge.com/minecraft/mc-mods/apotheosis
[relics]: https://www.curseforge.com/minecraft/mc-mods/relics-mod
[artifacts]: https://www.curseforge.com/minecraft/mc-mods/artifacts
