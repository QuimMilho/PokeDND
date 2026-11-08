# Phase 02 — Mod skeleton clean-up

| | |
|---|---|
| **Stage** | Resources and foundations (M0) |
| **Area** | Mod |
| **Depends on** | Phase 01 (Regenerate all resources) |
| **Spec** | chapter 13.1, chapter 13.2, chapter 15.1 (in `project_v2.md`) |

## Goal

Turn the Fabric template into the real mod project: right folder name, right licence, clear class names, and a build that passes. No features yet.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

**Ask the user first:**

- Are the new entry class names `PokeDnD` / `PokeDnDClient` fine?
- Create the Gradle wrapper by downloading Gradle, or with IntelliJ?
- A Java 25 toolchain that Gradle downloads, or install JDK 25 yourself?

## Tasks

- [ ] Close any IDE that has the folder open, then rename `PokeDnD/` to `Mod/` (a normal rename; `git mv` is not possible because nothing is tracked yet).
- [ ] Bootstrap Gradle. The folder has **no `gradlew`, `gradlew.bat` or `gradle-wrapper.jar`** (only `gradle-wrapper.properties`, pointing at Gradle 9.8.2) and no `gradle` command is on the PATH. Download the distribution named in that file into a temporary folder outside the repository, run `gradle wrapper` once in `Mod/`, and keep the wrapper files (they are tracked, not ignored). IntelliJ can also generate them.
- [ ] Check the JDK: the machine has JDK 27 on the PATH, but the spec needs Java 25 (Minecraft 26.3). Use a Gradle toolchain for Java 25 (it can download it) or install JDK 25, and write down which.
- [ ] In `fabric.mod.json`: set `license` to `MPL-2.0`, write a real `description`, fill `authors` and `credits` (the credit wording is decided in 21.2).
- [ ] Remove the unused `yarn_mappings` line from `gradle.properties` (Minecraft 26.x needs no mappings).
- [ ] Replace `LICENSE.txt` with the MPL-2.0 text (Phase 06 adds the root `LICENSE`; copy the same text).
- [ ] Rename the entry classes `Core`, `CoreClient`, `CoreDataGenerator` to `PokeDnD`, `PokeDnDClient`, `PokeDnDDataGenerator` *(Suggested)* so they do not clash with the new Core library, and update `fabric.mod.json` and the mixin files.
- [ ] Update every path in the repository that still says `PokeDnD/` (docs, `.gitignore` already covers both names).

## Tests

Every test must pass before the phase counts as done.

- [ ] `./gradlew --version` works in `Mod/` and reports Gradle 9.8.2 and Java 25; the wrapper files are not ignored by git (`git check-ignore` prints nothing for them).
- [ ] `./gradlew build` in `Mod/` finishes with BUILD SUCCESSFUL.
- [ ] The built jar contains `fabric.mod.json` with license MPL-2.0 and the new entrypoint class names (check with a zip listing).
- [ ] `gradle.properties` has no `yarn_mappings`; no source file still refers to the old class names (search).
- [ ] `git status --short` shows `Mod/src` and the Gradle wrapper as untracked, and nothing under `Mod/build`, `Mod/.gradle`, `Mod/run`, `Mod/.idea`.

## Done when

The mod lives in `Mod/`, builds, and its metadata matches chapter 13.2.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 02** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
