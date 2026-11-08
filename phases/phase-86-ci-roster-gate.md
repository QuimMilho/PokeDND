# Phase 86 — CI roster gate

| | |
|---|---|
| **Stage** | Asset track (runs in parallel, optional) |
| **Area** | tools |
| **Depends on** | Phase 09 (check-assets tool and the roster); Phase 85 (New species: icons, card art, roster) |
| **Spec** | chapter 13.6, Appendix C (in `project_v2.md`) |

## Goal

Make it impossible to list an incomplete species as playable.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Run `tools/check-assets.mjs --strict` in the tools CI workflow after the resources are rebuilt.
- [ ] Make the failure message name the species and the missing piece.
- [ ] Document how to fix a failing gate in a comment in the workflow: finish the species, or remove it from the roster.

## Tests

Every test must pass before the phase counts as done.

- [ ] The workflow passes on the current repository.
- [ ] Removing a texture from a copy of one roster species makes the step fail with a clear message.
- [ ] After the incomplete species is removed from the roster, the step passes again (the fix path works).

## Done when

The roster is guarded by CI.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 86** in `todo.md`, and only now: the phase is added **and** tested. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
