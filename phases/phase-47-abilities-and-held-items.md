# Phase 47 — Abilities and held items

| | |
|---|---|
| **Stage** | Rules and combat in the App (M3) |
| **Area** | Core |
| **Depends on** | Phase 46 (Move effects) |
| **Spec** | chapter 7.9, chapter 19.6, chapter 8.8 (in `project_v2.md`) |

## Goal

Give Pokémon their abilities and items in battle, for the most common ones.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] Implement the top 25 scripted abilities from the coverage report (Sturdy, Keen Eye, Intimidate, Inner Focus, Blaze, Overgrow, Sheer Force, Swarm, and so on). *Changed on 2026-11-10 (the user's decision):* Swift Swim, Chlorophyll, Sand Veil and Leaf Guard only matter in weather, which is not in the game, so they do nothing and do not count; the 25 are filled with the next real ones (Appendix D #93).
- [x] Held items: Leftovers, Choice items, Focus Sash, Life Orb, type gems, berries that heal or cure; one-use items are consumed.
- [x] Settings `combat.abilities.enabled` and `combat.heldItems.enabled`; manual fallback for the rest.

## Tests

Every test must pass before the phase counts as done.

- [x] One test per implemented ability and item.
- [x] With abilities switched off, battles run without any ability effect.
- [x] Coverage numbers regenerated and written to 19.6.

## Done when

The most common abilities and items work in battle.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 47** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
