# Phase 79 — Release pipeline

| | |
|---|---|
| **Stage** | Polish and release (M5) |
| **Area** | tools |
| **Depends on** | Phase 78 (Documentation and runbook); Phase 08 (CI pipelines) |
| **Spec** | chapter 13.6, chapter 16.3, chapter 21.3 (in `project_v2.md`) |

## Goal

A tag makes a release that anyone can install.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] A release workflow: on a version tag, build and publish the API images (`-postgres`, `-mysql`, `-mariadb`, `-mongo`), the app image and the mod jar; attach SHA-256 checksums.
- [ ] Release notes with the licence summary (21.3) and the protocol version.
- [ ] Versioning rules and the protocol compatibility check (20.8).

## Tests

Every test must pass before the phase counts as done.

- [ ] A dry run on a throw-away tag in a test repository publishes everything.
- [ ] The mod jar loads on a clean Fabric server and connects to the released API image.
- [ ] The checksums verify.

## Done when

A tag produces a complete, verifiable release.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 79** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
