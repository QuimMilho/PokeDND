# Phase 09 — check-assets tool and the roster

| | |
|---|---|
| **Stage** | Resources and foundations (M0) |
| **Area** | tools |
| **Depends on** | Phase 01 (Regenerate all resources) |
| **Spec** | chapter 19.3, chapter 22, Appendix C (in `project_v2.md`) |

## Goal

Make the list of which Pokémon are complete enough to play. The check reproduces Appendix C and writes the roster that the importer, the cards and the mod will use.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] Write `tools/check-assets.mjs` (no dependencies). For every species and form it checks: model, texture, shiny texture, animations, poser, resolver, icon, cry sound, licence file.
- [x] Write the report to `build/reports/assets.md` and the roster to `build/roster.json` (generated, ignored). The roster lists the playable species and forms (all required assets present).
- [x] Make it fail with a clear message if a species listed in the roster is incomplete.
- [x] Add a small test with a fixture folder (a few fake species with missing pieces) so the logic is tested without the real 100 MB.

## Tests

Every test must pass before the phase counts as done.

- [x] On the real `resources/` it reports: 905 species with models, resolvers, icons and textures; 120 species without assets (Appendix C.1); 2 models without animations (Bounsweet, Pyukumuku); and every poser named by a resolver exists (the 277 ported ones count).
- [x] The fixture test passes for each kind of missing piece.
- [x] Running it twice gives byte-identical output.

## Done when

The roster and the asset report are generated and match Appendix C.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 09** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
