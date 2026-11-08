# Phase 68 — Mod: cameras

| | |
|---|---|
| **Stage** | The Minecraft mod (M4) |
| **Area** | Mod |
| **Depends on** | Phase 61 (Mod: fake players, commands, skins); Phase 64 (Renderer 3: procedural motion, layers, entities) |
| **Spec** | chapter 10.1, chapter 10.2, chapter 10.3, chapter 10.4, chapter 10.5 (in `project_v2.md`) |

## Goal

The Minecraft screen shows the game well: players only switch cameras, the admin can fly.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Lock real players in place and show them the auto camera (director) by default (10.1, 10.3).
- [ ] The trainer view on a configurable key (10.4) and who sees what (10.2).
- [ ] The admin's flying camera (10.5).
- [ ] Camera shots for walking, encounters and battles, with settings for each.

## Tests

Every test must pass before the phase counts as done.

- [ ] GameTests for the camera state machine (which camera in which game state).
- [ ] Players cannot move during a game (simulated input does nothing).
- [ ] Manual checklist entry for shot quality on a shared screen (20.5).

## Done when

The shared screen shows the game with sensible cameras.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 68** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
