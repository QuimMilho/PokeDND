# Phase 08 — CI pipelines

| | |
|---|---|
| **Stage** | Resources and foundations (M0) |
| **Area** | tools |
| **Depends on** | Phase 01 (Regenerate all resources); Phase 02 (Mod skeleton clean-up); Phase 03 (Core library skeleton); Phase 04 (API skeleton); Phase 05 (App skeleton) |
| **Spec** | chapter 13.6, chapter 20.1 (in `project_v2.md`) |

## Goal

Make a clean machine prove, on every push, that the whole repository builds and its checks pass, with no stored copy of the Cobblemon jar.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

**Ask the user first:**

- Is there a GitHub remote to test the workflows on? (Answered: yes, `origin` is the public repository `QuimMilho/PokeDND`; runs on `main` happen after each push. A throw-away branch has not been used.)

## Tasks

- [x] Create `.github/workflows/`: one workflow per project (tools, core, api, app, mod) with path filters, so a change in one project does not rebuild the others.
- [x] The tools workflow downloads the jar from the URL in `tools/extract.config.json`, verifies its SHA-512 (extract does this), runs `node tools/extract.mjs --no-icons` and `node tools/check-resources.mjs`.
- [x] Pin every action to a full version or commit and use caches for Gradle and npm.
- [x] Core publishes to Maven local before the API and the mod build (13.4).
- [x] Keep secrets out of the files; document which repository secrets will be needed later.

## Tests

Every test must pass before the phase counts as done.

- [x] Every workflow file is valid (run `actionlint` if it can be installed, otherwise a YAML parser).
- [x] Every command in every workflow runs successfully on this machine, in the same order.
- [x] A push to a throw-away branch runs all workflows green (ask the user to push, or to confirm there is a GitHub remote; if there is none, say so). Done on branch `ci/phases-34-43-check`: tools, core, api, app and e2e are green; `mod` is path-filtered and did not run (nothing in `Mod/` changed), its last run on `main` is green.

## Done when

All workflows are written, valid, and their commands pass locally; a real run is confirmed or the missing remote is reported.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 08** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
