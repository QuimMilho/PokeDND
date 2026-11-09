# Phase 28 — API: Discord sign-in and account linking

| | |
|---|---|
| **Stage** | Accounts, servers, lobby, settings (M2) |
| **Area** | API |
| **Depends on** | Phase 22 (API: minimal accounts and roles) |
| **Spec** | chapter 3.2, chapter 16.4, chapter 14.6, chapter 18.1 (in `project_v2.md`) |

## Goal

Let people sign in with Discord as well as email and password. Discord is the only third-party login.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

**Ask the user first:**

- If a Discord account has the same email as an existing account, link them automatically or refuse?

## Tasks

- [x] Write a small OAuth 2 authorization-code client for Discord (it has no discovery document). Use PKCE only if Discord supports it for this kind of app; check and write down the answer. **Answer (2026-11-08): Discord documents no PKCE for this grant, so none is used** (14.6).
- [x] Endpoints `GET /auth/oauth/discord/start` and `/callback`, with a random `state` value checked on return. The client id and secret come from `POKEDND_OAUTH_DISCORD_*` (13.5).
- [x] Create or link the `Identity` row (`provider` = `discord`). Decided with the owner: a Discord account with the same verified email as an existing account is **linked automatically** (an account that never verified its address loses its password, see Appendix D #57).
- [x] A signed-in user can link and unlink Discord from their profile; the last remaining way to log in cannot be removed.

## Tests

Every test must pass before the phase counts as done.

- [x] Integration tests against a stub Discord server: new user, returning user, linking, wrong `state`, denied consent.
- [x] Unlinking the last login method is refused.
- [x] No client secret appears in logs (a test captures the log output).

## Done when

Discord sign-in works against a stub, and accounts can be linked and unlinked.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 28** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
