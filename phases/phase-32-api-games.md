# Phase 32 — API: games

| | |
|---|---|
| **Stage** | Accounts, servers, lobby, settings (M2) |
| **Area** | API |
| **Depends on** | Phase 31 (API: servers) |
| **Spec** | chapter 3.3, chapter 3.4, chapter 3.5, chapter 3.8, chapter 16.1, chapter 17.2 (in `project_v2.md`) |

## Goal

The game as an object: its lifecycle, who is in it, and how people join.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] Entities `Game` and `Membership` and the state machine of 3.4 (draft, lobby, running, paused, ended) with the allowed transitions.
- [x] The owner admin can add co-admins (other admins of the same server). The admin may also play (3.3).
- [x] Join code: 6 characters, no look-alikes, valid while the lobby is open; requests are accepted or rejected by the admin (3.5).
- [x] One server, one active game (3.8); progress between games as the spec says.

## Tests

Every test must pass before the phase counts as done.

- [x] Every allowed and every forbidden transition is a test.
- [x] A second active game on one server is refused.
- [x] Join code: valid only while the lobby is open; request, accept, reject.

## Done when

Games can be created, joined and moved through their states.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 32** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.

## Notes from doing the phase

- "Start game" and "close lobby" are the same step (Lobby to Running); the join code is stored only while the lobby is open (Appendix D #66 to #68).
- "Progress between games": a trainer, team, bag and money belong to one game, and nothing is shared between games. **Importing a trainer from another game waits for phases 37 and 38**, since trainers do not exist yet.
- The game settings (`game.admin.plays`, `game.lobby.codeLength`, `game.lobby.autoAccept`, `game.players.max`…) are not read yet: the registry comes in phase 33, and until then the defaults are fixed in `GameService`.
- Tests: `games/GameStateMachineTest` (all 42 state and command pairs), `games/GamesTest`.
