# Phase 10 — Importer: read and clean

| | |
|---|---|
| **Stage** | Data and cards (M1) |
| **Area** | Core |
| **Depends on** | Phase 03 (Core library skeleton); Phase 01 (Regenerate all resources) |
| **Spec** | chapter 19.1, chapter 19.3 (in `project_v2.md`) |

## Goal

Start the importer in Core: read the raw files in `resources/` into clean in-memory records. Nothing is merged yet.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Add a CSV reader to Core (no new dependencies). Handle the byte-order mark, quoted fields and the known header typo (`Attcak EVs`).
- [ ] Read `pokedex.csv` (split the combined `Type` text into two, turn `—` into "none"), `attack.csv` (skip the one blank row), `item.csv`, `pokemon-attacks.csv` and `tm.csv` into typed records.
- [ ] Read the species JSON files (`data/cobblemon/species/**`) into records, indexed by Dex number.
- [ ] Every file gets a small report of what was skipped and why (kept for the final reports phase).

## Tests

Every test must pass before the phase counts as done.

- [ ] Row counts: pokedex 1,025; attack 930 usable moves; pokemon-attacks 130,704; item 390; tm 391; species JSON 1,025.
- [ ] The known quirks are covered by unit tests with small fixtures (BOM, typo header, blank row, `—`).
- [ ] Reading twice gives equal results.

## Done when

All raw inputs load into typed records with the expected counts.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 10** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
