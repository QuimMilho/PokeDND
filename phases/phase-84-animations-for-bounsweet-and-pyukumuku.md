# Phase 84 — Animations for Bounsweet and Pyukumuku

| | |
|---|---|
| **Stage** | Asset track (runs in parallel, optional) |
| **Area** | Assets |
| **Depends on** | Phase 01 (Regenerate all resources) |
| **Spec** | chapter 15.3, Appendix C (in `project_v2.md`) |

## Goal

Give the two models without animations enough motion to be playable.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

**Ask the user first:**

- Who makes these animations?

## Tasks

- [ ] Decide who makes them (the user in Blockbench, or a hand-written Bedrock animation file made here for simple idle and walk motion).
- [ ] Create `ground_idle`, `ground_walk`, `battle_idle`, `sleep`, `faint` animation files and the poser entries.
- [ ] Run the asset check and the icon render for both species.

## Tests

Every test must pass before the phase counts as done.

- [ ] `node tools/check-assets.mjs` no longer lists them under "models without animations".
- [ ] Their icons render without errors with the new animations.

## Done when

Bounsweet and Pyukumuku have animations and pass the check.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 84** in `todo.md`, and only now: the phase is added **and** tested. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
