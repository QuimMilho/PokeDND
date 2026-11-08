# Phase 34 — API: database flavours

| | |
|---|---|
| **Stage** | Accounts, servers, lobby, settings (M2) |
| **Area** | API |
| **Depends on** | Phase 33 (API: layered settings, rule sets, presets) |
| **Spec** | chapter 16.3, chapter 13.5, chapter 20.3 (in `project_v2.md`) |

## Goal

The database is chosen by configuration: PostgreSQL, MySQL, MariaDB or MongoDB. The same code and the same tests must work on each.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] MySQL and MariaDB through the SQL adapter: dialect differences, Flyway folders per dialect where needed, JSON columns.
- [ ] The MongoDB adapter behind the repository interfaces (MongoDB with Panache) and versioned migration scripts.
- [ ] Build one API image per flavour: `-postgres`, `-mysql`, `-mariadb`, `-mongo` (Quarkus fixes the database kind at build time).
- [ ] Run the whole API test suite once per flavour with Testcontainers; make the flavour a test parameter.
- [ ] Add the compose profiles for each database in `deploy/`.

## Tests

Every test must pass before the phase counts as done.

- [ ] The full API integration suite passes on all four flavours.
- [ ] Each image starts and answers `/q/health` (if Docker is available).
- [ ] Switching flavour needs only configuration (a test starts each image with its own environment).

## Done when

The API suite is green on PostgreSQL, MySQL, MariaDB and MongoDB.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 34** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
