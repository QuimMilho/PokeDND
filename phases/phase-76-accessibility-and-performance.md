# Phase 76 — Accessibility and performance

| | |
|---|---|
| **Stage** | Polish and release (M5) |
| **Area** | App |
| **Depends on** | Phase 74 (Complete Portuguese) |
| **Spec** | chapter 14.8, chapter 20.4, chapter 11.1 (in `project_v2.md`) |

## Goal

The app is usable by everyone and fast on a phone.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Run automated accessibility checks (for example axe through Playwright) on the main screens in both languages and both themes; fix what they find.
- [ ] Measure the first-load size and speed, and the realtime message rates, against 14.8; fix the worst offenders.
- [ ] A load test with many fake clients on one game.

## Tests

Every test must pass before the phase counts as done.

- [ ] The accessibility checks report no serious violations on the main screens.
- [ ] The measured numbers are written in 14.8 next to the targets and meet them (or the gap is explained).
- [ ] Screenshots at phone and desktop sizes for the main screens are stored for comparison.

## Done when

Accessibility checks pass and performance targets are met.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 76** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
