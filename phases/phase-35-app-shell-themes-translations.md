# Phase 35 — App: shell, themes, translations

| | |
|---|---|
| **Stage** | Accounts, servers, lobby, settings (M2) |
| **Area** | App |
| **Depends on** | Phase 25 (App: login and API client) |
| **Spec** | chapter 11.2, chapter 11.7, chapter 11.8 (in `project_v2.md`) |

## Goal

The frame of the app: menus, themes and language, built for phones first.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] Navigation for each role (11.2), responsive layout, and the themes `system`, `light`, `dark`.
- [x] The account page: language, theme, sessions, linked Minecraft accounts (with the link-by-code form) and the Discord link.
- [x] A "Sign in with Discord" button on the login screen.
- [x] A test that fails if any translation key exists in English but not in Portuguese, or the other way round.

## Tests

Every test must pass before the phase counts as done.

- [x] Unit tests for navigation by role and the theme switch.
- [x] The translation completeness test passes (and fails when one key is removed from a copy).
- [x] Playwright smoke: change language and theme, link a Minecraft account with a code from the fake mod or the API.

## Done when

The app has its frame, themes, account page and complete translations.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 35** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
