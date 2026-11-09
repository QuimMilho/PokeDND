# Phase 21 — API: database layer and reference data

| | |
|---|---|
| **Stage** | Data and cards (M1) |
| **Area** | API |
| **Depends on** | Phase 04 (API skeleton); Phase 14 (Importer: learnsets, derived values, bundle) |
| **Spec** | chapter 16.3, chapter 17.1, chapter 17.2, chapter 18.1 (in `project_v2.md`) |

## Goal

Give the API a database and let it serve the Pokémon reference data (species, moves, items) to the app.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] Repository interfaces in the domain layer, and the SQL adapter with Hibernate ORM + Panache (16.3).
- [x] Flyway migrations for the reference-data tables; flexible data as JSON columns.
- [x] A loader that reads the data bundle and fills the reference tables (idempotent: the manifest hash decides).
- [x] Read-only endpoints for species, forms, moves, items, types (see 18.1 for names).

## Tests

Every test must pass before the phase counts as done.

- [x] Integration tests against PostgreSQL with Testcontainers (skip with a clear message only if Docker is missing).
- [x] The loader run twice does not duplicate rows; a changed hash reloads.
- [x] Each endpoint returns the expected shape for a few known species.

## Notes from the work

- **Reference data in tables.** Chapter 17.1 used to say reference data is not stored in the database; the owner decided (phase 21) that it is loaded into tables (a cache of the bundle), and 17.1 now says so.
- **Testcontainers.** The tests use Quarkus Dev Services (Testcontainers). On Docker Engine 29 the container start hangs, so they were run against a PostgreSQL started by hand and pointed at with `QUARKUS_DATASOURCE_JDBC_URL` (see `API/README.md`, Appendix D #50). The tests themselves are the same either way.

## Done when

The API serves reference data from PostgreSQL.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 21** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
