# Phase 57 — App: cards in play

| | |
|---|---|
| **Stage** | Rules and combat in the App (M3) |
| **Area** | App |
| **Depends on** | Phase 55 (App: play screens) |
| **Spec** | chapter 9.4, chapter 9.5, chapter 9.9, chapter 9.1 (in `project_v2.md`) |

## Goal

The cards inside the game: see them, edit them (digital mode) or bind printed ones.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Virtual card view (9.9): art, level, HP bar, status, moves with PP, ability, nature, held item; cosmetics.
- [ ] Personal cards (9.4): export a card for one owned Pokémon with its real stats.
- [ ] Physical mode (9.5): bind a printed card by scanning its QR code (camera scanner, which needs HTTPS), the unbound behaviour setting, rescan after trade, and the admin's manual bind fallback.

## Tests

Every test must pass before the phase counts as done.

- [ ] Component tests for the card view and the editable fields.
- [ ] The scanner decodes a QR image from a test file and binds the card.
- [ ] A trade requires a rescan when `cards.physical.rescanAfterTrade` is true.

## Done when

Cards work in the app in both digital and physical modes.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 57** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
