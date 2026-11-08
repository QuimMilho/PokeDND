# Phase 72 — App: map and path editor, fog of war

| | |
|---|---|
| **Stage** | The Minecraft mod (M4) |
| **Area** | App |
| **Depends on** | Phase 71 (BlueMap markers and map proxy); Phase 58 (App: 2D virtual map) |
| **Spec** | chapter 11.5, chapter 8.3, chapter 15.12 (in `project_v2.md`) |

## Goal

The admin edits the world from the app; players see only what they may.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Embed the proxied map in the app with layers (`app.map.layers.*`).
- [ ] Path editor: add, move and remove nodes and edges and gates, with the same validator, sent to the mod through the API.
- [ ] Fog of war as a client-side mask now; API tile filtering is a later improvement (Risks in chapter 22).

## Tests

Every test must pass before the phase counts as done.

- [ ] Component tests for the editor operations and validation messages.
- [ ] Playwright against the API and the fake mod: edit a path and see the fake mod receive it.
- [ ] Players do not see the region layer (`app.map.layers.regions` is admin only).

## Done when

Paths can be edited on the map in the app.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 72** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
