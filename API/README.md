# API — the PokeDnD server

A Quarkus service (REST today; WebSocket, accounts, games and the rules engine come later). It is the only authority in a game: the app and the Minecraft mod both talk to it.

Spec: [`project_v2.md`](../project_v2.md), chapters 13.3, 13.5 and 16.

## Status

What exists (phases 21 to 32):

| Area | What |
|---|---|
| Database | PostgreSQL through Hibernate ORM with Panache (the SQL adapter) behind repository interfaces in `domain/`; Flyway migrations in `src/main/resources/db/migration/postgresql`. The other flavours come in phase 34. |
| Reference data | `GET /api/v1/data/…`: species, moves, items, abilities, types, natures, learnsets, roster. Loaded from the data bundle at start; the manifest hash decides whether anything is reloaded. `POST /api/v1/admin/data/reimport` (site admin) reloads it. |
| Accounts | `POST /api/v1/auth/register · login · refresh · logout`, `GET /api/v1/me`. Argon2id passwords, 15-minute RS256 access tokens, rotating refresh tokens in an HTTP-only cookie, rate limits. Every endpoint says what it needs with `@Requires(Action)` (the table of chapter 16.2 is in `auth/Permissions.java`). |
| E-mail and sessions | `POST /api/v1/auth/verify-email · resend-verification · forgot · reset`: single-use links (24 hours to verify, 1 hour to reset) sent over SMTP in English or Portuguese. With `pokednd.auth.require-email-verification` on (the default) a password account cannot log in before it is verified. A reset ends every login. `GET /api/v1/me/sessions`, `DELETE /api/v1/me/sessions/{id}` list and end logins. |
| Discord | `GET /api/v1/auth/oauth/providers · {provider}/start · {provider}/callback`: a small OAuth 2 authorization-code client (no PKCE, Discord documents none). `GET /api/v1/me/identities`, `POST …/discord/link`, `DELETE …/discord` link and unlink; the last way to log in cannot be removed. |
| Minecraft linking | `GET /api/v1/me/minecraft`, `POST …/link` (type the code the mod printed), `POST …/link-code` (the app shows a code), `DELETE …/{uuid}`. 8-character codes without look-alikes, 10 minutes, single use. The mod's side is `POST /api/v1/mod/link-code` (body: Minecraft id and name; answers a code) and `POST /api/v1/mod/link-complete` (body: the code, Minecraft id and name); both need a server token. |
| Servers | A site admin registers a server with `POST /api/v1/servers` and gets its token **once** (`pds_…`, 256 bits; only the SHA-256 hash is stored). `POST …/{id}/token/rotate` makes a new token and the old one stops working. `PUT/DELETE …/{id}/admins/{userId}` assign server admins; `GET /api/v1/me/servers` lists the servers a person administers; `GET …/{id}/status` (site admin or that server's admins) shows online state and versions, which the realtime channel will keep up to date. The mod authenticates with `Authorization: Bearer <server token>` on endpoints marked `@Requires(Action.SERVER_TOKEN)`. |
| Games | `POST /api/v1/games` (site admin or an admin of that server), `GET /api/v1/games`, `GET/PATCH/DELETE …/{id}`. The state machine of chapter 3.4 (draft, lobby, running, paused, ended, archived) is in `domain/games/GameState.java`; `POST …/{id}/lobby/open · lobby/close · start · pause · resume · end` move a game along it. One server has one active game (lobby, running or paused). A lobby has a 6-character join code (no look-alikes) that works only while the lobby is open; `POST /api/v1/games/join` asks to join, `GET …/requests` and `POST …/requests/{rid}/accept · reject` answer. `GET …/members`, `DELETE …/members/{userId}`, and `PUT/DELETE …/admins/{userId}` (the owner adds co-admins from the same server). Settings, rule sets and the log come in later phases. |
| Cards | `cards/` is the card engine (data, layout, PDFBox drawing, ZXing QR codes; deterministic output). `POST /api/v1/cards/exports` starts a job (the request can also set `bleedMm` and `gapMm`), `GET …/{id}` shows its progress, `…/download` gives the PDF and `…/manifest` the CSV. Finished files are deleted after 24 hours. |
| Card pictures | `GET /api/v1/art/{folder}/{file}`: the card art of a species for the app's preview (the 128 px icon with `?size=icon`, or when there is no card art). Public. |
| Misc | `GET /q/health`, `GET /q/metrics`, `GET /api/core-version`. |

## Build and run

Needs Java 25, **Core published to Maven local first**, and the **data bundle and resources** (they are generated, not in git):

```sh
node tools/extract.mjs && node tools/check-assets.mjs     # from the repository root: resources/ and build/roster.json
cd Core && ./gradlew publishToMavenLocal importBundle      # the Core library and ../build/bundle
cd ../API
./gradlew build           # compile and test (the tests need Docker or a PostgreSQL, see below)
./gradlew quarkusDev      # run with live reload, then open http://localhost:8080/q/health
```

On Windows PowerShell use `.\gradlew` instead of `./gradlew`. In dev mode, Quarkus starts a throw-away PostgreSQL for you when Docker is running and no database URL is set.

## Tests

`./gradlew test` runs plain unit tests (permissions table, Argon2id, rate limiter, PEM keys, the PDF engine with QR decoding and page counts, golden files of real cards) and tests that start the API (tagged `database`).

- The mail and Discord tests need nothing outside the test: a small fake SMTP server and a fake Discord (`FakeSmtpServer`, `FakeDiscord`, started by `FakeServices`) are started in the test JVM.
- **The `database` tests need PostgreSQL.** Quarkus Dev Services starts one with Testcontainers if Docker is available. Without Docker they are skipped with a warning. To use your own database instead (needed with **Docker Engine 29**, where Testcontainers hangs, see Appendix D #50 of the specification), set `QUARKUS_DATASOURCE_JDBC_URL`, `QUARKUS_DATASOURCE_USERNAME` and `QUARKUS_DATASOURCE_PASSWORD` first; use an empty throw-away database, for example `docker run -d -p 55432:5432 -e POSTGRES_USER=test -e POSTGRES_PASSWORD=test -e POSTGRES_DB=pokednd_test postgres:17`.
- Tests on real cards and reference data use `../build/bundle` and `../resources` and are skipped when those are missing, unless `POKEDND_REQUIRE_RESOURCES=true` (then they fail, as CI wants).
- The golden files are in `src/test/resources/golden/cards`. After a design change on purpose: `POKEDND_UPDATE_GOLDEN=true ./gradlew test --tests '*CardGoldenTest'`, then look at the pictures before keeping them.

## Configuration

Settings live in `src/main/resources/application.properties`. Secrets and connection details are **environment variables**, never files in git. The full list is in [`deploy/.env.example`](../deploy/.env.example), for example:

| Variable | Purpose |
|---|---|
| `QUARKUS_DATASOURCE_JDBC_URL`, `…_USERNAME`, `…_PASSWORD` | Database connection |
| `POKEDND_JWT_PRIVATE_KEY`, `POKEDND_JWT_PUBLIC_KEY` | RSA key pair (PEM, line breaks as `\n`) that signs the access tokens. Required in production |
| `POKEDND_BOOTSTRAP_ADMIN_EMAIL`, `…_PASSWORD` | The first site admin, created on first start when no admin exists |
| `POKEDND_BUNDLE_DIR` | The data bundle (default `../build/bundle`) |
| `POKEDND_RESOURCES_DIR` | The extracted `resources/` for card art, icons and language files (default `../resources`) |
| `POKEDND_DATA_DIR` | Where export files live |
| `POKEDND_BASE_URL` | Public URL, for e-mail links and OAuth callbacks |
| `POKEDND_OAUTH_DISCORD_CLIENT_ID`, `…_CLIENT_SECRET` | The Discord application; the redirect URL to register there is `<POKEDND_BASE_URL>/api/v1/auth/oauth/discord/callback`. Empty switches Discord sign-in off |
| `POKEDND_SMTP_HOST`, `…_PORT`, `…_USER`, `…_PASSWORD`, `…_FROM`, `…_TLS` | The mail server (Mailpit with the `dev` profile of `deploy/`) |

The card-export limits are `pokednd.cards.export.*` (maximum 1,500 printed cards per job, files kept 24 hours).

## Docker image

```sh
docker build -f deploy/api.Dockerfile -t pokednd-api .     # from the repository root
```

See [`deploy/`](../deploy) for running it with the database, the app and HTTPS.

The image does not contain the data bundle or the card art yet: put `bundle/` and `resources/` in the data volume (`/data`). The release pipeline (phase 79) will bake them in.
