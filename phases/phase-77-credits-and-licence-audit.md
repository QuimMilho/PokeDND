# Phase 77 — Credits and licence audit

| | |
|---|---|
| **Stage** | Polish and release (M5) |
| **Area** | Docs |
| **Depends on** | Phase 59 (M3 full-game test and first play-test) |
| **Spec** | chapter 21.1, chapter 21.2, chapter 21.3 (in `project_v2.md`) |

## Goal

Before anything is published, every legal and credit requirement is met and checkable.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

**Ask the user first:**

- Will you ask the Cobblemon team, and where (their Discord)?

## Tasks

- [ ] A script that checks: the MPL notice in every source file, the credit line in the PDF footer, the About page, the mod credits screen, README, CREDITS, and a licence file in every model folder.
- [ ] Every derived asset is labelled modified (icons, card art, converted posers, PDFs).
- [ ] No ads, payments or paywalled content anywhere (21.1); check donation wording if any.
- [ ] Draft a short message to the Cobblemon team asking whether the credit wording is enough; the user sends it.

## Tests

Every test must pass before the phase counts as done.

- [ ] The audit script passes, and fails when a notice is removed from a copy of a file.
- [ ] A sample card PDF contains the footer line.
- [ ] The draft message exists and the user has seen it.

## Done when

Credits and notices are complete and checked by a script.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 77** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
