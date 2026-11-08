# Phase 60 — Mod: connection, hello, config

| | |
|---|---|
| **Stage** | The Minecraft mod (M4) |
| **Area** | Mod |
| **Depends on** | Phase 41 (M2 end-to-end test); Phase 02 (Mod skeleton clean-up) |
| **Spec** | chapter 15.1, chapter 15.11, chapter 14.3, chapter 18.2, chapter 18.5, chapter 15.16 (in `project_v2.md`) |

## Goal

Give the mod its link to the API. Nothing visible yet, but it must connect, authenticate and stay connected.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Read `config/pokednd.json` (API URL, server token) and create a documented default file on first start.
- [ ] A WebSocket client in the mod (server side) with the message envelope of 18.3, the `hello` handshake, the protocol-version check and reconnection with back-off.
- [ ] Include the Core library in the mod jar (Fabric jar-in-jar) and use it for the shared message types.
- [ ] A `/pokednd status` command showing connected or not, and the last error.
- [ ] The mod refuses to talk to an incompatible API and says which side must be updated (20.8).

## Tests

Every test must pass before the phase counts as done.

- [ ] Unit tests for the message encoding and the reconnect timing, with a stub WebSocket server.
- [ ] A Fabric GameTest that the command answers in each state.
- [ ] Manual-but-scripted: start a dev server with the mod and the real API from compose; the server shows as online in the app. Write the steps down in the checklist file for this phase.

## Done when

The mod connects to the API and shows as online.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 60** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
