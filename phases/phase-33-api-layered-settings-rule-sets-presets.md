# Phase 33 — API: layered settings, rule sets, presets

| | |
|---|---|
| **Stage** | Accounts, servers, lobby, settings (M2) |
| **Area** | API |
| **Depends on** | Phase 32 (API: games) |
| **Spec** | chapter 12.1, chapter 12.2, chapter 12.3, chapter 12.6, Appendix A (in `project_v2.md`) |

## Goal

Every rule of the game is a setting with a default, and the admin can change all of them per game.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] A registry of all keys of Appendix A: type, default, allowed values, chapter. Generate it from `project_v2.md` Appendix A with a script so the two cannot drift, and commit the result.
- [x] Layering (12.2): defaults, rule set, game; the effective value and where it came from.
- [x] Rule sets (12.3) and presets (12.1) as bundles of settings; endpoints to read and change settings with validation.
- [x] Every change goes to the audit log (the log itself is finished later).

## Tests

Every test must pass before the phase counts as done.

- [x] The registry has exactly the keys of Appendix A, with the same defaults (a test parses the document).
- [x] Layering: a game value beats a rule-set value beats the default.
- [x] Invalid values are refused with a clear message; presets apply and can be undone.

## Done when

All settings exist with defaults and can be layered and changed.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 33** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
