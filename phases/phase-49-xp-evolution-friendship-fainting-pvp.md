# Phase 49 — XP, evolution, friendship, fainting, PvP

| | |
|---|---|
| **Stage** | Rules and combat in the App (M3) |
| **Area** | Core |
| **Depends on** | Phase 48 (Catching, fleeing, enemy AI, trainer attacks) |
| **Spec** | chapter 5.3, chapter 5.4, chapter 5.7, chapter 5.8, chapter 7.14, chapter 7.15 (in `project_v2.md`) |

## Goal

Pokémon grow after fights, and trainers can fight each other.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] XP awards and level-ups with move-learning prompts (5.3, 5.5).
- [x] Evolution triggers from the parsed rules (5.4): level, item, friendship, trade, time of day and the rest; the player or the admin confirms.
- [x] Friendship (5.8), fainting and healing (5.7), end of battle (7.15), PvP and NPC trainer battles (7.14).
- [x] Breeding (5.10) stays optional: skip it unless the user asks. *Skipped, as the phase says (Appendix D #95).*

## Tests

Every test must pass before the phase counts as done.

- [x] Scripted level-up and evolution cases for each evolution method found in the data.
- [x] Friendship changes as documented; fainted Pokémon stay fainted until healed or revived.
- [x] A PvP battle runs to the end with both players' rolls.

## Done when

Battles feed growth, and PvP works.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 49** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
