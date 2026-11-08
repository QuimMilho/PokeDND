# Phase 19 — Catch and flee calculators

| | |
|---|---|
| **Stage** | Data and cards (M1) |
| **Area** | Core |
| **Depends on** | Phase 16 (Smart dice selector); Phase 17 (Stats, XP and levelling) |
| **Spec** | chapter 7.10, chapter 7.11, chapter 6.5 (in `project_v2.md`) |

## Goal

Implement the catch and flee odds with the ball table, and express them as dice rolls.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Catch chance from the species catch rate, HP, status and the ball table in 7.10 (the common balls; special balls have their own rules).
- [ ] Flee chance (7.11) with its settings.
- [ ] Both return a smart-dice roll (die and bands).

## Tests

Every test must pass before the phase counts as done.

- [ ] The worked examples in 7.10 and 7.11 are test cases.
- [ ] A Master Ball always catches; a fainted-or-full-HP edge case behaves as documented.
- [ ] The ball table covers every ball in `resources/icons/items/poke_balls` or marks it "not for sale / special".

## Done when

Catch and flee odds match the documented examples.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 19** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
