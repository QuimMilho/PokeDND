# Phase 75 — Push notifications

| | |
|---|---|
| **Stage** | Polish and release (M5) |
| **Area** | App |
| **Depends on** | Phase 59 (M3 full-game test and first play-test) |
| **Spec** | chapter 11.6, chapter 13.3, chapter 16.7 (in `project_v2.md`) |

## Goal

The app can notify people even when it is closed.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [ ] VAPID keys in the deployment configuration; subscription endpoints; the service worker shows notifications.
- [ ] The events that notify (`app.push.events`: turn, approvals, gameState) with a per-user choice.
- [ ] Rate limiting and clean-up of dead subscriptions.

## Tests

Every test must pass before the phase counts as done.

- [ ] API test with a fake push service: the right users get the right messages, others none.
- [ ] Service worker unit test for showing and clicking a notification.
- [ ] Unsubscribing stops messages.

## Done when

Players are notified when it matters.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 75** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
