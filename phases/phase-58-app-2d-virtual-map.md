# Phase 58 — App: 2D virtual map

| | |
|---|---|
| **Stage** | Rules and combat in the App (M3) |
| **Area** | App |
| **Depends on** | Phase 55 (App: play screens); Phase 51 (World, paths, movement, encounters) |
| **Spec** | chapter 11.5, chapter 8.3, chapter 8.4 (in `project_v2.md`) |

## Goal

The overworld on a screen: this is how M3 is played before the mod exists.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Draw nodes, edges and gates from the world data; show trainers and parties; layers (`app.map.layers.default`).
- [ ] Choose a path at a fork and see movement rounds happen.
- [ ] Fog of war as a client-side mask for now (Risks in chapter 22).

## Tests

Every test must pass before the phase counts as done.

- [ ] Component tests that the map draws a fixture world correctly.
- [ ] Playwright: a player walks a path and meets an encounter.
- [ ] Hidden nodes stay hidden for players and are shown to the admin.

## Done when

The overworld can be walked in the app.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 58** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
