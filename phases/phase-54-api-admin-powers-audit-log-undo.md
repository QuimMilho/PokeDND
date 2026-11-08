# Phase 54 — API: admin powers, audit log, undo

| | |
|---|---|
| **Stage** | Rules and combat in the App (M3) |
| **Area** | API |
| **Depends on** | Phase 53 (API: play endpoints and events) |
| **Spec** | chapter 12.4, chapter 12.5, chapter 7.16, chapter 16.2 (in `project_v2.md`) |

## Goal

Give the admin maximum freedom, with a record of everything.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Endpoints for each power in 12.4 (create, give and remove Pokémon and items, edit stats, move trainers, force events).
- [ ] Roll approvals and battle control (7.16): pause, skip, resolve manually.
- [ ] The audit log endpoint and undo (12.5) at the API level.

## Tests

Every test must pass before the phase counts as done.

- [ ] Each admin power is a test, and a non-admin is refused.
- [ ] Every admin action appears in the audit log with who, when and what.
- [ ] Undo through the API restores the exact earlier state.

## Done when

The admin can control and undo everything, with an audit trail.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 54** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
