# PokeDnD — todo

The whole project cut into **86 small phases**. Each phase has a prompt file in [`phases/`](./phases) that is read when it is time to do that phase. The specification is [`project_v2.md`](./project_v2.md); the roadmap there (chapter 22) is what these phases split up.

## How this works

1. Work on **one phase at a time**. Take the first unticked phase whose dependencies are all ticked.
2. Open its prompt in `phases/` and read it completely before starting. Each prompt lists the spec chapters to read, the tasks, and the tests.
3. A phase is **added** when its tasks are done and **tested** when every test in its prompt passes. **Tick it here only when both are true.** Then update the progress line below.
4. Nothing generated goes into git: output is added to `.gitignore` in the phase that creates it. **Nobody commits automatically**; the owner commits when they want.
5. When something is not decided, **ask** instead of assuming. Proposals are marked *Suggested* and listed in Appendix D of the spec.
6. A phase is **small**: one working session, one area, and it ends with something that runs and is tested. If one grows, split it and add the new phase here.
7. The split into phases is itself a suggestion: reorder or merge phases freely, as long as dependencies still hold.

**Progress:** 19 of 86 phases done · 0 of 6 stage gates passed

## Cut from the project

Decided on 2026-11-08, so no phase builds them:

- **Google and Microsoft sign-in** (and with it the automatic Microsoft → Minecraft link and the Mojang application). Login is email + password and Discord; Minecraft accounts are linked by code.
- **SQLite** as a database. The API supports PostgreSQL, MySQL, MariaDB and MongoDB.

## Stage 0 — Resources and foundations (M0)

- [x] **Phase 01 — Regenerate all resources** · tools · [prompt](phases/phase-01-regenerate-all-resources.md)
  Rebuild everything generated (resources/, card art, reports) from scratch, prove it is reproducible, and make sure every generated file is git-ignored.
  *Depends on:* none
- [x] **Phase 02 — Mod skeleton clean-up** · Mod · [prompt](phases/phase-02-mod-skeleton-clean-up.md)
  Rename the mod folder PokeDnD to Mod and apply the clean-ups from chapter 13.2 (licence, description, authors, entry class names). The mod builds.
  *Depends on:* Phase 01
- [x] **Phase 03 — Core library skeleton** · Core · [prompt](phases/phase-03-core-library-skeleton.md)
  Create the Core Java library (rules, dice, importer) with Gradle, JUnit and publishToMavenLocal. One class and one test.
  *Depends on:* Phase 01, Phase 02
- [x] **Phase 04 — API skeleton** · API · [prompt](phases/phase-04-api-skeleton.md)
  Create the Quarkus API project with a health endpoint and a PostgreSQL profile. It builds and the health check passes in a test.
  *Depends on:* Phase 03
- [x] **Phase 05 — App skeleton** · App · [prompt](phases/phase-05-app-skeleton.md)
  Create the Angular PWA with Angular Material and English/Portuguese translation support. It builds, its unit test passes, and it opens.
  *Depends on:* Phase 01
- [x] **Phase 06 — Licences, README, credits** · Docs · [prompt](phases/phase-06-licences-readme-credits.md)
  Add the root LICENSE (MPL-2.0), README with build steps, and CREDITS.md with the decided credit wording and all third-party licences.
  *Depends on:* Phase 01
- [x] **Phase 07 — Deploy skeleton** · tools · [prompt](phases/phase-07-deploy-skeleton.md)
  Create deploy/ with docker-compose.yml, a Caddyfile and .env.example for the API, app and database.
  *Depends on:* Phase 04, Phase 05
- [ ] **Phase 08 — CI pipelines** · tools · [prompt](phases/phase-08-ci-pipelines.md)
  GitHub Actions that build and test tools, Core, API, App and Mod, download the Cobblemon jar from Modrinth, and run the resource check.
  *Depends on:* Phase 01, Phase 02, Phase 03, Phase 04, Phase 05
