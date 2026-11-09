# tools — Node scripts

Plain Node.js scripts with **no dependencies** (Node 24 recommended). They rebuild the generated `resources/` folder from the Cobblemon jar and check the repository.

Spec: [`project_v2.md`](../project_v2.md), chapter 13.2.

## Rebuild `resources/`

```sh
node tools/extract.mjs          # downloads the Cobblemon jar if missing, checks its SHA-512, builds resources/
node tools/check-resources.mjs  # PASS/FAIL smoke test of the result
```

Useful options: `extract.mjs --no-icons` (skip the slowest step; CI does this, and then `check-resources.mjs --no-icons`), `--card-art 1-151` (render card art), `--out <folder>`, `--jar <path>`. The jar's address and checksum are in `extract.config.json`.

## All scripts

| Script | What it does |
|---|---|
| `extract.mjs` | Rebuilds `resources/` from the Cobblemon jar (assets, species data, languages, TM definitions, icons, CSVs) |
| `build-csv.mjs` | Builds the CSVs from Cobblemon's own data (called by `extract.mjs`) |
| `render-icons.mjs` | Software renderer: Bedrock model plus texture to a PNG (icons and card art) |
| `filter-sounds.mjs` | Keeps only the sounds the game needs (part of `extract.mjs`) |
| `port-posers.mjs` | Converts Cobblemon's Kotlin poser classes to JSON. **Never point it at `ported-posers/`: it deletes its output folder first.** |
| `effect-coverage.mjs` | Measures how much of the Showdown move, ability and item data is plain data |
| `check-resources.mjs` | Checks that a rebuilt `resources/` is complete |
| `check-assets.mjs` | For every Pokémon: resolver, model, texture, shiny texture, animations, poser, icon, cry, licence file. Writes `build/reports/assets.md` (Appendix C) and `build/roster.json` (the playable species and forms). `--strict` fails if a species with some assets lacks a required piece; `--expect <roster.json>` fails if a listed species is incomplete; `--no-icons` for a folder made without icons |
| `export-showdown.mjs` | Unpacks the Showdown data inside the Cobblemon jar and writes it as JSON to `Core/src/main/resources/showdown/` (committed, MIT notice included), so CI needs neither the jar nor the network |
| `gen-settings.mjs` | Reads Appendix A of `project_v2.md` and `settings-schema.json` and writes the API's settings registry (`API/src/main/resources/settings/registry.json`, committed). `--check` writes nothing and fails when the committed file is out of date; a key in the schema file that Appendix A lacks stops it |
| `test-api-flavours.mjs` | Runs the API test suite on PostgreSQL, MySQL, MariaDB and MongoDB, one throw-away database container each (no Testcontainers). `--keep` leaves the containers |
| `check-api-images.mjs` | Builds the API image of every database flavour, starts it against its database with environment variables only, and asks `/q/health` |
| `check-docs.mjs` | Checks the licence text, the credits table and every link in README, CREDITS and CONTRIBUTING (`--offline` skips the web) |
| `check-deploy.mjs` | Checks `deploy/.env.example` against the spec and `docker-compose.yml` |

## Folders

| Path | What |
|---|---|
| `lib/` | Zip, PNG, CSV and Showdown readers shared by the scripts |
| `ported-posers/` | Posers converted by `port-posers.mjs`. **Tracked in git on purpose**: they can be fixed by hand |
| `attribution.template.md` | Template for `resources/ATTRIBUTION.md` |
| `extract.config.json` | Which Cobblemon version to use, with its download URL and checksum |

## Tests

```sh
node --test "tools/test/*.test.mjs"   # fixtures for the asset check, plus the real resources/ when they have icons
```

The Cobblemon jar, `resources/`, `build/` and `vendor/` are generated and **ignored by git**.
