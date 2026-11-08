# Phase 03 — Core library skeleton

| | |
|---|---|
| **Stage** | Resources and foundations (M0) |
| **Area** | Core |
| **Depends on** | Phase 01 (Regenerate all resources); Phase 02 (Mod skeleton clean-up) |
| **Spec** | chapter 13.1, chapter 13.3, chapter 13.4 (in `project_v2.md`) |

## Goal

Create the shared Java library that the API and the mod will both use. It has no Minecraft code and no Quarkus code; only a JSON library and the SLF4J API are allowed.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] Create `Core/` as its own Gradle build (Java 25, Gradle 9.8.2 like the mod). Copy the wrapper files (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) from `Mod/` (made in Phase 02) and use the same Java 25 toolchain. Group `net.quimmilho.pokednd`, artifact `core`.
- [x] Pin versions in a version catalog (`gradle/libs.versions.toml`): Jackson, SLF4J API, JUnit 5. Use the newest stable versions at the time and record them.
- [x] Add one small class (for example `CoreVersion`) and one JUnit test, so the pipeline is real.
- [x] Configure `maven-publish` so `./gradlew publishToMavenLocal` installs the jar.
- [x] Add a build check that fails if Core depends on anything other than Jackson and the SLF4J API *(Suggested)*.
- [x] Add the standard MPL-2.0 source-file notice to the sample files.

## Tests

Every test must pass before the phase counts as done.

- [x] `./gradlew build` passes in `Core/`.
- [x] `./gradlew publishToMavenLocal` puts `net/quimmilho/pokednd/core` in the local Maven repository.
- [x] The dependency check passes, and fails when a forbidden dependency is added (try it once, then undo).
- [x] `git status --short` shows no `Core/build` or `Core/.gradle`.

## Done when

Core builds, is tested, and can be published to the local Maven repository.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 03** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
