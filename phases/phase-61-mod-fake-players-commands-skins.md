# Phase 61 — Mod: fake players, commands, skins

| | |
|---|---|
| **Stage** | The Minecraft mod (M4) |
| **Area** | Mod |
| **Depends on** | Phase 60 (Mod: connection, hello, config) |
| **Spec** | chapter 15.4, chapter 15.5, chapter 15.13, chapter 3.6, chapter 3.7, Appendix E (in `project_v2.md`) |

## Goal

Each trainer becomes a fake player in the world, with the right name and skin.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

**Ask the user first:**

- If MineSkin is needed, is it acceptable to depend on that outside service?

## Tasks

- [ ] Fake players (15.4): spawn, remove, keep in the world when the real player disconnects (3.7), and no real walking.
- [ ] The `/pokednd` commands of 15.5: link, game lobby open, status, and the admin ones.
- [ ] Spike M4-S1: apply a skin to a fake player with SkinRestorer's Java API (compile-only dependency, installed next to the mod, never bundled). Find out how an uploaded PNG gets a signed texture (a public URL signed by MineSkin, or our own provider), whether it works after a restart, and the rate limits.
- [ ] Write the result of the spike into 15.13 and Appendix E gap 11, and pick the fallback if needed (the `/skin` command).

## Tests

Every test must pass before the phase counts as done.

- [ ] GameTests: spawn and remove a fake player; it persists after its owner disconnects.
- [ ] The spike result is written down with evidence (a screenshot or log) and the decision.
- [ ] Commands are refused for players without the right role.

## Done when

Trainers appear as fake players with skins, and the spike has a recorded answer.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 61** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
