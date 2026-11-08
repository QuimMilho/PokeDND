# Phase NN — Title

This is the structure every phase prompt follows, so any of them can be read cold. Copy it when a new phase is added, and add the phase to `todo.md` (with its checkbox) at the same time.

| | |
|---|---|
| **Stage** | Which stage of `todo.md` it belongs to |
| **Area** | tools, Mod, Core, API, App, Docs or Assets |
| **Depends on** | The phases that must be ticked first, or "none" |
| **Spec** | The chapters of `project_v2.md` to read |

## Goal

Two or three plain sentences: what exists after this phase that did not exist before, and why it matters.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md`.
4. Look at what the earlier phases built. If this prompt no longer matches reality, update this file first and tell the user what you changed.
5. If something is not decided, ask the user (AskUserQuestion) instead of assuming. Mark proposals of your own as *Suggested* and add them to Appendix D.

(Optional blocks: **Also read** and **Ask the user first**.)

## Tasks

- [ ] One small, concrete step per line (3 to 8 of them).

## Tests

Every test must pass before the phase counts as done.

- [ ] One checkable statement per line, with the command or the expected number where there is one.

## Done when

One sentence that a person can check.

## After the phase

1. Tick every task and test above in this file.
2. Tick the phase in `todo.md`, and only now: the phase is added **and** tested. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated to `.gitignore`.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, what is open.

## What "small" means

One working session, one area, and it ends with something that runs and is tested. If a phase grows beyond that, split it and add the new phase to `todo.md`.
