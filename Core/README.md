# Core — the shared rules library

Plain Java with **no Minecraft code and no Quarkus code**. It holds what the API and the Mod must agree on: dice, game rules and the data importer. The only libraries it may use are Jackson (JSON) and the SLF4J API; the build fails if anything else sneaks in (`checkAllowedDependencies`).

Spec: [`project_v2.md`](../project_v2.md), chapters 13.3 and 13.4.

## Status

The first step of the importer exists (phase 10): it **reads and cleans** the raw files. Merging, evolutions, moves, learnsets and the data bundle come in phases 11 to 14, and the rules and the smart-dice selector in phases 15 to 20 (see [`todo.md`](../todo.md)).

## The importer (`net.quimmilho.pokednd.core.importer`)

```java
Raw raw = RawReader.read(Path.of("../resources"));   // the folder made by: node tools/extract.mjs
raw.pokedex();    // 1,025 rows of pokedex.csv     raw.attacks();   // 929 usable moves (930 rows, 1 skipped)
raw.items();      // 390 rows of item.csv         raw.learnsets(); // 130,704 rows of pokemon-attacks.csv
raw.tms();        // 391 rows of tm.csv           raw.species();   // 1,025 species JSON files, by Dex number
raw.reports();    // per file: rows read, rows kept, every skipped or repaired row with the reason
```

| Class | What it does |
|---|---|
| `Csv` | CSV reader: byte-order mark, quoted fields, CRLF/LF, blank lines, and the header typo `Attcak EVs` |
| `RawReader` | Reads the five CSVs and the species JSON into typed records. A bad row is skipped and reported; a missing file or column stops the import (`ImportException`) |
| `Raw` | The records. A blank cell or `—` becomes `Optional.empty()` |
| `FileReport` | What was skipped or repaired in one file, for the reports of phase 14 |

The importer tests also read the real `resources/` folder. If it is missing they are skipped, unless `POKEDND_REQUIRE_RESOURCES=true` (CI sets it), when they fail.

## Build

Needs Java 25. The Gradle wrapper downloads Gradle.

```sh
./gradlew build                  # compile, test, check dependencies
./gradlew publishToMavenLocal    # installs net.quimmilho.pokednd:core:1.0-SNAPSHOT for the API and Mod
```

On Windows PowerShell use `.\gradlew` instead of `./gradlew`.

Publish Core **before** building the API; the API builds against the jar in your Maven local folder.

## Layout

| Path | What |
|---|---|
| `src/main/java/net/quimmilho/pokednd/core/` | The library |
| `src/test/java/` | JUnit tests |
| `gradle/libs.versions.toml` | Pinned library versions |
| `core_version` in `gradle.properties` | The version number that the API and Mod depend on |
