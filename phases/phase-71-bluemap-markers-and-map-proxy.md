# Phase 71 — BlueMap markers and map proxy

| | |
|---|---|
| **Stage** | The Minecraft mod (M4) |
| **Area** | API |
| **Depends on** | Phase 66 (Mod: regions, paths, wands, build mode) |
| **Spec** | chapter 15.12, chapter 16.6, chapter 11.5 (in `project_v2.md`) |

## Goal

A live web map of the game with overlays, safe by role.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Mod: marker sets for trainers and parties, nodes and edges, NPCs and encounters, updated as the game changes (BlueMap's Java API).
- [ ] API: proxy BlueMap tiles and marker files to the app and filter markers per viewer (fog of war, roles).
- [ ] BlueMap and the mod are both installed on the server; nothing of BlueMap is bundled.

## Tests

Every test must pass before the phase counts as done.

- [ ] API test with a stub BlueMap server: a player does not receive hidden markers; the admin does.
- [ ] GameTest: markers appear and update when a trainer moves.
- [ ] Proxy failures (BlueMap down) give a clear error, not a crash.

## Done when

The map and markers reach the API and are filtered per viewer.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 71** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
