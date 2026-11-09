# Phase 39 — tools/fake-mod

| | |
|---|---|
| **Stage** | Accounts, servers, lobby, settings (M2) |
| **Area** | tools |
| **Depends on** | Phase 31 (API: servers) |
| **Spec** | chapter 14.3, chapter 14.4, chapter 18.2, chapter 18.3, chapter 18.5 (in `project_v2.md`) |

## Goal

A stand-in for the Minecraft mod. Every automated test of the API and app that needs "the mod" uses this program.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] Write `tools/fake-mod/` in Node (the built-in WebSocket client of Node 22 is enough; no dependencies). It connects with a server token, sends `hello`, and handles the commands in 18.5.
- [x] Behaviours: show the join code, spawn and remove fake players, confirm skins, accept walk commands and report arrival after a delay, answer config sync.
- [x] A scriptable mode (a JSON scenario) for tests and an interactive mode that prints what it receives.
- [x] Document the protocol version it speaks.

## Tests

Every test must pass before the phase counts as done.

- [x] Unit tests for each message handler.
- [x] A test against a small stub server proves the hello and a spawn/walk/arrive cycle.
- [x] It refuses to run against an incompatible protocol version with a clear message.

## Done when

The fake mod speaks the protocol and can be scripted.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 39** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
