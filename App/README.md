# App — the PokeDnD web app

An installable web app (PWA) built with Angular and Angular Material, in **English and Portuguese**. Players and the game master use it to play; it talks only to the API.

Spec: [`project_v2.md`](../project_v2.md), chapters 13.3 and 14.

## Status

A skeleton: a placeholder home page, the language set-up, the service worker and the manifest. Login, the card browser and the rest come in phases 25 onwards; see [`todo.md`](../todo.md).

## Commands

Needs Node.js 24 and npm 11. The unit tests need Chrome.

```sh
npm ci                  # install exactly what package-lock.json says
npm start               # dev server at http://localhost:4200 (English)
npm run build           # production build: dist/pokednd-app/browser/en and /pt
npm test                # unit tests, headless Chrome
npm run test:smoke      # serves the build and checks home pages, manifest and service worker (run build first)
npm run extract-i18n    # update src/locale/messages.xlf from the templates
```

## Languages

Texts are marked with Angular's `i18n` attribute (`$localize` in code). English is the source; Portuguese is `src/locale/messages.pt.xlf`. After you add or change a text, run `npm run extract-i18n` and translate the new entries in `messages.pt.xlf`. The build makes one folder per language.

## Layout

| Path | What |
|---|---|
| `src/app/` | Components, routes, configuration |
| `src/locale/` | Translation files |
| `public/` | Icons and the web manifest |
| `ngsw-config.json` | Service worker (offline) settings |
| `dist/` | Build output (**ignored by git**) |

## Docker image

```sh
docker build -f deploy/app.Dockerfile -t pokednd-app .     # from the repository root
```

See [`deploy/`](../deploy) for running it behind HTTPS.
