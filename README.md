# PokeDnD

**Pokémon as a tabletop role-playing game.** Friends play together: one person is the game master (the admin), the others are trainers. Moves, catching and levelling are decided by dice, with the odds worked out for you. You can play with only the web app, or add a Minecraft server where the Pokémon and the world are shown on screen (using Cobblemon's art).

It is an **unofficial, free, non-commercial fan project** — see [CREDITS.md](CREDITS.md).

> Status: early development. The foundations exist (Mod, Core, API and App skeletons, the resource extraction tool). The game itself is not playable yet. The plan is in [`todo.md`](todo.md) and the full specification in [`project_v2.md`](project_v2.md).

## The parts

| Folder | What it is | Built with |
|---|---|---|
| `Core/` | Shared Java library: rules, dice, data importer | Gradle, Java 25 |
| `API/` | The server: accounts, games, rules engine, card PDFs | Quarkus (Gradle, Java 25) |
| `App/` | The web app (installable PWA), English and Portuguese | Angular, Angular Material (npm) |
| `Mod/` | The Fabric mod: shows the game inside Minecraft | Gradle, Java 25 |
| `tools/` | Node scripts that rebuild `resources/` from the Cobblemon jar | Node.js |
| `phases/`, `todo.md` | The work, cut into small phases | — |
| `resources/`, `build/` | **Generated** — not in git | — |

Each of `Core`, `API` and `Mod` is a separate Gradle build; `App` is its own npm project. They are not a monorepo build. Every folder has its own `README.md` (what it is, how to build and run it) and its own `.gitignore`: [Core](Core/README.md), [API](API/README.md), [App](App/README.md), [Mod](Mod/README.md), [tools](tools/README.md), [deploy](deploy/README.md).

## What you need installed

- **Java 25** (JDK). Each Gradle build uses its own wrapper (`gradlew`), so you do not install Gradle.
- **Node.js 24** and npm 11 (for `tools/` and `App/`).
- **Chrome** (the App's unit tests run in headless Chrome).
- Internet access the first time (Gradle, npm and the Cobblemon jar are downloaded).

On Windows, run `gradlew` instead of `./gradlew` in PowerShell.

## 1. Rebuild `resources/` (do this first)

`resources/` holds the Pokémon models, textures, icons, card art and the CSV data. It is generated from the Cobblemon jar and is **not stored in git**.

```sh
node tools/extract.mjs          # downloads the Cobblemon jar if it is missing, checks its SHA-512, then builds resources/
node tools/check-resources.mjs  # smoke test of the result; prints PASS/FAIL lines
```

The jar is about 140 MB and is also git-ignored. Its download address and checksum are in `tools/extract.config.json`. Never point `tools/port-posers.mjs` at `tools/ported-posers/` (it deletes its output folder first).

## 2. Build each project

```sh
# Core — the rules library
cd Core
./gradlew build                  # compile + tests
./gradlew publishToMavenLocal    # installs the jar so API can use it

# API — the Quarkus server (needs Core published first)
cd ../API
./gradlew build                  # compile + tests (no database needed yet)
./gradlew quarkusDev             # start it; then open http://localhost:8080/q/health

# App — the web app
cd ../App
npm ci                           # install exactly what package-lock.json says
npm run build                    # builds dist/pokednd-app/browser/en and /pt
npm test                         # unit tests, headless Chrome
npm run test:smoke               # serves the build and checks home page, manifest, service worker

# Mod — the Minecraft mod
cd ../Mod
./gradlew build                  # the mod jar ends up in Mod/build/libs
```

## Licence

Our code is under the [Mozilla Public License 2.0](LICENSE). The Pokémon art comes from Cobblemon under **CC BY-NC 3.0** (non-commercial; derived work is labelled *modified*). Full credits, the third-party licences and the fan-content disclaimer are in [CREDITS.md](CREDITS.md). Every source file carries the notice defined in [CONTRIBUTING.md](CONTRIBUTING.md).

Pokémon and all Pokémon names are trademarks of Nintendo, Creatures Inc. and GAME FREAK inc. This project has no affiliation with them.
