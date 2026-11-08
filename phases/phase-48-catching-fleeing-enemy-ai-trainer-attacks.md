# Phase 48 — Catching, fleeing, enemy AI, trainer attacks

| | |
|---|---|
| **Stage** | Rules and combat in the App (M3) |
| **Area** | Core |
| **Depends on** | Phase 44 (Battle rounds and the six actions); Phase 19 (Catch and flee calculators) |
| **Spec** | chapter 7.10, chapter 7.11, chapter 7.12, chapter 7.13, chapter 4.6 (in `project_v2.md`) |

## Goal

Wild Pokémon behave, can be caught or escaped from, and can threaten trainers.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Catching in battle with the roll flow (7.10) and fleeing (7.11).
- [ ] Enemy AI (7.13) and how enemies choose targets, including attacking trainers (7.12): the 10 % chance, the `level / 8` bonus, the trainer Defense value and the damage scale.
- [ ] Trainer health and knock-out rules (4.6).

## Tests

Every test must pass before the phase counts as done.

- [ ] Scripted catch and flee scenarios with fixed rolls.
- [ ] The AI never chooses an impossible action (property test over random states).
- [ ] A knocked-out trainer follows the documented rules.

## Done when

Wild encounters can be fought, caught or fled.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 48** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
