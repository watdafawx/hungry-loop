# CLAUDE.md

See **[AGENTS.md](AGENTS.md)** — single source of truth for this repo. Everything there applies.

The three things most likely to bite:

1. **Build with `build.bat build`, not `gradlew`** — there may be no JDK on `PATH`; the script
   locates JDK 21 (`toolchain\`, else Adoptium in Program Files).
2. **Curios is the only hard dependency.** Apotheosis, Relics and Artifacts are optional. Never put
   `@EventBusSubscriber` on a class importing their types, and keep every reference to them under
   `dev.watdafawx.hungryloop.integration`. Verify by launching a dev client with those jars removed from
   `run/mods/`.
3. **The dev client needs real mod jars in `run/mods/`** (FML only scans that folder). Compiling
   does not — Gradle resolves everything. `scripts/fetch-testmods.ps1` fills it.

Roadmap and progress: **[TODO.md](TODO.md)**. Concept and open questions: **[docs/DESIGN.md](docs/DESIGN.md)**.
