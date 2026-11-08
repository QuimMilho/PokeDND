# Phase 16 — Smart dice selector

| | |
|---|---|
| **Stage** | Data and cards (M1) |
| **Area** | Core |
| **Depends on** | Phase 03 (Core library skeleton) |
| **Spec** | chapter 6.1, chapter 6.2, chapter 6.4, chapter 6.5, Appendix B (in `project_v2.md`) |

## Goal

Implement the heart of the dice system: given a chance (or several outcomes), choose the die and the bands, so players roll a die that fits the odds.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Port the reference implementation of Appendix B.3 into Core, with dice notation (6.2): d4, d6, d8, d10, d12, d20, d100, and the tolerance and preferences as settings with defaults.
- [ ] Support one success chance (B.1) and several outcomes on one roll (B.2).
- [ ] Return a small result object: die, bands per outcome, the error against the official odds, and a plain-language explanation for the UI.

## Tests

Every test must pass before the phase counts as done.

- [ ] Every example in chapter 6.5 and Appendix B gives exactly the die and bands shown there.
- [ ] Property tests: the bands always add up to the die's sides; the error never exceeds the tolerance; a possible outcome never ends with zero faces.
- [ ] Edge odds (0 %, 100 %, 1 %, 99 %, 33.3 %) behave as documented.

## Done when

Smart dice reproduce every documented example and pass the property tests.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 16** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
