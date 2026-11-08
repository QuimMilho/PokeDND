# Phase 55 — App: play screens

| | |
|---|---|
| **Stage** | Rules and combat in the App (M3) |
| **Area** | App |
| **Depends on** | Phase 53 (API: play endpoints and events) |
| **Spec** | chapter 11.2, chapter 11.3, chapter 11.6, chapter 6.7 (in `project_v2.md`) |

## Goal

What a player sees and does during a game.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Roll prompt screen: the die shown big, the bands in words, and an input for the real result (or a digital roll button), clear on a phone.
- [ ] Battle screen with the log, actions, targets and statuses; bag and party screens; trainer sheet.
- [ ] In-app notifications when it is your turn or something needs you.

## Tests

Every test must pass before the phase counts as done.

- [ ] Component tests for the roll prompt (all dice, invalid values) and the battle actions.
- [ ] Playwright: a player takes a turn in a scripted battle against the real API.
- [ ] Screens work at 360 px width.

## Done when

A player can play a battle from a phone.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 55** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
