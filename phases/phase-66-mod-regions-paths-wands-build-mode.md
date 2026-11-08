# Phase 66 — Mod: regions, paths, wands, build mode

| | |
|---|---|
| **Stage** | The Minecraft mod (M4) |
| **Area** | Mod |
| **Depends on** | Phase 65 (Mod: world configuration) |
| **Spec** | chapter 15.6, chapter 10.6, chapter 8.2, chapter 8.3 (in `project_v2.md`) |

## Goal

A real Minecraft user who is an admin can build the game world without leaving the game.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Wands to mark region corners and path nodes, with particles showing what is selected.
- [ ] Commands to name regions, set spawn tables and levels, connect nodes with edges and gates (distances in blocks).
- [ ] Build mode (10.6) so the admin can edit while a game is paused.

## Tests

Every test must pass before the phase counts as done.

- [ ] GameTests for creating a region and a path with the wands (simulated clicks).
- [ ] The result passes the path validator from Phase 51.
- [ ] Players who are not admins cannot use the wands.

## Done when

An admin can create regions and paths in the game.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 66** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
