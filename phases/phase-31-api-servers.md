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

- [x] Entities and endpoints to register a server (a site admin does it), list servers, and assign server admins.
- [x] Server token: 256-bit random, only a hash stored, shown once, rotatable, bound to one server (14.6).
- [x] Online status is stored here and updated by the realtime phase; add the field and the endpoint now.
- [x] The two mod endpoints of the Minecraft linking, left over from phase 30 because they need the server token: `POST /mod/link-code` (body: Minecraft id and name; answers a code made by `MinecraftLinkService.codeForMinecraft`) and `POST /mod/link-complete` (body: the code, the Minecraft id and name; calls `completeInMinecraft`). Both are authenticated by the server token only (18.1).

## Tests

Every test must pass before the phase counts as done.

- [x] Only a site admin can register; a server admin can manage only their own server (permission table, 16.2).
- [x] The token is shown once; after rotation the old one fails.
- [x] The database never contains the token itself (a test reads the table).
- [x] The two mod linking endpoints refuse a missing, wrong or rotated server token, and work with a good one (a code made through `/mod/link-code` links an app account; a code made in the app is completed through `/mod/link-complete`).

## Done when

Servers can be registered, secured and administered.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 31** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.

## Notes from doing the phase

- The mod authenticates with `Authorization: Bearer pds_…` on endpoints marked `@Requires(Action.SERVER_TOKEN)`; `AuthFilters` checks the token. A wrong, missing or rotated token gives `401 INVALID_SERVER_TOKEN`.
- Suggested additions are in Appendix D #64 and #65: `GET /me/servers`, and the token format.
- A server with games cannot be deleted (phase 32 adds the games); removing a server admin also removes them as co-admin of that server's games.
- Tests: `servers/ServersTest`, `servers/ModLinkEndpointsTest`, and new rows in `PermissionsTest` and `EndpointPermissionsTest`.
