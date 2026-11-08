# Phase 24 — API: card export endpoints

| | |
|---|---|
| **Stage** | Data and cards (M1) |
| **Area** | API |
| **Depends on** | Phase 23 (API: card layout and PDF engine); Phase 22 (API: minimal accounts and roles); Phase 21 (API: database layer and reference data) |
| **Spec** | chapter 9.6, chapter 16.5, chapter 18.1, chapter 9.10 (in `project_v2.md`) |

## Goal

Let a logged-in user ask for a card export and download the PDF later.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Endpoints to start an export job (selection by generation, type, or an explicit list; with or without backs; size and paper options) and to poll and download it.
- [ ] Enforce `cards.export.maxCardsPerJob` (1,500) and `cards.export.fileTtlHours` (24); a scheduler deletes expired files.
- [ ] Any logged-in user may export species cards (16.2).

## Tests

Every test must pass before the phase counts as done.

- [ ] A job for Generation 1 gives a valid PDF with the right number of pages.
- [ ] A job over the limit is refused with a clear message.
- [ ] An expired file is gone and answers 404; anonymous users are refused.

## Done when

The API exports card sets as downloadable PDFs.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 24** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
