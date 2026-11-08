# Phase 78 — Documentation and runbook

| | |
|---|---|
| **Stage** | Polish and release (M5) |
| **Area** | Docs |
| **Depends on** | Phase 73 (Mod tests and the M4 end-to-end session) |
| **Spec** | chapter 20.7, chapter 20.8, chapter 2, chapter 13.5 (in `project_v2.md`) |

## Goal

Someone who did not build this can install it and run a game by following the documents.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Write the getting-started guide for admins (install, register the server, create a game) and for players (join, make a trainer) in plain language, with screenshots.
- [ ] Follow the runbook of 20.7 step by step on a clean machine or VM; fix every place where it was wrong or unclear.
- [ ] Write the backup, upgrade and monitoring notes (20.8: nightly dump, volume copy, `/q/health`, `/q/metrics`).
- [ ] List the settings a first-time admin should know (the presets).

## Tests

Every test must pass before the phase counts as done.

- [ ] The runbook worked unchanged on a clean machine (record the date and the machine).
- [ ] Every link and command in the documents was checked.
- [ ] A restore from a backup was done once and the game came back.

## Done when

The documents are tested by following them.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 78** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
