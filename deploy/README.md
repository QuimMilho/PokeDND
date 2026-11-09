# deploy — run everything with Docker Compose

Runs the whole system on one server: HTTPS proxy, app, API and a database. The Minecraft server normally lives on another machine.

Spec: [`project_v2.md`](../project_v2.md), chapters 13.5, 20.7 and 20.8.

## First start

```sh
cp deploy/.env.example deploy/.env      # then edit deploy/.env and replace every CHANGE_ME
docker compose -f deploy/docker-compose.yml up -d
```

Open `https://<POKEDND_DOMAIN>` (with the default `localhost`, your browser warns once about the local certificate). A real domain needs ports 80 and 443 reachable from the internet; Caddy then gets the certificate by itself.

`deploy/.env` holds your secrets and is **ignored by git**. `deploy/.env.example` is tracked and holds none.

## Choosing services

`COMPOSE_PROFILES` in `.env` switches services on:

| Profile | Starts |
|---|---|
| `postgres` (default) | PostgreSQL |
| `mysql`, `mariadb`, `mongo` | The other databases. Pick the API image of the same kind (see below) |
| `dev` | Mailpit, a fake mail server: mails show at http://localhost:8025 |
| `minecraft` | A test Minecraft server with the mod (set `MINECRAFT_EULA=TRUE`) |

Example: `COMPOSE_PROFILES=postgres,dev`.

## Choosing the database

The API supports PostgreSQL, MySQL, MariaDB and MongoDB. The kind of database is fixed when the API is built, so there is **one API image per database**. To switch, change four things in `.env` (the comments in `.env.example` show each):

| Database | `COMPOSE_PROFILES` | `POKEDND_API_IMAGE` | `API_FLAVOUR` | Connection |
|---|---|---|---|---|
| PostgreSQL | `postgres` | `…/pokednd-api:latest-postgres` | `postgres` | `QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://db:5432/pokednd` |
| MySQL | `mysql` | `…:latest-mysql` | `mysql` | `jdbc:mysql://db-mysql:3306/pokednd` |
| MariaDB | `mariadb` | `…:latest-mariadb` | `mariadb` | `jdbc:mariadb://db-mariadb:3306/pokednd` |
| MongoDB | `mongo` | `…:latest-mongo` | `mongo` | `QUARKUS_MONGODB_CONNECTION_STRING=mongodb://…@db-mongo:27017/pokednd?authSource=admin` (instead of the three datasource lines) |

Nothing else changes: the schema is made by the API when it starts (Flyway scripts for the SQL databases, versioned index scripts for MongoDB). `API_FLAVOUR` only matters when you build the image on the spot with `docker compose build`. `node tools/check-api-images.mjs` builds and starts every image against its database and asks it for `/q/health`.

## Files

| File | What |
|---|---|
| `docker-compose.yml` | The services |
| `Caddyfile` | The public proxy: HTTPS, `/api/*` and `/ws/*` to the API, `/q/health`, everything else to the app. `/q/metrics` is **not** published |
| `app.Caddyfile` | Inside the app image: serves `/en/` and `/pt/` |
| `api.Dockerfile`, `app.Dockerfile` | Build the two images (context: the repository root) |
| `*.Dockerfile.dockerignore` | What each image build does not send to Docker |
| `.env.example` | Every setting, with a comment each |

## Everyday commands

```sh
docker compose -f deploy/docker-compose.yml build     # build the API and app images here
docker compose -f deploy/docker-compose.yml logs -f api
docker compose -f deploy/docker-compose.yml down       # stop (data volumes stay)
```

Backups, upgrades and monitoring are described in chapter 20.8 of the spec.

## Troubleshooting

- **`docker compose up` or `docker run -v …` hangs forever on Windows.** On some Docker Desktop set-ups every bind mount from the repository folder (`./Caddyfile`, `../Mod/build/libs`) blocks without an error. Check Docker Desktop → Settings → Resources → File sharing, or run the stack from a Linux machine or WSL. To validate a file without mounting it, send it in through stdin: `docker run --rm -i caddy:2 caddy validate --config /dev/stdin --adapter caddyfile < deploy/Caddyfile`. In Git Bash also set `MSYS_NO_PATHCONV=1`, or it rewrites `/etc/...` into `C:/Program Files/Git/etc/...`.
- Put `timeout 120` in front of Docker commands in scripts so a stuck Docker fails instead of waiting for ever.
