# Phase 04 — API skeleton

| | |
|---|---|
| **Stage** | Resources and foundations (M0) |
| **Area** | API |
| **Depends on** | Phase 03 (Core library skeleton) |
| **Spec** | chapter 13.3, chapter 14.1, chapter 14.8, chapter 16.1, chapter 16.7 (in `project_v2.md`) |

## Goal

Create the API project so that later phases can add features to a working service: it starts, answers `/q/health`, and depends on the Core library.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

**Ask the user first:**

- Is Docker available, and which Quarkus version should be pinned?

## Tasks

- [ ] Check whether Docker is available on this machine (Testcontainers and Dev Services need it). If not, tell the user and agree how tests will run.
- [ ] Create `API/` as a Gradle project with Quarkus (the newest stable version at the time; record it), with the same wrapper files and Java 25 toolchain as `Core/`. Extensions: REST with Jackson, health, metrics, scheduler. WebSockets Next comes in a later phase.
- [ ] Declare the dependency on `net.quimmilho.pokednd:core` from Maven local.
- [ ] Add the PostgreSQL profile in `application.properties` (the connection settings are read from environment variables, see 13.5).
- [ ] Add a `@QuarkusTest` that checks `/q/health` answers UP, and one that checks `/q/metrics` answers.
- [ ] Add `API/` build output to `.gitignore` if it is not covered.

## Tests

Every test must pass before the phase counts as done.

- [ ] `./gradlew build` passes in `API/` including the two tests.
- [ ] Start the API (`./gradlew quarkusDev` or the built jar) and `curl http://localhost:8080/q/health` returns UP; stop it afterwards.
- [ ] The API jar contains the Core classes (or resolves them), proven by a test that calls the sample Core class.

## Done when

The API starts, reports healthy, and builds in one command.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 04** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
