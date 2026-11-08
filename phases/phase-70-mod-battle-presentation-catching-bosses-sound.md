# Phase 70 — Mod: battle presentation, catching, bosses, sound

| | |
|---|---|
| **Stage** | The Minecraft mod (M4) |
| **Area** | Mod |
| **Depends on** | Phase 69 (Mod: controls, HUD, screens); Phase 64 (Renderer 3: procedural motion, layers, entities); Phase 59 (M3 full-game test and first play-test) |
| **Spec** | chapter 15.10, chapter 10.9, chapter 8.9, chapter 7.3 (in `project_v2.md`) |

## Goal

Make a battle look like a battle.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] The battle presenter (15.10): place the Pokémon, play physical, special and status animations, show damage and faint, driven by the API's events.
- [ ] Catch animation and result; boss presentation (giant scale, effects) and shiny sparkle and sound.
- [ ] Cries and effect sounds (10.9, `sounds/pokemon/`).
- [ ] A presentation speed setting so a fast game does not wait for animations.

## Tests

Every test must pass before the phase counts as done.

- [ ] A GameTest plays a recorded battle event log and ends in the expected world state.
- [ ] Skipping the presentation still leaves the right final state.
- [ ] Manual checklist: model quality in all poses, effects readable.

## Done when

A recorded battle plays correctly in Minecraft.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 70** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
