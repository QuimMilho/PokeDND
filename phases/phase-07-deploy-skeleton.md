# Phase 07 — Deploy skeleton

| | |
|---|---|
| **Stage** | Resources and foundations (M0) |
| **Area** | tools |
| **Depends on** | Phase 04 (API skeleton); Phase 05 (App skeleton) |
| **Spec** | chapter 13.5, chapter 20.7, chapter 20.8 (in `project_v2.md`) |

## Goal

Describe how the whole system is run on a server with Docker Compose, with safe defaults, so the first install is a copy-and-fill-in job.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Create `deploy/docker-compose.yml` with the services from 13.5: `caddy`, `app`, `api`, `db` (PostgreSQL by default, with profiles for MySQL, MariaDB and MongoDB), `mailpit` for development, and the optional `minecraft` profile.
- [ ] Write the `Caddyfile` (automatic HTTPS, proxy to the API, static files for the app, WebSocket pass-through).
- [ ] Write `deploy/.env.example` with every variable in the 13.5 table and a comment each. No real secrets.
- [ ] Write `Dockerfile`s for the API and the app (they can be minimal now; later phases refine them).
- [ ] Make sure `deploy/.env` is ignored and `.env.example` is tracked.

## Tests

Every test must pass before the phase counts as done.

- [ ] `docker compose -f deploy/docker-compose.yml config` succeeds with `.env.example` copied to `.env` (if Docker is missing, validate the YAML with a linter and say so).
- [ ] Each of the images builds (`docker compose build`), if Docker is available.
- [ ] `git check-ignore deploy/.env` says ignored; `git check-ignore deploy/.env.example` says not ignored.
- [ ] `.env.example` contains no real password, token or key.

## Done when

`docker compose config` is valid and the example environment file documents every variable.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 07** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
