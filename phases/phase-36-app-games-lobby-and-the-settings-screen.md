# Phase 36 — App: games, lobby and the settings screen

| | |
|---|---|
| **Stage** | Accounts, servers, lobby, settings (M2) |
| **Area** | App |
| **Depends on** | Phase 35 (App: shell, themes, translations); Phase 32 (API: games); Phase 33 (API: layered settings, rule sets, presets) |
| **Spec** | chapter 11.3, chapter 11.4, chapter 12.1, chapter 12.2, chapter 12.3, chapter 3.4, chapter 3.5 (in `project_v2.md`) |

## Goal

The screens that let an admin create a game and open a lobby, and players join it.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] Create game (choose server, name, preset), game page with its state and buttons for the transitions the user is allowed to do.
- [x] Lobby: the join code, requests to accept or reject, members list; join by code for players; spectators.
- [x] Settings screen with every key grouped as in Appendix A, search, default markers, preset and rule-set pickers, and "reset to default".

## Tests

Every test must pass before the phase counts as done.

- [x] Component tests for the settings groups and the reset button.
- [x] Playwright: create a game, open the lobby, join with a second user, accept.
- [x] The settings screen shows exactly the keys of the registry.

## Done when

An admin can create a game, open the lobby and configure it; a player can join.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 36** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
