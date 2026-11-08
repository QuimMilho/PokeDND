# Phase 17 — Stats, XP and levelling

| | |
|---|---|
| **Stage** | Data and cards (M1) |
| **Area** | Core |
| **Depends on** | Phase 03 (Core library skeleton) |
| **Spec** | chapter 5.2, chapter 5.3, chapter 4.5 (in `project_v2.md`) |

## Goal

Put the numbers for a Pokémon's stats and for experience into Core.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Stat formulas exactly as in 5.2 (HP and other stats, IV 0-31, EV 0-252 and 510 in total, nature multiplier).
- [ ] XP needed per level for the six growth groups (5.3) and the XP reward from a defeated Pokémon.
- [ ] The trainer XP curve (4.5) and the `trainer.xp.multiplier` setting.
- [ ] Settings for the IV mode (`random` or `fixed`) with their defaults.

## Tests

Every test must pass before the phase counts as done.

- [ ] The worked examples in 5.2 and 5.3 are test cases.
- [ ] A few official values (for example a level 50 Garchomp with known IVs/EVs) match.
- [ ] XP is strictly increasing per level for all six groups and level 100 is reached.

## Done when

Stats and XP calculators pass the documented examples.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 17** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