- [x] **Phase 09 — check-assets tool and the roster** · tools · [prompt](phases/phase-09-check-assets-tool-and-the-roster.md)
  Write tools/check-assets: for every species and form report model, texture, shiny texture, animations, poser, resolver, icon, cry and licence file, and write the roster of playable species.
  *Depends on:* Phase 01

- [ ] **M0 gate: all four projects (Mod, Core, API, App) build in CI; the API starts and answers a health check; the app builds and opens.**

## Stage 1 — Data and cards (M1)

- [x] **Phase 10 — Importer: read and clean** · Core · [prompt](phases/phase-10-importer-read-and-clean.md)
  Core reads the four CSVs, tm.csv and the species JSON and cleans them (byte-order mark, header typos, numbers, malformed rows).
  *Depends on:* Phase 03, Phase 01
- [x] **Phase 11 — Importer: merge species, map forms** · Core · [prompt](phases/phase-11-importer-merge-species-map-forms.md)
  Merge CSV and species JSON into one species list (CSV wins), map the 63 form names to form ids, and write a conflicts report.
  *Depends on:* Phase 10
- [x] **Phase 12 — Importer: evolution parser** · Core · [prompt](phases/phase-12-importer-evolution-parser.md)
  Parse the evolution text of every species into rules (level, item, friendship, trade and more) and list the ones that cannot be parsed.
  *Depends on:* Phase 11
- [x] **Phase 13 — Importer: moves, abilities, items, TMs** · Core · [prompt](phases/phase-13-importer-moves-abilities-items-tms.md)
  Merge moves, abilities and items with the Showdown data (kept as committed JSON), classify effects as automatic, scripted or manual, and build the item list including the 391 TMs.
  *Depends on:* Phase 11
- [x] **Phase 14 — Importer: learnsets, derived values, bundle** · Core · [prompt](phases/phase-14-importer-learnsets-derived-values-bundle.md)
  Add learnsets and derived values (stage badges, weaknesses), validate that nothing dangles, and write the data bundle, reports and manifest.
  *Depends on:* Phase 12, Phase 13, Phase 09
- [x] **Phase 15 — Type chart and natures** · Core · [prompt](phases/phase-15-type-chart-and-natures.md)
  The type chart and the 25 natures in Core, tested against an independently written table.
  *Depends on:* Phase 03
- [x] **Phase 16 — Smart dice selector** · Core · [prompt](phases/phase-16-smart-dice-selector.md)
  The algorithm that picks the right die for any odds (25 % is a d4, awkward odds use a d100), with every worked example as a test.
  *Depends on:* Phase 03
- [x] **Phase 17 — Stats, XP and levelling** · Core · [prompt](phases/phase-17-stats-xp-and-levelling.md)
  Stat formulas (HP and the other five, with IVs, EVs and natures) and the XP tables for all six growth groups.
  *Depends on:* Phase 03
- [x] **Phase 18 — Damage dice, hit bands, crits** · Core · [prompt](phases/phase-18-damage-dice-hit-bands-crits.md)
  The power-to-dice table, the hit roll bands, critical hits and the damage formula.
  *Depends on:* Phase 15, Phase 16
- [x] **Phase 19 — Catch and flee calculators** · Core · [prompt](phases/phase-19-catch-and-flee-calculators.md)
  The odds of catching a Pokémon (balls, HP, status) and of fleeing, as smart-dice rolls.
  *Depends on:* Phase 16, Phase 17
- [x] **Phase 20 — Signature moves for cards** · Core · [prompt](phases/phase-20-signature-moves-for-cards.md)
  Choose the four moves printed on each species card with the rule in 9.3, plus a manual overrides file.
  *Depends on:* Phase 14, Phase 18
- [ ] **Phase 21 — API: database layer and reference data** · API · [prompt](phases/phase-21-api-database-layer-and-reference-data.md)
  The SQL adapter (Hibernate with Panache, Flyway migrations), loading the data bundle, and reference-data endpoints. Tested on PostgreSQL.
  *Depends on:* Phase 04, Phase 14
