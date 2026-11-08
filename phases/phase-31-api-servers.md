# Phase 31 — API: servers

| | |
|---|---|
| **Stage** | Accounts, servers, lobby, settings (M2) |
| **Area** | API |
| **Depends on** | Phase 22 (API: minimal accounts and roles) |
| **Spec** | chapter 3.1, chapter 14.6, chapter 16.1, chapter 16.2, chapter 18.1 (in `project_v2.md`) |

## Goal

A Minecraft server can be registered and trusted by the API, and has admins.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Entities and endpoints to register a server (a site admin does it), list servers, and assign server admins.
- [ ] Server token: 256-bit random, only a hash stored, shown once, rotatable, bound to one server (14.6).
- [ ] Online status is stored here and updated by the realtime phase; add the field and the endpoint now.

## Tests

Every test must pass before the phase counts as done.

- [ ] Only a site admin can register; a server admin can manage only their own server (permission table, 16.2).
- [ ] The token is shown once; after rotation the old one fails.
- [ ] The database never contains the token itself (a test reads the table).

## Done when

Servers can be registered, secured and administered.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 31** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
