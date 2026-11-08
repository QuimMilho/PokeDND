# Phase 06 — Licences, README, credits

| | |
|---|---|
| **Stage** | Resources and foundations (M0) |
| **Area** | Docs |
| **Depends on** | Phase 01 (Regenerate all resources) |
| **Spec** | chapter 21.1, chapter 21.2, chapter 21.3 (in `project_v2.md`) |

## Goal

Put the legal and welcome files in place: our code licence, a README that tells a newcomer how to build everything, and the credits we owe to Cobblemon, Showdown, BlueMap and SkinRestorer.

## Before you start

1. Read `todo.md` and this whole file.
2. Check that every phase under "Depends on" is ticked in `todo.md`. If one is not, stop and tell the user.
3. Read the spec chapters listed above in `project_v2.md` (it is the canonical specification).
4. Look at what the earlier phases built (code, tests, reports). If this prompt no longer matches reality, **update this file first** and tell the user what you changed.
5. If something this phase needs is not decided, ask the user (AskUserQuestion) instead of assuming. Mark any proposal of your own as *Suggested* and add it to Appendix D of `project_v2.md`.

## Tasks

- [x] Add the root `LICENSE` with the official MPL-2.0 text (fetch it from mozilla.org and compare, do not retype it).
- [x] Write `README.md`: what PokeDnD is in plain words, the folders (13.1), how to rebuild `resources/` (`node tools/extract.mjs`), and how to build each project (the commands from Phase 02, Phase 03, Phase 04 and Phase 05).
- [x] Write `CREDITS.md` with the **decided** wording: "The Cobblemon Team" with links to cobblemon.com and the assets repository, CC BY-NC 3.0, everything derived is labelled modified; plus the Showdown (MIT), BlueMap (MIT), SkinRestorer (MIT, Lionarius) and Pokémon trademark lines from 21.1, and the fan-content disclaimer.
- [x] Define the MPL source-file notice once (in `README.md` or `CONTRIBUTING.md`) so every later phase uses the same text.
- [x] Do not invent artist names. If something is unknown, say so.

## Tests

Every test must pass before the phase counts as done.

- [x] The `LICENSE` file is byte-identical to the official MPL-2.0 text.
- [x] Every row of the table in 21.1 appears in `CREDITS.md` or the README.
- [x] Every link in README and CREDITS answers (check them).
- [x] A person who only reads the README can run the commands for the projects that exist at this point.

## What was built

- `LICENSE`: the official MPL-2.0 text from mozilla.org. `Mod/LICENSE` was one character different (`http://` instead of `https://` in the notice line), so it was replaced by the same official text. `.gitattributes` keeps both files free of line-ending conversion (`-text`), so they stay byte-identical.
- `README.md`: what PokeDnD is, the folders, what to install, how to rebuild `resources/`, and the build commands for Core, API, App and Mod.
- `CREDITS.md`: the decided wording (The Cobblemon Team, links, CC BY-NC 3.0, "modified"), Showdown, BlueMap, SkinRestorer (Lionarius), Pokémon trademarks, Minecraft, the fan-content disclaimer. It names no individual artists and says so.
- `CONTRIBUTING.md`: the one MPL source-file notice every later phase must use, plus the art and data rules.
- `tools/check-docs.mjs`: the tests of this phase as a script (licence identical to mozilla.org, every 21.1 row covered, every link answers, notice present). `--offline` skips the network checks. Phase 77 can reuse it.
- Test 4 was done by running the README commands as written: `check-resources`, `Core` build + publishToMavenLocal, `API` build, `Mod` build, and `App` `npm ci` / build / test / test:smoke. All passed.

## Done when

The repository has a licence, a README and a credits file that match chapter 21.

## After the phase

1. Tick every task and every test above in this file.
2. Tick **Phase 06** in `todo.md`, and only now: the phase is added **and** tested. If it was the last phase of its stage and the gate holds, tick the stage gate as well. Update the progress line.
3. If a decision or a number in `project_v2.md` changed, fix it there, in plain language.
4. Add anything this phase generated (build output, downloads, reports) to `.gitignore`. Generated files never go into git.
5. Do **not** run `git commit`; the user commits when they want to.
6. Tell the user in a few lines: what changed, what was tested, and what is still open.