- [ ] **Phase 22 — API: minimal accounts and roles** · API · [prompt](phases/phase-22-api-minimal-accounts-and-roles.md)
  Email and password sign-up and login (Argon2id), access and refresh tokens, and the roles per scope.
  *Depends on:* Phase 21
- [ ] **Phase 23 — API: card layout and PDF engine** · API · [prompt](phases/phase-23-api-card-layout-and-pdf-engine.md)
  Lay out species cards (front and back) and write a deterministic PDF with QR codes, bleed and cut gaps.
  *Depends on:* Phase 20, Phase 14, Phase 04
- [ ] **Phase 24 — API: card export endpoints** · API · [prompt](phases/phase-24-api-card-export-endpoints.md)
  Export jobs for cards by generation, type or manual selection, with limits and expiring files.
  *Depends on:* Phase 23, Phase 22, Phase 21
- [ ] **Phase 25 — App: login and API client** · App · [prompt](phases/phase-25-app-login-and-api-client.md)
  Login and sign-up screens, the API client with token refresh, and route guards.
  *Depends on:* Phase 05, Phase 22
- [ ] **Phase 26 — App: card browser** · App · [prompt](phases/phase-26-app-card-browser.md)
  Browse species, filter by generation and type, search by name, and see a card preview.
  *Depends on:* Phase 25, Phase 21
- [ ] **Phase 27 — App: export screen and the M1 end-to-end test** · App · [prompt](phases/phase-27-app-export-screen-and-the-m1-end-to-end-test.md)
  The export screen (choose cards, size, paper, backs) and a Playwright test that logs in and exports Generation 1.
  *Depends on:* Phase 26, Phase 24

- [ ] **M1 gate: a logged-in user exports Generation 1 species cards, with backs, as a print-ready PDF (checked by an automated end-to-end test).**

## Stage 2 — Accounts, servers, lobby, settings (M2)

- [ ] **Phase 28 — API: Discord sign-in and account linking** · API · [prompt](phases/phase-28-api-discord-sign-in-and-account-linking.md)
  Sign in with Discord through a small custom OAuth 2 client, and link a Discord identity to an existing account.
  *Depends on:* Phase 22
- [ ] **Phase 29 — API: email flows and sessions** · API · [prompt](phases/phase-29-api-email-flows-and-sessions.md)
  Email verification, password reset by email, and managing sessions (refresh tokens).
  *Depends on:* Phase 22
- [ ] **Phase 30 — API: Minecraft linking by code** · API · [prompt](phases/phase-30-api-minecraft-linking-by-code.md)
  Link Minecraft accounts to app accounts with an 8-character code (10 minutes, single use).
  *Depends on:* Phase 22
- [ ] **Phase 31 — API: servers** · API · [prompt](phases/phase-31-api-servers.md)
  Register Minecraft servers, issue and rotate server tokens, assign server admins, and track online status.
  *Depends on:* Phase 22
- [ ] **Phase 32 — API: games** · API · [prompt](phases/phase-32-api-games.md)
  Create games and move them through draft, lobby, running, paused and ended; members, co-admins and join codes.
  *Depends on:* Phase 31
- [ ] **Phase 33 — API: layered settings, rule sets, presets** · API · [prompt](phases/phase-33-api-layered-settings-rule-sets-presets.md)
  A settings registry of every key in Appendix A with defaults, layered per game, plus rule sets and the Fast/Detailed presets.
  *Depends on:* Phase 32
- [ ] **Phase 34 — API: database flavours** · API · [prompt](phases/phase-34-api-database-flavours.md)
  Make the API run on MySQL, MariaDB and MongoDB as well as PostgreSQL, with the same tests on each and one image per flavour.
  *Depends on:* Phase 33
