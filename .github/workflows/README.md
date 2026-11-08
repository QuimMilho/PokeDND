# CI workflows

Each project has its own workflow. A workflow runs only when files of its project change (path filters), so a change in `App/` does not rebuild `Mod/`. Every workflow can also be started by hand (Actions tab → *Run workflow*).

| Workflow | Runs when these change | What it does |
|---|---|---|
| `tools.yml` | `tools/`, `deploy/`, README, CREDITS, CONTRIBUTING, LICENSE | Downloads the Cobblemon jar and checks its SHA-512, runs `tools/extract.mjs --no-icons` and `tools/check-resources.mjs --no-icons`; checks licence, credits and links (`check-docs`); checks `deploy/` (`check-deploy`, `docker compose config` for every database profile, both Caddyfiles). |
| `core.yml` | `Core/`, and the tools that build `resources/` | Rebuilds `resources/` (the importer tests read it), builds and tests Core, publishes it to Maven local, keeps the jars as an artifact. |
| `api.yml` | `API/`, `Core/` | Publishes Core to Maven local first, then builds and tests the API. |
| `app.yml` | `App/` | `npm ci`, production build (English and Portuguese), unit tests in headless Chrome, smoke test. |
| `mod.yml` | `Mod/` | Builds the mod and keeps its jar as an artifact. |

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
