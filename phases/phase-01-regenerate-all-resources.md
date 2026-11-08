# Phase 01 — Regenerate all resources

| | |
|---|---|
| **Stage** | Resources and foundations (M0) |
| **Area** | tools |
| **Depends on** | none |
| **Spec** | chapter 13.2, chapter 19.1, chapter 9.8, Appendix E (in `project_v2.md`) |

## Goal

Rebuild every generated file from the Cobblemon jar with a clean slate, check the result with an automatic smoke test, and make sure nothing generated can be added to git by accident. This proves that `resources/` is reproducible, which is the base for every later phase.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

**Also read:**

- `tools/extract.config.json` (jar version, URL, SHA-512) and the header comment of `tools/extract.mjs`.
- Appendix E of `project_v2.md` (what each gap became) and chapter 13.2 (what exists today).
- `.gitignore` as it is now.

**Ask the user first:**

- Delete the obsolete `extracted/` folder?

## What is generated and what is not

- **Generated and ignored:** `resources/` (assets, data, five CSVs, icons, card art, language files, TM data and icons, licence copies, `ATTRIBUTION.md`); `build/` (reports, Showdown staging); `vendor/cobblemon-src/` (a git clone, inside `/vendor`); the build output of Gradle projects (`.gradle/`, `build/`, `run/`) and IDE folders; npm/Angular output; `.env` files.
- **Not generated on purpose, stays tracked:** `tools/ported-posers/` (277 posers converted from Cobblemon code; they can be fixed by hand). `tools/port-posers.mjs` deletes its output folder before writing, so it must **never** be pointed at `tools/ported-posers/` in this phase.

## Tasks

- [x] Pre-checks: Node 22.2 or newer, `git` on the PATH, the Cobblemon jar present (its SHA-512 is checked by `extract.mjs`), at least 2 GB of free disk.
- [x] Record a snapshot of the current state: file counts per top folder of `resources/` and the row count of each CSV (pokedex 1,025; attack 930; pokemon-attacks 130,704; item 390; tm 391).
- [x] Run `node tools/extract.mjs --clean --card-art 1-1025` (all generations: about 1,500 images and about 110 MB, estimated from Generation 1). It is slow, so run it in the background and wait for it to finish.
- [x] Run `node tools/effect-coverage.mjs` (writes `build/reports/coverage.md` and `.json`).
- [x] Run `node tools/port-posers.mjs --no-fetch --out build/ported-posers-check --report build/reports` and compare the result with `tools/ported-posers/` (same files, same content). This shows the conversion is deterministic without touching the tracked folder.
- [x] Write `tools/check-resources.mjs` *(Suggested)*, a smoke test of `resources/` that asserts: the jar SHA-512; the CSV row counts above; 905 model folders each with a `license` file; `icons/_errors.txt` and `card-art/_errors.txt` are empty; every poser named by a resolver exists as a JSON file; the three language files, `data/cobblemon/tms` and `ATTRIBUTION.md` exist; no Aprijuice file anywhere.
- [x] Audit `.gitignore`: it must contain the block for generated output (`**/.gradle/`, `**/build/`, `/PokeDnD/run/`, `/Mod/run/`, `.idea/`, `*.iml`, `node_modules/`, `.angular/`, `/App/dist/`, `.env`, `*.log`) plus `/resources`, `/build`, `/vendor`, `/extracted` and the jar. Add anything the rebuild revealed that is missing.
- [x] Run `git check-ignore -v` on every generated path, `git ls-files resources build vendor` (must print nothing) and `git status --short --ignored`; no generated file may show as untracked.
- [x] Ask the user whether to delete the obsolete `extracted/` folder (about 110 MB, ignored). Delete it only if they say yes.

## Tests

Every test must pass before the phase counts as done.

- [x] `node tools/check-resources.mjs` exits with 0 and prints each check.
- [x] A second run, `node tools/extract.mjs --no-icons --out build/second-run`, produces the same file list and the same SHA-256 hashes as the first run (apart from icons and card art).
- [x] The snapshot from before matches the result apart from the card art (more images) and nothing missing.
- [x] `git status --short` lists nothing under `resources/`, `build/`, `vendor/`, `PokeDnD/.gradle`, `PokeDnD/build`, `PokeDnD/run` or `PokeDnD/.idea`.
- [x] `git ls-files resources build vendor` prints nothing.

## Done when

`resources/` was rebuilt from scratch, `tools/check-resources.mjs` passes, and every generated path is ignored by git.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 01** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
