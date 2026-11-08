# Phase 40 — API: realtime channel and server protocol

| | |
|---|---|
| **Stage** | Accounts, servers, lobby, settings (M2) |
| **Area** | API |
| **Depends on** | Phase 32 (API: games); Phase 39 (tools/fake-mod) |
| **Spec** | chapter 14.3, chapter 14.4, chapter 14.7, chapter 18.2, chapter 18.3, chapter 18.4, chapter 18.5 (in `project_v2.md`) |

## Goal

Live communication: the app and the mod keep a connection to the API and exchange typed messages.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] WebSockets Next endpoints. Users authenticate with the access token in the first message; servers with the server token (18.2).
- [ ] The message envelope (18.3), with version, id and type, plus the app-to-API events (18.4) and the mod-to-API events (18.5) needed so far: hello, config sync, lobby and spawn commands.
- [ ] Presence: the server shows online or offline; protocol version check (refuse with a clear reason).
- [ ] Reconnection and message loss rules from 14.7.

## Tests

Every test must pass before the phase counts as done.

- [ ] Integration tests with the fake mod: hello, config sync, lobby, spawn, disconnect and reconnect.
- [ ] A bad token or version is refused; a user cannot receive events of a game they are not in.
- [ ] Message order and duplicates are handled (a test replays a duplicate).

## Done when

The app and the fake mod stay connected and exchange the first messages.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 40** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
