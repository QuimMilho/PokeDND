# Phase 14 — Importer: learnsets, derived values, bundle

| | |
|---|---|
| **Stage** | Data and cards (M1) |
| **Area** | Core |
| **Depends on** | Phase 12 (Importer: evolution parser); Phase 13 (Importer: moves, abilities, items, TMs); Phase 09 (check-assets tool and the roster) |
| **Spec** | chapter 19.2, chapter 19.3, chapter 9.3, chapter 20.2 (in `project_v2.md`) |

## Goal

Finish the importer: one deterministic data bundle (with a content hash) that the API and the mod both load, plus readable reports.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] Learnsets: per species and form, moves by method and level (the form text is mapped with the table from the merge phase).
- [x] Derived values: stage badge (Basic, Stage 1, Stage 2), weaknesses and resistances (they need the type chart from Phase 15: if that is not done yet, leave a clearly marked stub and finish it there). Default hit bands and damage dice per move are added in Phase 18.
- [x] Validate: fail the build if any id dangles (a learnset move that does not exist, an evolution result that does not exist, a roster species without data).
- [x] Write the bundle files (`species.json`, `evolutions.json`, `learnsets.json`, `moves.json`, `abilities.json`, `items.json`, `roster.json`, `derived.json`), `reports/*.md` and `MANIFEST.json` (version, input hashes, output hash).
- [x] Add the `Core` command-line entry that writes the bundle to a folder.

## Tests

Every test must pass before the phase counts as done.

- [x] Running the importer twice gives the same output hash.
- [x] The bundle has no dangling ids (a test removes one move and expects a failure).
- [x] The roster equals the output of `tools/check-assets` (903 species: 905 modelled, minus Bounsweet and Pyukumuku, Appendix E #32).
- [x] The item filter removed exactly the Pokédex rows.

## Done when

A deterministic data bundle with reports and a manifest is produced from `resources/`.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 14** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
