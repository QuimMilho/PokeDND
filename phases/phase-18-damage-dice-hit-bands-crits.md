# Phase 18 — Damage dice, hit bands, crits

| | |
|---|---|
| **Stage** | Data and cards (M1) |
| **Area** | Core |
| **Depends on** | Phase 15 (Type chart and natures); Phase 16 (Smart dice selector) |
| **Spec** | chapter 7.5, chapter 7.6, chapter 6.5 (in `project_v2.md`) |

## Goal

Turn a move into dice: how likely it is to hit, to crit, and how many dice of damage it does.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] The power table (`combat.damage.powerTable`) as editable data, with the default values of 7.6.
- [x] Hit bands from accuracy and stat stages (7.5) using the smart dice; crit chance and stages.
- [x] Damage from dice, STAB, type effectiveness, burn, and the scale settings, with every setting defaulted.

## Tests

Every test must pass before the phase counts as done.

- [x] Every worked example in 7.5 and 7.6 is a test.
- [x] A move that always hits (accuracy shown as the dash) never produces a miss band.
- [x] Changing the power table changes the result as expected (a test edits the table).

## Done when

Hit, crit and damage calculators pass the documented examples.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 18** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
