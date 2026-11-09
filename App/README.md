# App — the PokeDnD web app

An installable web app (PWA) built with Angular and Angular Material, in **English and Portuguese**. Players and the game master use it to play; it talks only to the API.

Spec: [`project_v2.md`](../project_v2.md), chapters 13.3 and 14.

## Status

What exists (phases 25 to 27, 35 and 36):

| Area | What |
|---|---|
| Accounts | Sign-up, login and log-out screens (English and Portuguese). The access token lives in memory only; the HTTP-only refresh cookie continues the session when the page opens, and a call that fails with 401 is repeated once after one refresh. Route guards for public and signed-in pages. |
| Card browser | `/cards`: playable species with art, name, Dex number and types, filtered by generation, type and name, 60 at a time. `/cards/:id`: the card preview with its four moves and its alternate forms. |
| Card export | `/cards/export`: choose cards by generation, type or by hand; size, paper, bleed, gap, copies, backs, language, colour; start the job, watch it, download the PDF. `/cards/jobs`: earlier exports. |
| Shell | The main menu (a bar at the bottom on a phone, in the top bar from 600 px; "New game" only for server admins and the site admin), light/dark/system themes, and the English/Portuguese switch. |
| Account | `/account`: display name, language, theme (saved on the account with `PATCH /me`), the logins of the account with a button to end the others, linked Minecraft accounts (link with a code from the mod, or show a code to type in Minecraft) and the Discord link. The login screen has a "Sign in with Discord" button when Discord is set up. |
| Games | `/games` (my games, join with a code, as player or spectator), `/games/new`, `/games/:id` (state and the buttons the state allows), `/games/:id/lobby` (join code, requests to accept or reject, members; asks the API every 3 seconds until phase 40), `/games/:id/admin/settings` (every setting of Appendix A in its group, with search, default markers, reset, presets and rule sets with a preview). |
| Trainers | `/games/:id/trainer/create` (and `/trainer/edit`): the wizard in six steps (class, ability scores by point buy, standard array or 4d6, skills, starter Pokémon, name and skin, summary). Every step is checked in the browser and again by the API; *Next* shows what is missing. `/games/:id/trainer`: the sheet (HP, Armor Class, abilities with modifiers and saves, skills, class features, bag, skin), change and remove while the game has not started, and the other trainers of the game. The skin is a 64×64 PNG from the computer or the skin of a Minecraft name; it is checked here and cleaned by the API. |
| Not yet | The play screens and the rest come in later phases; see [`todo.md`](../todo.md). |

## Commands

Needs Node.js 24 and npm 11. The unit tests need Chrome.

```sh
npm ci                  # install exactly what package-lock.json says
npm start               # dev server at http://localhost:4200 (English)
npm run build           # production build: dist/pokednd-app/browser/en and /pt
npm test                # unit tests, headless Chrome
npm run test:smoke      # serves the build and checks home pages, manifest and service worker (run build first)
npm run extract-i18n    # update src/locale/messages.xlf from the templates
npm run test:e2e        # end-to-end tests with a real browser, API and database (see below)
npm run e2e:stack       # start that stack alone, to try the app by hand (Ctrl+C stops it)
```

### Running the app against an API

`npm start` passes `/api` to an API on http://localhost:8080 (`proxy.conf.json`). Start the API as described in [`API/README.md`](../API/README.md); on plain http it needs `POKEDND_AUTH_COOKIE_SECURE=false`, or the browser refuses to keep the login cookie.

### End-to-end tests

`npm run test:e2e` (Playwright, with the Chrome that is installed) needs:

1. the app built (`npm run build`) and the API built (`./gradlew quarkusBuild` in `API/`), plus the data bundle (`build/bundle`) and `resources/` (see the API README);
2. a PostgreSQL server that is already running. The default is `postgres://test:test@127.0.0.1:55432/postgres`, the throw-away container of the API README; set `POKEDND_E2E_PG` to use another. Each run makes a **new, empty database** in it and drops it at the end;
3. Java on the path.

`e2e/stack.mjs` starts the API and a small web server that serves the built app and passes `/api` to the API (as Caddy does). The API log is `test-results/api.log`. `e2e/shell.e2e.ts` (theme, language, Minecraft linking, sessions, phone layout) and `e2e/games.e2e.ts` (create a game, lobby, join, accept, the states, the settings screen against the real registry) need a site admin: the stack creates one (`admin@example.test`). The M2 test, `e2e/m2.e2e.ts` (about 30 seconds), runs the whole flow of the specification's chapter 2, steps 1 to 4, with the **fake mod** (`tools/fake-mod`, started in the test by `e2e/fake-mod.ts` and connected to the real API with the server's token): the admin opens a lobby and the mod shows the join code, three players join and make trainers, the mod receives a spawn command for each trainer (and a skin command for the one with an uploaded skin), then the admin starts the game and the mod hears that the lobby closed. `e2e/flow.ts` holds the steps that several tests share (make a game, walk the trainer wizard). The CI workflow `e2e.yml` runs it. The main test `e2e/m1.e2e.ts` registers, logs in, exports Generation 1 with backs, downloads the PDF, counts its pages and decodes every QR code from the drawn pages (about 90 seconds).

## Languages

Texts are marked with Angular's `i18n` attribute (`$localize` in code). English is the source; Portuguese is `src/locale/messages.pt.xlf`. After you add or change a text, run `npm run extract-i18n` and translate the new entries in `messages.pt.xlf`. A unit test (`src/app/locale.spec.ts`) fails when a text exists in one catalogue and not in the other, and the production build fails when a text has no translation. The build makes one folder per language.

## Layout

| Path | What |
|---|---|
| `src/app/` | Components, routes, configuration (`core/` API client, guards, menu, theme; `auth/` login and register; `account/`; `games/` list, lobby and game page; `settings/` the settings screen; `trainers/` the wizard (`wizard-model.ts` holds the rules of each step, `wizard-steps.ts` the steps), the trainer page and the skin picker; `cards/` browser, preview, export; `shared/`) |
| `e2e/` | End-to-end tests and the stack they start |
| `src/locale/` | Translation files |
| `public/` | Icons and the web manifest |
| `ngsw-config.json` | Service worker (offline) settings |
| `dist/` | Build output (**ignored by git**) |

## Docker image

```sh
docker build -f deploy/app.Dockerfile -t pokednd-app .     # from the repository root
```

See [`deploy/`](../deploy) for running it behind HTTPS.