- [ ] **Phase 35 — App: shell, themes, translations** · App · [prompt](phases/phase-35-app-shell-themes-translations.md)
  Navigation, light/dark themes, the account page, and English/Portuguese strings with an automatic completeness test.
  *Depends on:* Phase 25
- [ ] **Phase 36 — App: games, lobby and the settings screen** · App · [prompt](phases/phase-36-app-games-lobby-and-the-settings-screen.md)
  Create and join games, run the lobby, and change every setting of Appendix A with presets and rule sets.
  *Depends on:* Phase 35, Phase 32, Phase 33
- [ ] **Phase 37 — Trainers: model, classes, creation rules** · Core · [prompt](phases/phase-37-trainers-model-classes-creation-rules.md)
  The trainer sheet, the default classes, how a trainer is created and levels up, and how it affects its Pokémon.
  *Depends on:* Phase 14, Phase 17
- [ ] **Phase 38 — App: trainer wizard** · App · [prompt](phases/phase-38-app-trainer-wizard.md)
  A step-by-step wizard to create a trainer: class, skills, ability scores, starter Pokémon and skin upload.
  *Depends on:* Phase 36, Phase 37
- [ ] **Phase 39 — tools/fake-mod** · tools · [prompt](phases/phase-39-tools-fake-mod.md)
  A small program that speaks the mod protocol (answers lobby, spawn and walk commands, reports arrivals) so the API and app can be tested without Minecraft.
  *Depends on:* Phase 31
- [ ] **Phase 40 — API: realtime channel and server protocol** · API · [prompt](phases/phase-40-api-realtime-channel-and-server-protocol.md)
  WebSocket endpoints for the app and for the mod: envelope, authentication, hello, config sync, presence and reconnection.
  *Depends on:* Phase 32, Phase 39
- [ ] **Phase 41 — M2 end-to-end test** · tools · [prompt](phases/phase-41-m2-end-to-end-test.md)
  One automated test: an admin creates a game and opens a lobby, three players join and make trainers, and the fake mod shows the spawn commands.
  *Depends on:* Phase 40, Phase 38, Phase 34

- [ ] **M2 gate: in an automated test, an admin creates a game and opens a lobby, three players join and make trainers, and the fake mod shows the spawn commands.**

## Stage 3 — Rules and combat in the App (M3)

- [ ] **Phase 42 — Game state, event log, undo** · Core · [prompt](phases/phase-42-game-state-event-log-undo.md)
  The game as a log of events: the same log always rebuilds the same state, and the admin can undo.
  *Depends on:* Phase 41
- [ ] **Phase 43 — Roll requests and approvals** · Core · [prompt](phases/phase-43-roll-requests-and-approvals.md)
  Who rolls which die, the input modes (admin types, player types with approval, digital), approvals and modifiers.
  *Depends on:* Phase 42
- [ ] **Phase 44 — Battle rounds and the six actions** · Core · [prompt](phases/phase-44-battle-rounds-and-the-six-actions.md)
  Start a battle, order the actions by initiative, and handle the six actions of a round.
  *Depends on:* Phase 43
- [ ] **Phase 45 — Hit, damage, types, status** · Core · [prompt](phases/phase-45-hit-damage-types-status.md)
  Put the calculators into the battle: hit rolls, crits, damage, type effects, secondary effects and status conditions.
  *Depends on:* Phase 44, Phase 18
- [ ] **Phase 46 — Move effects** · Core · [prompt](phases/phase-46-move-effects.md)
  All plain-data moves work automatically, plus the 25 most-learned scripted moves.
  *Depends on:* Phase 45
- [ ] **Phase 47 — Abilities and held items** · Core · [prompt](phases/phase-47-abilities-and-held-items.md)
  The 25 most-used scripted abilities and the common held items, with an admin fallback and the on/off settings.
  *Depends on:* Phase 46
