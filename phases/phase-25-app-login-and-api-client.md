# Phase 25 — App: login and API client

| | |
|---|---|
| **Stage** | Data and cards (M1) |
| **Area** | App |
| **Depends on** | Phase 05 (App skeleton); Phase 22 (API: minimal accounts and roles) |
| **Spec** | chapter 11.2, chapter 11.3, chapter 14.6 (in `project_v2.md`) |

## Goal

The app can sign people in and talks to the API safely.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] Login, register and logout screens using Angular Material, in English and Portuguese.
- [x] An API client service with the access token in memory, silent refresh through the cookie, and a clear "signed out" state.
- [x] Route guards (public, signed in) and a simple signed-in home screen.

## Tests

Every test must pass before the phase counts as done.

- [x] Unit tests for the client (refresh on 401, one retry) and the guards.
- [x] Component tests for the forms (validation messages in both languages).
- [x] A manual or Playwright smoke test against the running API: register, log in, log out.

## Done when

A person can register and log in from the app.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 25** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
