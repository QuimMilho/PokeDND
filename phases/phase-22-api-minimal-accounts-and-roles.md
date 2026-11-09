# Phase 22 — API: minimal accounts and roles

| | |
|---|---|
| **Stage** | Data and cards (M1) |
| **Area** | API |
| **Depends on** | Phase 21 (API: database layer and reference data) |
| **Spec** | chapter 3.1, chapter 3.2, chapter 16.2, chapter 16.4, chapter 14.6 (in `project_v2.md`) |

## Goal

The first accounts: people can sign up and log in with email and password, and the API checks permissions by role.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] Entities `User` and `Identity` (17.2) and migrations.
- [x] Register, login, refresh, logout (`/auth/*`, 18.1) with Argon2id, a 15-minute signed access token and a rotating refresh token in an HTTP-only cookie (14.6).
- [x] The role model (site admin, server admin, player, spectator) and a permission check helper used by every endpoint (16.2).
- [x] Rate limits on login and sign-up. Email verification itself comes in Phase 29: for now accounts are active (a setting).

## Tests

Every test must pass before the phase counts as done.

- [x] Sign up, log in, refresh, log out: success and failure paths (wrong password, reused refresh token).
- [x] Passwords are stored only as Argon2id hashes (a test reads the table).
- [x] The permission table in 16.2 is a parametrised test: every role x endpoint pair.

## Done when

People can create accounts and log in; permissions are enforced.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 22** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