- [ ] **Phase 48 — Catching, fleeing, enemy AI, trainer attacks** · Core · [prompt](phases/phase-48-catching-fleeing-enemy-ai-trainer-attacks.md)
  Catch and flee inside battles, the enemy's choices, enemies attacking trainers, and knock-out.
  *Depends on:* Phase 44, Phase 19
- [ ] **Phase 49 — XP, evolution, friendship, fainting, PvP** · Core · [prompt](phases/phase-49-xp-evolution-friendship-fainting-pvp.md)
  After a battle: XP and level-ups, new moves, evolution, friendship, fainting and healing, and PvP and NPC trainer battles.
  *Depends on:* Phase 48
- [ ] **Phase 50 — Bag, items, money, shops, trading** · Core · [prompt](phases/phase-50-bag-items-money-shops-trading.md)
  The bag and its pockets, using items, money, shops and merchants, tutors, healers and trading.
  *Depends on:* Phase 42, Phase 13
- [ ] **Phase 51 — World, paths, movement, encounters** · Core · [prompt](phases/phase-51-world-paths-movement-encounters.md)
  Regions, paths made of nodes and edges, movement rounds, encounters with spawn tables, shinies, time and towns.
  *Depends on:* Phase 42
- [ ] **Phase 52 — NPCs, quests, badges, bosses, loot** · Core · [prompt](phases/phase-52-npcs-quests-badges-bosses-loot.md)
  NPC roles (battle, trade, tutor, healer, merchant), quests and badges, giant boss Pokémon and their loot.
  *Depends on:* Phase 51, Phase 50
- [ ] **Phase 53 — API: play endpoints and events** · API · [prompt](phases/phase-53-api-play-endpoints-and-events.md)
  Expose the rules engine: commands from players and admins, events to the app and to the mod, with the server as the only authority.
  *Depends on:* Phase 52, Phase 49, Phase 47
- [ ] **Phase 54 — API: admin powers, audit log, undo** · API · [prompt](phases/phase-54-api-admin-powers-audit-log-undo.md)
  The admin's controls: edit anything, approve rolls, control battles, undo, and the audit log.
  *Depends on:* Phase 53
- [ ] **Phase 55 — App: play screens** · App · [prompt](phases/phase-55-app-play-screens.md)
  The player screens: roll prompts, battle, bag, party, trainer sheet and notifications.
  *Depends on:* Phase 53
- [ ] **Phase 56 — App: admin panel** · App · [prompt](phases/phase-56-app-admin-panel.md)
  The admin panel: live board, roll approvals, battle control, undo, audit log and editing tools.
  *Depends on:* Phase 54
- [ ] **Phase 57 — App: cards in play** · App · [prompt](phases/phase-57-app-cards-in-play.md)
  Virtual cards, personal cards, binding physical cards to Pokémon, and scanning with the camera.
  *Depends on:* Phase 55
- [ ] **Phase 58 — App: 2D virtual map** · App · [prompt](phases/phase-58-app-2d-virtual-map.md)
  A simple 2D map of regions, paths and trainers so the overworld can be played without Minecraft.
  *Depends on:* Phase 55, Phase 51
- [ ] **Phase 59 — M3 full-game test and first play-test** · tools · [prompt](phases/phase-59-m3-full-game-test-and-first-play-test.md)
  A scripted full game with the fake mod, then a short real session with people using the app alone.
  *Depends on:* Phase 56, Phase 57, Phase 58

- [ ] **M3 gate: the scripted full-game test passes, and a real group plays a short session in the app alone.**

## Stage 4 — The Minecraft mod (M4)

- [ ] **Phase 60 — Mod: connection, hello, config** · Mod · [prompt](phases/phase-60-mod-connection-hello-config.md)
  The mod connects to the API with its server token, says hello, checks the protocol version and reconnects after a drop.
  *Depends on:* Phase 41, Phase 02
