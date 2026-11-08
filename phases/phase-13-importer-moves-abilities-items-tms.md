# Phase 13 — Importer: moves, abilities, items, TMs

| | |
|---|---|
| **Stage** | Data and cards (M1) |
| **Area** | Core |
| **Depends on** | Phase 11 (Importer: merge species, map forms) |
| **Spec** | chapter 19.5, chapter 19.6, chapter 8.8, chapter 5.5 (in `project_v2.md`) |

## Goal

Build the moves, abilities and items tables with their effects, using a committed copy of the Showdown data so that CI needs neither the jar nor the network.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

**Ask the user first:**

- Confirm the item price table before it is used (Appendix D #11), or keep it as a draft?

## Tasks

- [x] Write a Node script in `tools/` that unpacks `showdown.zip` from the jar and converts the needed data files to JSON; commit the JSON in `Core/src/main/resources/showdown/` with the MIT notice (19.5).
- [x] Merge `attack.csv` with Showdown moves; same for abilities and items. Classify every entry as **automatic**, **scripted** or **manual** with the rule in `tools/effect-coverage.mjs` (19.6).
- [x] Items: read `item.csv` (390 rows, drop the 7 Pokédex rows) and `tm.csv` (391 TMs); attach the category effect handler (the Cobblemon `bag_items` scripts are the reference), the default price table (8.8) and the TM price.
- [x] The IV candies change one IV by the step in `items.ivCandy.step` (decided: 1).

## Tests

Every test must pass before the phase counts as done.

- [x] The class counts equal `build/reports/coverage.json` (445 automatic and 397 scripted moves; 87 Z/Max and 1 without data are listed separately).
- [x] There are 383 usable items plus 391 TM items; Aprijuice appears nowhere.
- [x] No move, ability or item in the tables refers to an id that does not exist.

## Done when

Moves, abilities, items and TMs exist with effect classes and prices.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 13** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
