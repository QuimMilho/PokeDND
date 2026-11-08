# Phase 64 — Renderer 3: procedural motion, layers, entities

| | |
|---|---|
| **Stage** | The Minecraft mod (M4) |
| **Area** | Mod |
| **Depends on** | Phase 63 (Renderer 2: animations and posers) |
| **Spec** | chapter 15.3, chapter 15.9, chapter 14.8 (in `project_v2.md`) |

## Goal

Finish the renderer: the ported posers work, every roster species can appear in the world, and performance is acceptable.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] The procedural functions with the defaults used by Cobblemon: `q.look(bone, pitchMult 1, yawMult 1, maxPitch 70, minPitch -45, maxYaw 45, minYaw -45)`, `q.biped_walk(period 0.6662, amplitude 1.4, legs)`, `q.quadruped_walk`, `q.bimanual_swing(period 0.6662, amplitude 1, arms)`, `q.sine_wing_flap(amplitude, period, verticalShift in radians, axis, wings)`, `q.pitch_tilt`.
- [ ] Extra texture layers (some emissive) and cosmetic aspects.
- [ ] The generic fallback poser (idle, walk, sleep, cry, faint from the model's own animations) for what the ported posers do not cover (the 19 partial ones).
- [ ] Pokémon entities (15.9): spawn, scale, hitbox, despawn; target 60 FPS with about 40 entities on screen (cull far ones, update distant ones less often).

## Tests

Every test must pass before the phase counts as done.

- [ ] Unit tests for each procedural function with numbers worked out by hand from their definitions.
- [ ] Every roster species renders without a missing-animation error (a loop that loads each one).
- [ ] A performance test scene with 40 entities holds the target frame rate on this machine (record the number).

## Done when

All roster Pokémon animate in the world at an acceptable frame rate.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 64** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
