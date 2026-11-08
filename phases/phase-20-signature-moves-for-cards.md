# Phase 20 — Signature moves for cards

| | |
|---|---|
| **Stage** | Data and cards (M1) |
| **Area** | Core |
| **Depends on** | Phase 14 (Importer: learnsets, derived values, bundle); Phase 18 (Damage dice, hit bands, crits) |
| **Spec** | chapter 9.3, chapter 9.2 (in `project_v2.md`) |

## Goal

Decide which four moves a species card shows, in a way that is explainable and can be overridden by hand.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Implement the selection rule of 9.3 (candidate filter, scoring, pick order, level caps by stage `cards.signature.stageLevelCaps`).
- [ ] Read the overrides file `cards/signature-overrides.json` (create it with a few examples) and let it win.
- [ ] Add the result to `derived.json`.

## Tests

Every test must pass before the phase counts as done.

- [ ] Pikachu, Charizard, Magikarp and Eevee get sensible, documented moves (a test lists the expected four each).
- [ ] Every species in the roster gets exactly four distinct moves, or fewer only if it truly has fewer.
- [ ] An override changes the result and is reported.

## Done when

Every roster species has four signature moves, with overrides.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 20** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
