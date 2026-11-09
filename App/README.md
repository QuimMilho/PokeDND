# App — the PokeDnD web app

An installable web app (PWA) built with Angular and Angular Material, in **English and Portuguese**. Players and the game master use it to play; it talks only to the API.

Spec: [`project_v2.md`](../project_v2.md), chapters 13.3 and 14.

## Status

What exists (phases 25 to 27):

| Area | What |
|---|---|
| Accounts | Sign-up, login and log-out screens (English and Portuguese). The access token lives in memory only; the HTTP-only refresh cookie continues the session when the page opens, and a call that fails with 401 is repeated once after one refresh. Route guards for public and signed-in pages. |
| Card browser | `/cards`: playable species with art, name, Dex number and types, filtered by generation, type and name, 60 at a time. `/cards/:id`: the card preview with its four moves and its alternate forms. |
| Card export | `/cards/export`: choose cards by generation, type or by hand; size, paper, bleed, gap, copies, backs, language, colour; start the job, watch it, download the PDF. `/cards/jobs`: earlier exports. |
| Not yet | Games, the trainer wizard, play screens and the rest come in later phases; see [`todo.md`](../todo.md). |

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

`e2e/stack.mjs` starts the API and a small web server that serves the built app and passes `/api` to the API (as Caddy does). The API log is `test-results/api.log`. The main test, `e2e/m1.e2e.ts`, registers, logs in, exports Generation 1 with backs, downloads the PDF, counts its pages and decodes every QR code from the drawn pages (about 90 seconds).

## Languages

Texts are marked with Angular's `i18n` attribute (`$localize` in code). English is the source; Portuguese is `src/locale/messages.pt.xlf`. After you add or change a text, run `npm run extract-i18n` and translate the new entries in `messages.pt.xlf`. The build makes one folder per language.

## Layout

| Path | What |
|---|---|
| `src/app/` | Components, routes, configuration (`core/` API client and guards, `auth/` login and register, `cards/` browser, preview, export) |
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
