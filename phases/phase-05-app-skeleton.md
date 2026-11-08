# Phase 05 — App skeleton

| | |
|---|---|
| **Stage** | Resources and foundations (M0) |
| **Area** | App |
| **Depends on** | Phase 01 (Regenerate all resources) |
| **Spec** | chapter 11.1, chapter 11.7, chapter 13.3 (in `project_v2.md`) |

## Goal

Create the web app project: installable PWA, Angular Material, phone-first layout, and a translation set-up that already holds English and Portuguese.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

**Asked the user first (answered 2026-11-08):**

- Built-in Angular i18n or Transloco? → **Angular built-in i18n** (one build per language: `/en/` and `/pt/`).
- Karma/Jasmine or Jest? → **Jasmine/Karma**.

## Tasks

- [x] Create `App/` with the newest stable Angular and Node versions at the time (record them): standalone components, signals, Angular Material, a service worker and a web manifest.
- [x] Set up translations for English and Portuguese. The spec allows Angular's built-in i18n or Transloco: ask the user which one.
- [x] Choose the unit-test runner (Jasmine/Karma or Jest, as the spec allows): ask the user. Write one component test.
- [x] Make a first page that works on a phone-sized and a desktop-sized window (a placeholder home screen with the app name).
- [x] Make sure `node_modules/`, `.angular/` and `dist/` are ignored.

## Tests

Every test must pass before the phase counts as done.

- [x] `npm ci` then `npm run build` succeeds.
- [x] `npm test` (headless) passes.
- [x] Serve the built app and fetch the home page; the manifest and the service worker file are served.
- [x] Switching the language shows the English and the Portuguese placeholder text.

## What was built

- `App/`: Angular 22.2.6, Angular Material 22.2.6 (Material 3 theme, Roboto), `@angular/localize`, `@angular/pwa` (service worker `ngsw-worker.js`, `manifest.webmanifest`, icons). Built with Node 24.20.0 and npm 11.19.0 (npm is pinned in `package.json`).
- Built-in i18n: English is the source text, `src/locale/messages.pt.xlf` is Portuguese. `npm run build` writes `dist/pokednd-app/browser/en/` and `.../pt/`. `npm run extract-i18n` refreshes `src/locale/messages.xlf`.
- Placeholder home screen: toolbar with EN/PT links and a card. Phone-first, more padding from 900 px wide.
- Tests: `npm test` (3 Jasmine/Karma tests in headless Chrome) and `npm run test:smoke` (`App/scripts/smoke.mjs`: serves the build and checks home page, manifest and service worker for both languages).
- Not done here (later phases): a root `/` redirect to the browser language, and the language setting in the account page (phase 35).

## Done when

The app builds, tests pass, it opens in a browser and is installable.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 05** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
