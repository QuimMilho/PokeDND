# Phase 38 — App: trainer wizard

| | |
|---|---|
| **Stage** | Accounts, servers, lobby, settings (M2) |
| **Area** | App |
| **Depends on** | Phase 36 (App: games, lobby and the settings screen); Phase 37 (Trainers: model, classes, creation rules) |
| **Spec** | chapter 4.3, chapter 3.6, chapter 5.11, chapter 11.3 (in `project_v2.md`) |

## Goal

Players create their trainer in the app; the API stores it and validates the skin.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] API: trainer endpoints, the skin upload (PNG only, 64x64, size-limited, re-encoded to strip metadata, classic or slim) and the trainer name rules (3.6: 3 to 16 characters, unique in the game).
- [ ] App wizard: class, ability scores, skills, starter Pokémon (5.11), name and skin (upload, or copy from a Minecraft username if the server allows).
- [ ] A summary page and a way to edit before the game starts.

## Tests

Every test must pass before the phase counts as done.

- [ ] API tests: valid and invalid skins (wrong size, metadata, not a PNG), name rules, duplicates.
- [ ] Component tests for each wizard step and the validation messages in both languages.
- [ ] Playwright: three users create three trainers in one game.

## Done when

Players can create valid trainers with skins in the app.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 38** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
