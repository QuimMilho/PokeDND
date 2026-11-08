# API — the PokeDnD server

A Quarkus service (REST today; WebSocket, accounts, games and the rules engine come later). It is the only authority in a game: the app and the Minecraft mod both talk to it.

Spec: [`project_v2.md`](../project_v2.md), chapters 13.3, 13.5 and 16.

## Status

A skeleton: `GET /q/health`, `GET /q/metrics`, and `GET /api/core-version` (proves the API uses Core; it can be removed later). PostgreSQL is the default database; the other flavours come in phase 34.

## Build and run

Needs Java 25 and **Core published to Maven local first**:

```sh
cd ../Core && ./gradlew publishToMavenLocal
cd ../API
./gradlew build           # compile and test (the tests need no database yet)
./gradlew quarkusDev      # run with live reload, then open http://localhost:8080/q/health
```

On Windows PowerShell use `.\gradlew` instead of `./gradlew`.

## Configuration

Settings live in `src/main/resources/application.properties`. Secrets and connection details are **environment variables**, never files in git. The full list is in [`deploy/.env.example`](../deploy/.env.example), for example:

| Variable | Purpose |
|---|---|
| `QUARKUS_DATASOURCE_JDBC_URL`, `…_USERNAME`, `…_PASSWORD` | Database connection |
| `POKEDND_BASE_URL` | Public URL, for e-mail links and OAuth callbacks |
| `POKEDND_DATA_DIR` | Where skins, card art and export files live |

In dev mode, Quarkus starts a throw-away PostgreSQL for you when Docker is running and no database URL is set.

## Docker image

```sh
docker build -f deploy/api.Dockerfile -t pokednd-api .     # from the repository root
```

See [`deploy/`](../deploy) for running it with the database, the app and HTTPS.
