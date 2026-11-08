# Phase 81 — Hand-fix the 19 partial posers

| | |
|---|---|
| **Stage** | Asset track (runs in parallel, optional) |
| **Area** | Assets |
| **Depends on** | Phase 01 (Regenerate all resources) |
| **Spec** | chapter 15.3, Appendix C (in `project_v2.md`) |

## Goal

Close the small gaps listed in Appendix C.3 so those species move as well as the others.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

**Ask the user first:**

- How should hand-fixed posers be marked so the port tool leaves them alone?

## Tasks

- [ ] Read `build/reports/posers-port.md` for the exact list and the Kotlin source of each (in `vendor/cobblemon-src`).
- [ ] For each of the 19, decide what the best JSON equivalent is (a bedrock animation, a function the renderer will implement, or leave out) and edit the file in `tools/ported-posers/`.
- [ ] Protect hand fixes: `tools/port-posers.mjs` rewrites its output folder, so decide how it should skip fixed files (for example a list of hand-fixed names), and implement that.
- [ ] Update Appendix C.3 and the counts.

## Tests

Every test must pass before the phase counts as done.

- [ ] The bone and animation checks of `tools/port-posers.mjs` pass for every fixed poser.
- [ ] Icons render without errors for all 19 species.
- [ ] Running `port-posers.mjs` again does not overwrite a hand-fixed file (test it on a copy).

## Done when

The partial posers are completed and protected from being overwritten.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 81** in `todo.md`, and only now: the phase is added **and** tested. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
