# Phase 82 — Community model packs: licence review

| | |
|---|---|
| **Stage** | Asset track (runs in parallel, optional) |
| **Area** | Assets |
| **Depends on** | Phase 01 (Regenerate all resources) |
| **Spec** | chapter 21.1, chapter 21.2, Appendix C (in `project_v2.md`) |

## Goal

Find out, in writing, which existing community models could be used legally, before anyone downloads or includes anything.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

**Ask the user first:**

- Which candidates, if any, should be added to the project?

## Tasks

- [ ] List candidate packs for the 120 missing species (Appendix C.1) and note for each: author, licence, what it covers, link.
- [ ] A pack is only usable if its licence is compatible with CC BY-NC (credit the authors, keep the licence files, label as modified). Mark each candidate usable, unusable or unclear.
- [ ] Write the result in `docs/model-packs.md`. Do not download or add any model without the user's decision.

## Tests

Every test must pass before the phase counts as done.

- [ ] Every candidate has author, licence text or link, and a verdict.
- [ ] The user has read the list and chosen which to pursue.

## Done when

There is a reviewed list of usable and unusable model sources.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 82** in `todo.md`, and only now: the phase is added **and** tested. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
