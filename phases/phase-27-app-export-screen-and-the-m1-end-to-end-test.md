# Phase 27 — App: export screen and the M1 end-to-end test

| | |
|---|---|
| **Stage** | Data and cards (M1) |
| **Area** | App |
| **Depends on** | Phase 26 (App: card browser); Phase 24 (API: card export endpoints) |
| **Spec** | chapter 9.6, chapter 20.4 (in `project_v2.md`) |

## Goal

Finish milestone M1: from the app, export a print-ready PDF of Generation 1 cards.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] The export screen: pick by generation, type or by hand; options for size, paper, bleed, gap and backs; start the job, show progress, download.
- [x] Set up Playwright with a real API and database: the stack (`App/e2e/stack.mjs`) makes a new, empty database inside a PostgreSQL that is already running, starts the built API jar and a web server that serves the app and passes `/api` to the API. *(Changed from "docker compose or Testcontainers": starting a new container from the test hangs on Docker Engine 29, Appendix D #50 and #55.)*
- [x] Write the end-to-end test: register, log in, export Generation 1 with backs, download the PDF.

## Tests

Every test must pass before the phase counts as done.

- [x] The Playwright test passes from a clean database (a new empty database for every run).
- [x] The downloaded PDF is valid, has the expected page count, and its QR codes decode (checked inside the test).
- [x] All unit tests of the earlier M1 phases still pass.

## Done when

A logged-in user exports Generation 1 species cards, with backs, as a print-ready PDF, in an automated test.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 27** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