- [ ] **Phase 61 — Mod: fake players, commands, skins** · Mod · [prompt](phases/phase-61-mod-fake-players-commands-skins.md)
  Spawn and remove fake players on command from the API, run the /pokednd commands, and apply skins through SkinRestorer's Java API (spike M4-S1).
  *Depends on:* Phase 60
- [ ] **Phase 62 — Renderer 1: models, textures, resolvers** · Mod · [prompt](phases/phase-62-renderer-1-models-textures-resolvers.md)
  Load Bedrock models, textures and resolvers, and draw a still Pokémon in the world. The mod has its own renderer.
  *Depends on:* Phase 02
- [ ] **Phase 63 — Renderer 2: animations and posers** · Mod · [prompt](phases/phase-63-renderer-2-animations-and-posers.md)
  Play Bedrock animations with the Molang subset the data uses, and choose animations by pose with the poser files.
  *Depends on:* Phase 62
- [ ] **Phase 64 — Renderer 3: procedural motion, layers, entities** · Mod · [prompt](phases/phase-64-renderer-3-procedural-motion-layers-entities.md)
  The motions Cobblemon builds in code (look, walk, wing flap), emissive layers, the fallback poser, and Pokémon as entities in the world.
  *Depends on:* Phase 63
- [ ] **Phase 65 — Mod: world configuration** · Mod · [prompt](phases/phase-65-mod-world-configuration.md)
  Regions, paths and NPCs live in the world as a datapack, synced with the API, and can be reloaded while the server runs.
  *Depends on:* Phase 60, Phase 51
- [ ] **Phase 66 — Mod: regions, paths, wands, build mode** · Mod · [prompt](phases/phase-66-mod-regions-paths-wands-build-mode.md)
  In-game tools for the admin to create regions and paths with wands, and a build mode.
  *Depends on:* Phase 65
- [ ] **Phase 67 — Mod: NPCs, world protection, vanilla rules** · Mod · [prompt](phases/phase-67-mod-npcs-world-protection-vanilla-rules.md)
  NPC entities with their roles, protection of the world, and the vanilla rules that must be switched off or changed.
  *Depends on:* Phase 66, Phase 64
- [ ] **Phase 68 — Mod: cameras** · Mod · [prompt](phases/phase-68-mod-cameras.md)
  Real players never walk: an automatic director camera, the trainer's view, and the admin's flying camera.
  *Depends on:* Phase 61, Phase 64
- [ ] **Phase 69 — Mod: controls, HUD, screens** · Mod · [prompt](phases/phase-69-mod-controls-hud-screens.md)
  The PokeDnD section in Minecraft's Controls screen, the on-screen overlays (join code, prompts, turn banner) and the mod's own screens.
  *Depends on:* Phase 68
- [ ] **Phase 70 — Mod: battle presentation, catching, bosses, sound** · Mod · [prompt](phases/phase-70-mod-battle-presentation-catching-bosses-sound.md)
  Show battles in the world: positions, attack animations and effects, catch animation, the giant boss, shiny sparkles and Pokémon cries.
  *Depends on:* Phase 69, Phase 64, Phase 59
- [ ] **Phase 71 — BlueMap markers and map proxy** · API · [prompt](phases/phase-71-bluemap-markers-and-map-proxy.md)
  The mod draws trainers, paths and NPCs as BlueMap markers; the API proxies the map to the app, hiding what each viewer may not see.
  *Depends on:* Phase 66
- [ ] **Phase 72 — App: map and path editor, fog of war** · App · [prompt](phases/phase-72-app-map-and-path-editor-fog-of-war.md)
  Show the BlueMap in the app with the game overlays and let the admin edit paths on it; fog of war as a client-side mask.
  *Depends on:* Phase 71, Phase 58
- [ ] **Phase 73 — Mod tests and the M4 end-to-end session** · Mod · [prompt](phases/phase-73-mod-tests-and-the-m4-end-to-end-session.md)
  Collect the GameTests, golden images and manual checklist, and play the whole example session of chapter 2 with Minecraft as the screen.
  *Depends on:* Phase 70, Phase 72, Phase 67

