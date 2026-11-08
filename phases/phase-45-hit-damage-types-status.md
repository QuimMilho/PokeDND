# Phase 45 — Hit, damage, types, status

| | |
|---|---|
| **Stage** | Rules and combat in the App (M3) |
| **Area** | Core |
| **Depends on** | Phase 44 (Battle rounds and the six actions); Phase 18 (Damage dice, hit bands, crits) |
| **Spec** | chapter 7.5, chapter 7.6, chapter 7.7, chapter 7.8, chapter 5.6 (in `project_v2.md`) |

## Goal

Moves now do something: they hit or miss, hurt, and apply statuses and stat stages.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Wire hit bands, crit bands and damage dice into move use; type effectiveness and STAB.
- [ ] Status conditions of 5.6 (burn, poison, bad poison, paralysis, sleep, freeze, confusion, flinch) and stat stages, each with its dice.
- [ ] Secondary effect chances become smart dice rolled by the attacker's owner (7.8).

## Tests

Every test must pass before the phase counts as done.

- [ ] Scripted battles with fixed rolls produce the documented damage and statuses.
- [ ] Each status has a test for start, each turn, and end.
- [ ] A fainted Pokémon is removed and the battle can end.

## Done when

Basic fights with statuses work end to end.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 45** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
