# Phase 53 — API: play endpoints and events

| | |
|---|---|
| **Stage** | Rules and combat in the App (M3) |
| **Area** | API |
| **Depends on** | Phase 52 (NPCs, quests, badges, bosses, loot); Phase 49 (XP, evolution, friendship, fainting, PvP); Phase 47 (Abilities and held items) |
| **Spec** | chapter 14.2, chapter 14.4, chapter 18.1, chapter 18.4, chapter 18.5 (in `project_v2.md`) |

## Goal

Make the engine playable over the network.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Persist each game's event log; load and rebuild state on start.
- [ ] Command endpoints and WebSocket events for rolls, battles, movement, items, shops and quests (18.1, 18.4).
- [ ] Events to the mod for spawns, walking and battle presentation (18.5).
- [ ] Permission checks: players act only for their own trainer; spectators only watch.

## Tests

Every test must pass before the phase counts as done.

- [ ] Integration tests that play a battle through the API and compare the event log with the engine run directly.
- [ ] A player cannot act for another trainer; a spectator cannot act.
- [ ] Restart the API mid-game: the state is rebuilt from the log.

## Done when

A game can be played through the API.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 53** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