- [ ] **M4 gate: the whole example session in chapter 2 can be played with Minecraft as the screen.**

## Stage 5 — Polish and release (M5)

- [ ] **Phase 74 — Complete Portuguese** · App · [prompt](phases/phase-74-complete-portuguese.md)
  Every text in the app, the mod, the API emails and the cards exists in English and Portuguese.
  *Depends on:* Phase 59
- [ ] **Phase 75 — Push notifications** · App · [prompt](phases/phase-75-push-notifications.md)
  Web Push (VAPID) so players are told when it is their turn or the admin must approve a roll.
  *Depends on:* Phase 59
- [ ] **Phase 76 — Accessibility and performance** · App · [prompt](phases/phase-76-accessibility-and-performance.md)
  Automated accessibility checks on the main screens and measured performance against the targets in 14.8.
  *Depends on:* Phase 74
- [ ] **Phase 77 — Credits and licence audit** · Docs · [prompt](phases/phase-77-credits-and-licence-audit.md)
  Check that the credit wording and licence notices are everywhere they must be, and ask the Cobblemon team whether they are happy with it.
  *Depends on:* Phase 59
- [ ] **Phase 78 — Documentation and runbook** · Docs · [prompt](phases/phase-78-documentation-and-runbook.md)
  A getting-started guide for admins and players, and the first-install runbook tested on a clean machine, with backups, upgrades and monitoring.
  *Depends on:* Phase 73
- [ ] **Phase 79 — Release pipeline** · tools · [prompt](phases/phase-79-release-pipeline.md)
  Tagged builds that publish the API images for each database, the app image and the mod jar, with checksums and release notes.
  *Depends on:* Phase 78, Phase 08
- [ ] **Phase 80 — Real-group play-tests and fixes** · Docs · [prompt](phases/phase-80-real-group-play-tests-and-fixes.md)
  Run real sessions with the people who will play, use the checklist in 20.6, and fix what they find.
  *Depends on:* Phase 79, Phase 76, Phase 77, Phase 75, Phase 73

- [ ] **M5 gate (Suggested; the roadmap gives none): the play-test checklist is answered, the release is tagged, and the runbook worked on a clean machine.**

## Asset track (runs in parallel, optional)

- [ ] **Phase 81 — Hand-fix the 19 partial posers** · Assets · [prompt](phases/phase-81-hand-fix-the-19-partial-posers.md)
  Finish by hand the posers that could not be converted completely (wave animations, triangle wing flaps, multiple heads and more).
  *Depends on:* Phase 01
- [ ] **Phase 82 — Community model packs: licence review** · Assets · [prompt](phases/phase-82-community-model-packs-licence-review.md)
  Look for existing models for the 120 species without assets, and check whether their licences allow our use.
  *Depends on:* Phase 01
- [ ] **Phase 83 — Modelling brief for the missing species** · Assets · [prompt](phases/phase-83-modelling-brief-for-the-missing-species.md)
  A checklist for the person who makes new models in Blockbench: files, names, animations and acceptance, starting with the ordinary species.
  *Depends on:* Phase 09
- [ ] **Phase 84 — Animations for Bounsweet and Pyukumuku** · Assets · [prompt](phases/phase-84-animations-for-bounsweet-and-pyukumuku.md)
  These two models have no animations; add simple idle and walk animations so they pass the asset check.
  *Depends on:* Phase 01
- [ ] **Phase 85 — New species: icons, card art, roster** · Assets · [prompt](phases/phase-85-new-species-icons-card-art-roster.md)
  When new species are delivered, render their icons and card art and add them to the roster.
  *Depends on:* Phase 83, Phase 84, Phase 09
- [ ] **Phase 86 — CI roster gate** · tools · [prompt](phases/phase-86-ci-roster-gate.md)
  CI fails if any species listed in the roster is missing a required asset.
  *Depends on:* Phase 09, Phase 85
