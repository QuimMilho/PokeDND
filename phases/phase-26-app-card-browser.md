# Phase 26 — App: card browser

| | |
|---|---|
| **Stage** | Data and cards (M1) |
| **Area** | App |
| **Depends on** | Phase 25 (App: login and API client); Phase 21 (API: database layer and reference data) |
| **Spec** | chapter 9.9, chapter 11.2, chapter 9.1 (in `project_v2.md`) |

## Goal

A screen where people can find species and see how the card will look.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] Paged list of species with art, name, Dex number and types; filters for generation and type; search by name.
- [x] A detail view showing the card preview with its four moves (from the reference-data endpoints).
- [x] Phone-first layout, usable on a PC too.

## Tests

Every test must pass before the phase counts as done.

- [x] Unit tests for the filter and search logic.
- [x] Component tests: filters change the list; the empty state shows a message.
- [x] The list loads Generation 1 (151 species) from the real API in a smoke test.

## Done when

People can browse and preview species cards.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 26** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
