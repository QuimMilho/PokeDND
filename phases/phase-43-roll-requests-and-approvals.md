# Phase 43 — Roll requests and approvals

| | |
|---|---|
| **Stage** | Rules and combat in the App (M3) |
| **Area** | Core |
| **Depends on** | Phase 42 (Game state, event log, undo) |
| **Spec** | chapter 6.3, chapter 6.6, chapter 6.7, chapter 6.8 (in `project_v2.md`) |

## Goal

The flow around every dice roll: the system asks, a human rolls a real die (or the app does), the result is checked and recorded.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] Roll request objects: what is rolled, the die, the bands and who must answer (6.7).
- [x] Input modes and approvals as settings (6.8): admin enters results; players enter and the admin approves; digital rolls by the system.
- [x] Modifiers (6.6) and the explanation text for the UI.
- [x] Rolls made by the system use a seeded generator that is stored, so replay is exact.

## Tests

Every test must pass before the phase counts as done.

- [x] Each input mode is a scripted scenario test.
- [x] An invalid value (a 7 on a d6) is refused.
- [x] Digital rolls replay to the same values from the log.

## Done when

Roll requests work in every input mode.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 43** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
