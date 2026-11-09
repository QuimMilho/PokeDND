# Phase 30 — API: Minecraft linking by code

| | |
|---|---|
| **Stage** | Accounts, servers, lobby, settings (M2) |
| **Area** | API |
| **Depends on** | Phase 22 (API: minimal accounts and roles) |
| **Spec** | chapter 3.2, chapter 16.4, chapter 18.1, chapter 17.2 (in `project_v2.md`) |

## Goal

The only way to connect a Minecraft account: a short code, typed on one side and shown on the other.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] Entity `McLink` (17.2): `mc_uuid` unique, so each Minecraft account belongs to one app account; one user may link several.
- [x] The code service for both directions, `POST /me/minecraft/link` and `POST /me/minecraft/link-code` for the app. **The two endpoints the mod calls (`POST /mod/link-code`, `POST /mod/link-complete`) need a server token, so phase 31 adds them** (decided with the owner on 2026-11-08); the service methods they will call, `codeForMinecraft` and `completeInMinecraft`, are built and tested here.
- [x] 8 characters without look-alikes, valid 10 minutes, single use, rate limited.
- [x] `GET /me/minecraft` and `DELETE /me/minecraft/{uuid}`.

## Tests

Every test must pass before the phase counts as done.

- [x] A valid code links once; reuse, expiry and a wrong code are refused.
- [x] Linking an account that already belongs to someone else is refused.
- [x] Codes contain no characters from the look-alike set (property test).

## Done when

Minecraft accounts can be linked and unlinked by code.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 30** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
