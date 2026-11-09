# CI workflows

Each project has its own workflow. A workflow runs only when files of its project change (path filters), so a change in `App/` does not rebuild `Mod/`. Every workflow can also be started by hand (Actions tab → *Run workflow*).

| Workflow | Runs when these change | What it does |
|---|---|---|
| `tools.yml` | `tools/`, `deploy/`, README, CREDITS, CONTRIBUTING, LICENSE | Downloads the Cobblemon jar and checks its SHA-512, runs `tools/extract.mjs --no-icons` and `tools/check-resources.mjs --no-icons`; checks licence, credits and links (`check-docs`); checks `deploy/` (`check-deploy`, the settings registry against Appendix A, `docker compose config` for every database profile, both Caddyfiles). |
| `core.yml` | `Core/`, `cards/`, and the tools that build `resources/` and the roster | Rebuilds `resources/` (the importer tests read it) and writes `build/roster.json` with `check-assets` (they read that too), builds and tests Core with `POKEDND_REQUIRE_RESOURCES=true`, publishes it to Maven local, keeps the jars as an artifact. |
| `api.yml` | `API/`, `Core/`, `deploy/api.Dockerfile`, `project_v2.md` | Publishes Core to Maven local first, then builds and tests the API once per database (PostgreSQL, MySQL, MariaDB, MongoDB; Dev Services start each one), and builds the image of each database and asks it for `/q/health`. |
| `app.yml` | `App/` | `npm ci`, production build (English and Portuguese), unit tests in headless Chrome, smoke test. |
| `mod.yml` | `Mod/` | Builds the mod and keeps its jar as an artifact. |

The API tests that need the data bundle, the card art or the icons (`RealData`, the art and reference-data tests) **skip themselves** in CI, because `api.yml` builds none of those. Everything else, including the trainer and skin tests (the tests use a fake Mojang), runs on all four databases.

Icons are not rendered in CI (the slowest step). Render them locally with `node tools/extract.mjs` and check with `node tools/check-resources.mjs`.

## Rules for editing workflows

- **Pin every action to a full commit** and write the version as a comment, e.g. `actions/checkout@<40-character commit> # v7.0.1`. Look up a commit with `git ls-remote --tags https://github.com/<owner>/<action>.git`; for an annotated tag use the line that ends in `^{}`.
- Give every workflow `permissions: contents: read` and add more only to the job that needs it.
- Keep secrets out of the files. A step reads a secret as `${{ secrets.NAME }}`.

## Repository secrets

**None are needed today.** The workflows use no secrets and download only public files.

Needed later (create them in *Settings → Secrets and variables → Actions* when the phase arrives):

| Secret | Phase | Why |
|---|---|---|
| *(none: the built-in `GITHUB_TOKEN` with `packages: write`)* | 79 | Push the API and app images to `ghcr.io` and create the GitHub release with the mod jar. |
| `POKEDND_E2E_*` | 27, 41 | Only if the end-to-end tests ever need a real Discord or SMTP account. The plan is a test API with Mailpit, so probably none. |
| `MODRINTH_TOKEN` | 79, *optional* | Only if the mod jar is also published on Modrinth. Not in the plan. |
| `CODECOV_TOKEN` | *optional* | Only if coverage is uploaded. Not in the plan. |

Production secrets (database password, JWT keys, SMTP, VAPID, Discord) belong in `deploy/.env` on the server, **not** in GitHub.

## When a run fails

The logs of a run need a GitHub login, but the result of each job and step is public: `https://api.github.com/repos/QuimMilho/PokeDND/actions/runs?per_page=10`, then `…/actions/runs/<id>/jobs` (the step that failed has `conclusion: failure`). To see **why**, repeat what the workflow does on a clean copy of the commit, so that nothing generated on your machine hides the problem:

```sh
git clone . ../ci-copy && cp Cobblemon-fabric-*.jar ../ci-copy/    # the committed files only
cd ../ci-copy
node tools/extract.mjs --no-icons                                    # core.yml, tools.yml
node tools/check-assets.mjs --no-icons                               # core.yml: build/roster.json
(cd Core && POKEDND_REQUIRE_RESOURCES=true ./gradlew build publishToMavenLocal)
(cd API && ./gradlew build)                                          # needs a database: see API/README.md, Tests
```

Three failures found this way (2026-11-09) and their causes, so they are not made again:

- **`core`**: the importer tests read `build/roster.json`, which only `check-assets` writes. The workflow now runs it after `extract`.
- **`tools`**: the "real resources" tests of `check-assets` ran whenever `resources/icons` existed, but `extract --no-icons` still writes `icons/items` and `icons/tm`. They now look for the Pokémon icon folders (`icons/0001_…`) and skip without them.
- **`api`**: `CardDataFactoryTest.missingCardArtFallsBackToTheIconWithAWarning` copied a real icon before checking that the data exists. A test that needs generated files must skip first (`RealData.repository()`, `assumeTrue`).

**Rule:** when a phase adds a test that reads generated files (`resources/`, `build/`), make it skip when they are missing (and fail instead when `POKEDND_REQUIRE_RESOURCES=true`), or add the step that builds them to the workflow.
