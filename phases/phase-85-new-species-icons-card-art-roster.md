# Phase 85 — New species: icons, card art, roster

| | |
|---|---|
| **Stage** | Asset track (runs in parallel, optional) |
| **Area** | Assets |
| **Depends on** | Phase 83 (Modelling brief for the missing species); Phase 84 (Animations for Bounsweet and Pyukumuku); Phase 09 (check-assets tool and the roster) |
| **Spec** | chapter 9.8, Appendix C (in `project_v2.md`) |

## Goal

Make newly modelled species fully playable and printable.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

**Ask the user first:**

- Where should new, hand-made assets be stored in the repository?

## Tasks

- [ ] Copy the delivered files into the place `tools/extract.mjs` reads them from (decide where new assets live so they are tracked and merged into `resources/`).
- [ ] Render icons and card art for each (`tools/render-icons.mjs --only <folder>`), then re-run the asset check and regenerate the roster.
- [ ] Update Appendix C.1.

## Tests

Every test must pass before the phase counts as done.

- [ ] The roster has the new species and `tools/check-assets.mjs` passes.
- [ ] Their cards export and look right (one exported card per species checked by eye).
- [ ] Rebuilding `resources/` from scratch still produces them.

## Done when

New species are in the roster and on the cards.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 85** in `todo.md`, and only now: the phase is added **and** tested. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
