# Phase 80 — Real-group play-tests and fixes

| | |
|---|---|
| **Stage** | Polish and release (M5) |
| **Area** | Docs |
| **Depends on** | Phase 79 (Release pipeline); Phase 76 (Accessibility and performance); Phase 77 (Credits and licence audit); Phase 75 (Push notifications); Phase 73 (Mod tests and the M4 end-to-end session) |
| **Spec** | chapter 20.6, chapter 2 (in `project_v2.md`) |

## Goal

Find out whether the game is fun and understandable, and fix the worst problems before calling it finished.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

**Ask the user first:**

- Which findings are "fix now"?

## Tasks

- [ ] Run one or more real sessions; use the seven questions of 20.6 and write down surprises in `playtests/`.
- [ ] Triage the findings with the user into: fix now, later, never.
- [ ] Fix the "now" items in small steps; tune the settings that make rounds too long or damage too swingy (Risks in chapter 22).

## Tests

Every test must pass before the phase counts as done.

- [ ] The play-test notes answer all seven questions.
- [ ] Every "fix now" item has a fix and a regression test where it makes sense.
- [ ] All test suites are green after the fixes.

## Done when

A real group played, the findings were handled, and the product is ready to release.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 80** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
