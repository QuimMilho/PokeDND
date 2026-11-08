# Phase 59 — M3 full-game test and first play-test

| | |
|---|---|
| **Stage** | Rules and combat in the App (M3) |
| **Area** | tools |
| **Depends on** | Phase 56 (App: admin panel); Phase 57 (App: cards in play); Phase 58 (App: 2D virtual map) |
| **Spec** | chapter 2, chapter 20.1, chapter 20.6 (in `project_v2.md`) |

## Goal

Finish milestone M3: the example session of chapter 2 works in the app, in a test and with real people.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

**Ask the user first:**

- Who will take part in the play-test, and when?

## Tasks

- [ ] Write the scripted full-game test with the fake mod that follows the steps of chapter 2 as far as they do not need Minecraft.
- [ ] Run it in CI.
- [ ] Prepare and run a short play-test with real people (the checklist in 20.6); write down the surprises in a new file `playtests/M3.md`.
- [ ] Turn the findings into small follow-up tasks (ask the user what to do with them).

## Tests

Every test must pass before the phase counts as done.

- [ ] The scripted test passes three times in a row from a clean start.
- [ ] All earlier tests are green.
- [ ] The play-test notes exist and answer the seven questions of 20.6.

## Done when

The scripted game passes and a real group played a session in the app.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 59** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
