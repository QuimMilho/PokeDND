# Phase 83 — Modelling brief for the missing species

| | |
|---|---|
| **Stage** | Asset track (runs in parallel, optional) |
| **Area** | Assets |
| **Depends on** | Phase 09 (check-assets tool and the roster) |
| **Spec** | chapter 15.2, chapter 15.3, Appendix C (in `project_v2.md`) |

## Goal

Tell a modeller exactly what to deliver so a new species passes the asset check without rework.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] Write `docs/modelling-brief.md`: for each species the files needed (model, texture, shiny texture, animations such as `ground_idle`, `ground_walk`, `sleep`, `battle_idle`, `cry`, `faint`, poser, resolver, cry sound, licence file), the naming and folder rules, and the order (Gulpin line, Castform, Vullaby line, Oricorio, Oranguru, Passimian, Blipbug line, Pawmi line, Bombirdier, Frigibax line first).
- [ ] A template folder with an empty poser and resolver for a new species.
- [ ] Explain how to check a delivery: `node tools/check-assets.mjs`.

## Tests

Every test must pass before the phase counts as done.

- [ ] Following the brief for one invented test species produces a folder that passes the asset check.
- [ ] The user (or the modeller) confirms the brief is clear.

## Done when

A modeller can work from the brief alone.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 83** in `todo.md`, and only now: the phase is added **and** tested. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
