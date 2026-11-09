# Phase 37 — Trainers: model, classes, creation rules

| | |
|---|---|
| **Stage** | Accounts, servers, lobby, settings (M2) |
| **Area** | Core |
| **Depends on** | Phase 14 (Importer: learnsets, derived values, bundle); Phase 17 (Stats, XP and levelling) |
| **Spec** | chapter 4.1, chapter 4.2, chapter 4.3, chapter 4.4, chapter 4.5, chapter 4.6, chapter 4.7 (in `project_v2.md`) |

## Goal

The rules of a trainer as code and data: ability scores, skills, classes, levelling and health.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

**Ask the user first:**

- The default class list is a Suggested item (Appendix D #2): keep it as written?

## Tasks

- [x] The trainer sheet (4.1) and the conversion of ability scores into effects on Pokémon (4.2: the small conversion rates).
- [x] Creation rules (4.3) and the default class list as editable data (4.4), including features and numbers.
- [x] Levelling and the XP table (4.5) and trainer health, being attacked and knock-out (4.6). Settings with defaults (4.7).
- [x] Validation: a created trainer must obey the rules, and the errors are readable.

## Tests

Every test must pass before the phase counts as done.

- [x] Each class can be created and reaches level 20 following 4.5; the numbers match the tables in the spec.
- [x] The conversion rates of 4.2 are tested with examples.
- [x] Invalid trainers (too many points, unknown class) are refused with clear messages.

## Done when

Trainers can be created and levelled according to chapter 4.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 37** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
