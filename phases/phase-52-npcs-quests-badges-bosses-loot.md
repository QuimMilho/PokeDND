# Phase 52 — NPCs, quests, badges, bosses, loot

| | |
|---|---|
| **Stage** | Rules and combat in the App (M3) |
| **Area** | Core |
| **Depends on** | Phase 51 (World, paths, movement, encounters); Phase 50 (Bag, items, money, shops, trading) |
| **Spec** | chapter 8.6, chapter 8.7, chapter 8.9 (in `project_v2.md`) |

## Goal

Give the world things to do and reasons to do them.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] NPC definitions and roles (8.6) with their dialogue steps.
- [ ] Quests with structured objectives tracked automatically and free-form ones tracked by the admin; badges (8.7).
- [ ] Bosses, shinies and the default loot table (8.9; it is Suggested, Appendix D #12). Loot rolls are made by the system.

## Tests

Every test must pass before the phase counts as done.

- [ ] A quest is completed through its objectives in a scripted scenario.
- [ ] A boss defeat gives loot that follows the table weights (fixed seed).
- [ ] An NPC of each role runs one interaction.

## Done when

NPCs, quests, badges and bosses work in the engine.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 52** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
