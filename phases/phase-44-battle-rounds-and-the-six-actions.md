# Phase 44 — Battle rounds and the six actions

| | |
|---|---|
| **Stage** | Rules and combat in the App (M3) |
| **Area** | Core |
| **Depends on** | Phase 43 (Roll requests and approvals) |
| **Spec** | chapter 7.1, chapter 7.2, chapter 7.3, chapter 7.4 (in `project_v2.md`) |

## Goal

The turn structure of a battle, without damage yet.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] Battle start (7.2) and participants.
- [x] The round (7.3): switches, items and flee first, then moves by the `Speed / 20` initiative rule (Appendix D #6).
- [x] The six actions (7.4) as commands that produce events.
- [x] A text battle log in plain language.

## Tests

Every test must pass before the phase counts as done.

- [x] Scripted rounds with fixed rolls give the documented order.
- [x] A player can skip a turn; a fainted Pokémon cannot act.
- [x] The log replays from events.

## Done when

Battles run round by round with the right turn order.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 44** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
