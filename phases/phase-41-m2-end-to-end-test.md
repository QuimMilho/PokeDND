# Phase 41 — M2 end-to-end test

| | |
|---|---|
| **Stage** | Accounts, servers, lobby, settings (M2) |
| **Area** | tools |
| **Depends on** | Phase 40 (API: realtime channel and server protocol); Phase 38 (App: trainer wizard); Phase 34 (API: database flavours) |
| **Spec** | chapter 2, chapter 20.1, chapter 20.3 (in `project_v2.md`) |

## Goal

Finish milestone M2 with a test that runs the whole flow from a clean start.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Start database, API, app and the fake mod from a script (compose or Testcontainers).
- [ ] Write the Playwright test for the flow in the title, checking what the fake mod received.
- [ ] Run it in CI (add it to the workflows).

## Tests

Every test must pass before the phase counts as done.

- [ ] The test passes from a clean database, three times in a row.
- [ ] All earlier unit and integration tests still pass.
- [ ] The fake mod log shows a spawn command for each of the three trainers.

## Done when

The M2 flow passes automatically.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 41** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
