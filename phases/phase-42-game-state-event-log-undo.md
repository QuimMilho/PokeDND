# Phase 42 — Game state, event log, undo

| | |
|---|---|
| **Stage** | Rules and combat in the App (M3) |
| **Area** | Core |
| **Depends on** | Phase 41 (M2 end-to-end test) |
| **Spec** | chapter 14.5, chapter 12.5, chapter 7.16 (in `project_v2.md`) |

## Goal

The foundation of the rules engine: state is rebuilt from events, so battles can be replayed, audited and undone exactly.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Event types, a state reducer, snapshots and the log format (14.5).
- [ ] Undo (12.5): it returns to the exact previous state, including rolls and random choices already made.
- [ ] An audit log entry for every event, with who did it.

## Tests

Every test must pass before the phase counts as done.

- [ ] Property test: replaying any generated event list gives the same state each time.
- [ ] Undo n steps then redo equals the original state.
- [ ] Serialising and loading a snapshot is lossless.

## Done when

State is event-sourced, replayable and undoable.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 42** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
