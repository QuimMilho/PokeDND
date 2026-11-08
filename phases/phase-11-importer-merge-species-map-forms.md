# Phase 11 — Importer: merge species, map forms

| | |
|---|---|
| **Stage** | Data and cards (M1) |
| **Area** | Core |
| **Depends on** | Phase 10 (Importer: read and clean) |
| **Spec** | chapter 19.3, chapter 19.4, chapter 5.9 (in `project_v2.md`) |

## Goal

Produce one merged list of species and forms, and report every place where the CSV and the Cobblemon JSON disagree.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Merge each species: start from the CSV row and fill gaps from the JSON (hitbox, scale, behaviour, labels). If they disagree the CSV wins (decided); write every disagreement to a conflicts report.
- [ ] Map the 63 free-text `form` values of `pokemon-attacks.csv` to form ids using the species features and the resolvers (19.4). Unknown values are reported, never guessed.
- [ ] Mark forms as playable only when the roster says they are complete (use `build/roster.json` from the assets phase).
- [ ] Note: there are no Mega, Gigantamax, Primal or Terastal forms in the assets.

## Tests

Every test must pass before the phase counts as done.

- [ ] All 63 form values map to a form id or appear in the "unmapped" list, which a test pins to a known, reviewed content.
- [ ] The merged list has 1,025 species; the roster subset has 905.
- [ ] The conflicts report is deterministic (same file twice).

## Done when

One merged species and form list exists, with a reviewed conflicts report.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 11** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
