# Phase 15 — Type chart and natures

| | |
|---|---|
| **Stage** | Data and cards (M1) |
| **Area** | Core |
| **Depends on** | Phase 03 (Core library skeleton) |
| **Spec** | chapter 7.7, chapter 5.2 (in `project_v2.md`) |

## Goal

Put the type effectiveness table and the natures in Core and prove them against a table written separately.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Write the type chart (18 types, multipliers 0, 0.5, 1, 2) and the nature table as data in Core.
- [ ] Functions: effectiveness of one move type against one or two defender types; nature multipliers per stat.
- [ ] Write the independent reference table in the test folder from a different source (for example typed in by hand from the games), not copied from the Showdown data.

## Tests

Every test must pass before the phase counts as done.

- [ ] All 18 x 18 pairs equal the independent table.
- [ ] Dual types multiply (Fire against Grass/Bug is 4x; Ground against Flying is 0).
- [ ] The 25 natures: five neutral, each other one raises one stat by 10 % and lowers another by 10 %.

## Done when

Type and nature tables are in Core and verified.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 15** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
