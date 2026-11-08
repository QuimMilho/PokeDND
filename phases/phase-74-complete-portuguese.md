# Phase 74 — Complete Portuguese

| | |
|---|---|
| **Stage** | Polish and release (M5) |
| **Area** | App |
| **Depends on** | Phase 59 (M3 full-game test and first play-test) |
| **Spec** | chapter 11.7, chapter 9.10, chapter 11.8 (in `project_v2.md`) |

## Goal

Portuguese is complete. The app uses European Portuguese; game texts fall back pt_pt, pt_br, en_us.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

**Ask the user first:**

- Who reviews the Portuguese wording?

## Tasks

- [ ] Fill every missing Portuguese string in the app, the mod language file, the email templates and the card text.
- [ ] Use the game-text lookup `app.language.gameTextFallback` everywhere Cobblemon texts appear.
- [ ] Ask the user (a native speaker) to review the Portuguese wording; list the open questions.

## Tests

Every test must pass before the phase counts as done.

- [ ] The translation completeness tests pass for the app and the mod (no key missing in either language).
- [ ] A card exported in Portuguese has no English fallback except the documented Pokédex-text case.
- [ ] No hard-coded user-visible English strings remain (a search script over the source finds none).

## Done when

The whole product is available in English and Portuguese.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 74** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
