# Phase 73 — Mod tests and the M4 end-to-end session

| | |
|---|---|
| **Stage** | The Minecraft mod (M4) |
| **Area** | Mod |
| **Depends on** | Phase 70 (Mod: battle presentation, catching, bosses, sound); Phase 72 (App: map and path editor, fog of war); Phase 67 (Mod: NPCs, world protection, vanilla rules) |
| **Spec** | chapter 2, chapter 15.15, chapter 20.5, chapter 20.6, chapter 14.8 (in `project_v2.md`) |

## Goal

Finish milestone M4: the full session works in real Minecraft.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

**Ask the user first:**

- Who will run the real Minecraft session, and on which server?

## Tasks

- [ ] Gather all GameTests (the list in 15.15) into one suite that runs with one command, and add the missing ones.
- [ ] Write the manual checklist file `Mod/CHECKLIST.md`: model quality in all poses, camera shots, key bindings, HUD readability on a TV.
- [ ] Run the chapter 2 session end to end with real Minecraft clients and the app. Note every problem in `playtests/M4.md`.
- [ ] Measure the targets in 14.8 (players per game, message rates, first-load size) and record them.

## Tests

Every test must pass before the phase counts as done.

- [ ] The GameTest suite is green.
- [ ] The chapter 2 session is completed and its notes written.
- [ ] All earlier test suites (Core, API, App, tools) are green.

## Done when

The whole example session can be played with Minecraft as the screen.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 73** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
