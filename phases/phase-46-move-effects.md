# Phase 46 — Move effects

| | |
|---|---|
| **Stage** | Rules and combat in the App (M3) |
| **Area** | Core |
| **Depends on** | Phase 45 (Hit, damage, types, status) |
| **Spec** | chapter 19.6, chapter 7.8 (in `project_v2.md`) |

## Goal

Make most moves work. Plain-data moves are automatic; the most-used scripted ones are written by hand; the rest fall back to the admin.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] Automatic moves: recoil, drain, healing, multi-hit, one-hit knockouts, fixed damage, self boosts, priority, switch-out, secondary chances.
- [x] Implement the 25 most-learned scripted moves from `build/reports/coverage.md` (Rest, Sleep Talk, Helping Hand, Giga Impact, Hyper Beam, Protect, Endure, Substitute, Tera Blast, Facade, and so on).
- [x] The manual class: the app shows the move's text and the admin applies the result (7.8).
- [x] Regenerate the coverage report and record the new numbers in 19.6.

## Tests

Every test must pass before the phase counts as done.

- [x] One test per implemented scripted move, and a generic test per automatic effect type.
- [x] The coverage report counts match the implemented set; the learnset-row coverage reaches about 71 % with the top 25 (19.6).
- [x] An unimplemented move produces an admin task, never a crash.

## Done when

The most used moves work; the rest are handled by the admin.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 46** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
