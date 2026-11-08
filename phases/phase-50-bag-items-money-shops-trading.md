# Phase 50 — Bag, items, money, shops, trading

| | |
|---|---|
| **Stage** | Rules and combat in the App (M3) |
| **Area** | Core |
| **Depends on** | Phase 42 (Game state, event log, undo); Phase 13 (Importer: moves, abilities, items, TMs) |
| **Spec** | chapter 8.8, chapter 5.5, chapter 9.4 (in `project_v2.md`) |

## Goal

Items and money work: collect, use, buy, sell and trade.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Bag with pockets and limits (`bag.limit.*`); using items inside and outside battle; the effect handlers per category (8.8) including TMs and IV candies.
- [ ] Money in Pokédollars, shops and merchants (stock, restock, price multiplier), tutors, healers.
- [ ] Trading at a trade-centre NPC with confirmation by both sides; trade evolutions trigger.

## Tests

Every test must pass before the phase counts as done.

- [ ] Buy, sell and stock rules (including restock rounds) as scripted scenarios.
- [ ] IV candies stay within 0-31; a TM teaches its move and replaces one when the Pokémon knows four.
- [ ] A trade moves the Pokémon, items and money atomically, and can be undone.

## Done when

The item economy works.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 50** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
