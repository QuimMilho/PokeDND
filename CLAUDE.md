# PokeDnD — how to work in this repository

- The canonical specification is `project_v2.md`. `project.md` is the original brief and must stay unchanged.
- The work is cut into small phases. `todo.md` lists them with checkboxes. To do work: open `todo.md`, take the first unticked phase whose dependencies are ticked, read its file in `phases/` completely, and follow it.
- Tick a phase in `todo.md` **only after it is added and every test in its prompt passes**. Update the progress line.
- **Do not run `git commit`** unless the user asks.
- Ask the user (AskUserQuestion) instead of assuming. Mark your own proposals *Suggested* and list them in Appendix D of `project_v2.md`.
- Write specs and documents in plain, human-readable language (examples, tables, short sentences).
- Generated files (`resources/`, `build/`, `vendor/`, build output) are git-ignored. Rebuild `resources/` with `node tools/extract.mjs`; never point `tools/port-posers.mjs` at `tools/ported-posers/` (it deletes its output folder first).

## How to build and test each area

| Area | Build and test | Notes |
|---|---|---|
| `Core/` | `cd Core && ./gradlew build`, then `./gradlew publishToMavenLocal` | Publish Core **before** building the API; the API builds against the jar in Maven local. Only Jackson and SLF4J are allowed as dependencies. The importer tests read `resources/` and `build/roster.json` (`node tools/extract.mjs`, `node tools/check-assets.mjs`) |
| `API/` | `cd API && ./gradlew test` (flavour: `-PdbFlavour=postgres\|mysql\|mariadb\|mongo`) | The tests tagged `database` need a database. On this machine start none with Testcontainers (it hangs on Docker Engine 29): set `QUARKUS_DATASOURCE_JDBC_URL`, `…_USERNAME`, `…_PASSWORD` for a PostgreSQL you started yourself (see `API/README.md`). **Only one Quarkus test run at a time** (they share a test port). A change that touches a repository must be written for all four databases: the SQL migrations in `db/migration/{postgresql,mysql,mariadb}`, the MongoDB adapter and its migration step, and the flavours must at least compile (`./gradlew compileJava compileTestJava -PdbFlavour=…`) |
| `App/` | `npm test`, `npm run build` (both languages), `npm run test:smoke`; end-to-end: build the API (`./gradlew quarkusBuild`) and the app, then `npx playwright test e2e/<file>` | A text in a template or in `$localize` needs a Portuguese translation: run `npm run extract-i18n`, then add the Portuguese units to `src/locale/messages.pt.xlf`. The build and a unit test fail when one is missing. Unit tests can switch language only for `$localize` calls in TypeScript, not for texts in templates |
| `tools/` | `node --test "tools/test/*.test.mjs"` | |

## Rules learned the hard way

- **Keep CI green and up to date.** The workflows are in `.github/workflows/` (one per area). When a phase adds files, tests or steps, change the matching workflow and its README **in the same phase**. A test that reads generated files (`resources/`, `build/`) must skip when they are missing (CI builds only some of them), or the workflow must build them. To find out why a run failed, follow "When a run fails" in `.github/workflows/README.md` (the run results are public; the logs are not).
- A REST resource class whose `@Path` is a prefix of another class's path hides it (`/api/v1/games` hides `/api/v1/games/{id}/…`). Put the routes of a game under `@Path("/api/v1/games/{id}")` in their own class, as `GameSettingsResource` and `GameTrainersResource` do.
- Every REST method needs `@Requires(Action…)`; add the new endpoints to `EndpointPermissionsTest`.
- When a decision is not in the specification, write it down as a numbered row in Appendix D of `project_v2.md` (and update the count under the table).
- The Bash tool can fail on long here-documents that contain quotes. Write files with the Write tool and run small scripts from the scratchpad.
