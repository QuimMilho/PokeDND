# Core — the shared rules library

Plain Java with **no Minecraft code and no Quarkus code**. It holds what the API and the Mod must agree on: dice, game rules and the data importer. The only libraries it may use are Jackson (JSON, with its Jdk8 module for `Optional`) and the SLF4J API; the build fails if anything else sneaks in (`checkAllowedDependencies`).

Spec: [`project_v2.md`](../project_v2.md), chapters 13.3 and 13.4.

## Status

Done: the whole **data importer** (phases 10 to 14: read, merge, evolutions, moves/abilities/items/TMs, learnsets, derived values, the bundle), the **type chart and natures** (phase 15) the **smart-dice selector** (phase 16), **stats, XP and levelling** (phase 17), **hit bands, crits and damage** (phase 18), the **catch and flee calculators** (phase 19) the **signature moves** of the cards (phase 20) the **trainers** (phase 37: sheet, classes, creation, levelling, knock-out) the **game state, event log and undo** (phase 42), the **roll requests and approvals** (phase 43), the **battle rounds** (phase 44), and **hit, damage, types, statuses and move effects** (phases 45 and 46). Abilities and held items come next (see [`todo.md`](../todo.md)).

## The importer (`net.quimmilho.pokednd.core.importer`)

Write the data bundle. It needs `resources/` (from `node tools/extract.mjs`) and `build/roster.json` (from `node tools/check-assets.mjs`):

```sh
./gradlew importBundle      # writes ../build/bundle: species, evolutions, learnsets, moves, abilities, items,
                            # natures, typechart, roster, derived, forms, reports/*.md and MANIFEST.json
```

`importBundle` also reads the hand-written `cards/signature-overrides.json`. From code: `Bundle.build(resources, rosterJson, overridesJson).write(outDir)` (pass `null`, or use the two-argument `build`, for no overrides). The same inputs always give the same files (the hash is in `MANIFEST.json`), and nothing is written if an id dangles.

| Class | What it does |
|---|---|
| `Csv`, `RawReader`, `Raw`, `FileReport` | Phase 10: read and clean the CSVs and the species JSON. A bad row is skipped and reported; a missing file or column stops the import (`ImportException`) |
| `Roster` | Reads `build/roster.json`, the playable species and forms |
| `Forms` | The 63 reviewed `form` texts and their form ids; unknown texts are reported, never guessed |
| `SpeciesMerger` | CSV and species JSON in one list (the CSV wins), the conflicts, the form table |
| `EvolutionParser`, `Evolutions` | Evolution text to rules, cross-checked with the species JSON |
| `Showdown` | The committed Pokémon Showdown snapshot (`src/main/resources/showdown/`, MIT; made by `tools/export-showdown.mjs`) |
| `Catalog`, `ItemRules` | Moves, abilities, items and TM items, each with an effect class (automatic, scripted, manual), default effects and prices |
| `Learnsets`, `Derived` | What each form learns; stage badges, weaknesses and resistances, and the signature moves of every card |
| `Signature` | Phase 20: the four signature moves of every roster species and form (rule plus overrides), and `reports/signature.md` |
| `Bundle`, `Reports` | Validation, the bundle files, the manifest and the Markdown reports |

The importer tests also read the real `resources/` and `build/roster.json`. If they are missing the tests are skipped, unless `POKEDND_REQUIRE_RESOURCES=true` (CI sets it), when they fail.

## Rules (`net.quimmilho.pokednd.core.rules`, `net.quimmilho.pokednd.core.dice`)

| Class | What it does |
|---|---|
| `TypeChart`, `PokemonType` | The 18-type chart (data in `rules/typechart.json`); dual types multiply; weaknesses and resistances; `with(…)` makes an edited copy |
| `Nature`, `Stat` | The 25 natures and their 1.1 / 0.9 multipliers |
| `SmartDice`, `DiceSettings`, `DiceChoice`, `Dice` | The smart-dice rule: the simplest die for any odds, with the faces of each outcome, the error, and a sentence for the screen |
| `Stats`, `StatBlock`, `Ivs`, `Evs`, `PokemonSettings` | Phase 17: the HP and other-stat formulas (whole-number maths), stat and accuracy stages, IV generation (random or fixed), EV limits |
| `Growth`, `Experience`, `TrainerXp` | Phase 17: the XP tables of the six growth groups, the XP a defeated Pokémon gives, levelling up, and the trainer XP curve and table |

## Game state, event log, undo (`net.quimmilho.pokednd.core.game`)

Chapters 12.5 and 14.5. A game **is its list of events**: `GameReducer` turns them into a `GameState`, with no clock, no dice and no input or output, so the same events always give the same state. An event carries every number already decided (a roll has the faces that were rolled, a random choice has its value), so undoing, redoing or replaying never rolls again.

| Class | What it does |
|---|---|
| `GameState`, `GameStatus` | The immutable state: status, round, trainers, open roll requests, answered rolls, random choices made. Later phases add battles, parties and the map |
| `GameEvent`, `EventTypes` | The event records (status, round, trainer set/removed, roll requested/submitted/rejected/answered/overridden/expired/cancelled, random drawn), their stored names and the conversion to the log's `type` and JSON `payload`. A new event needs a record, a line in `EventTypes` and a branch in the reducer (the compiler insists) |
| `GameReducer`, `InvalidEventException` | `(state, event) → new state`. An event that cannot happen (a roll with a wrong number of dice, a request that does not exist) is refused and changes nothing |
| `GameLog`, `LogEntry`, `Snapshot`, `AuditEntry` | The log of one game: `append` an action (one or more events, added all or nothing), `undo` / `undoTo` / `redo`, a snapshot at the start of every round (`game.autosave.everyRounds`), and one audit entry per event, undo and redo, with who did it and what changed. `GameLog.restore` rebuilds a log from the stored entries, snapshots and audit entries |
| `GameStateJson`, `StateDiff` | A state as JSON (snapshots) and read back without loss; the changed parts of the state between two points (the audit entry's `before` and `after`) |

The API stores `LogEntry` rows (`seq`, `actionId`, `type`, `actor`, `payload`, `undone`, `discarded`), `Snapshot` rows and `AuditEntry` rows in the same transaction as the game rows, and passes `admin.undo.depth` and `game.autosave.everyRounds` in `GameLog.Settings`. Undone entries stay in the log. Tests: `GameReducerTest`, `GameLogTest`, `RollDeskTest`, and `GameLogPropertyTest` (400 generated games each: replay is always equal, undo then redo gives back the original, snapshots survive JSON).

### Roll requests and approvals (phase 43)

Chapters 6.3, 6.6, 6.7 and 6.8. Every roll is a **roll request**: who must roll, why, the dice, what the faces mean (the bands), the true chance, the modifiers and an optional time limit. A person answers it in one of three ways, and the result is checked and recorded as an event.

| Class | What it does |
|---|---|
| `RollRequests` | Builds the request events: `smart` from a `DiceChoice` (die, bands, chance), `classic` for a d20 plus modifier against a difficulty, and the sentence for the screen (`Roll 1d20 for Scratch · 1–4 miss · 5–19 hit · 20 crit · chance 80 % (+4 pp command bonus)`) |
| `RollModifier` | One bonus or penalty of chapter 6.6 (`STAGE`, `POINTS`, `MULTIPLIER`, `ADD`) with its short text |
| `RollSettings` | The settings of 6.8: `dice.input.modes`, `dice.approval.required`, `dice.approval.autoSeconds`, `dice.entry.perDie`, `dice.prompt.showTrueChance`, `dice.prompt.timeoutSeconds`, `dice.enemy.mode`, `dice.secret.mode`, `dice.rng.source`, with the defaults of the specification |
| `RollDesk` | One method per human action (`open`, `submit`, `rollDigital`, `approve`, `edit`, `reject`, `enter`, `cancel`, `override`, `reroll`, `expire`, `autoApprove`). It checks the actor and the settings and returns the events for `GameLog.append`, or throws `RollRefusedException` with a sentence for the person. It has no clock and no random numbers of its own |
| `DigitalRoller` | Faces from a **stored seed** (JDK generator `L64X128MixRandom`). A digital answer carries its seed, and the reducer refuses faces that the seed does not give, so a replay never rolls again and a log cannot be tampered with unnoticed |

The three ways: **player submits** (the player types the real die; with approval on, the admin approves, edits or rejects), **digital** (the system rolls with a seed, no approval), **admin enters** (always allowed). The sources in the log are `typed` (the player's real die), `digital` and `admin`. Enemy and secret rolls are made by the system, or by the admin with real dice when the setting says `adminPhysical`. Details and the choices made are in Appendix D #89 of the specification.

## Combat (`net.quimmilho.pokednd.core.combat`)

| Class | What it does |
|---|---|
| `PowerTable` | Move power to damage dice, as data (`rules/power-table.json`); edit with `withRow(…)`, store as JSON |
| `HitRoll` | The hit roll: accuracy, stages, bonuses and crit stage become the bands miss / hit / crit and the die; one-hit-KO moves |
| `Damage` | The damage formula with the dice total, crit, STAB, type, burn and other factors; fixed-damage moves |
| `MoveProfile` | What the calculators need to know about a move, read from the bundle |
| `CombatSettings` | `combat.crit.*`, `combat.stab.multiplier`, `combat.accuracy.stageEffects`, `combat.damage.*`, `combat.multihit.damageDice` with their defaults |
| `CatchCalculator`, `BallTable` | The catch chance from catch rate, HP, status, ball and Handling bonus, as a smart-dice roll; the table of all 48 balls |
| `FleeCalculator` | The flee chance from Speed, tries and the DEX bonus, as a smart-dice roll |

## Trainers (`net.quimmilho.pokednd.core.trainer`)

Chapter 4 as code and data. A game's rules are one `TrainerRulebook` (its `TrainerClasses`, its list of `SkillDef`s and its `TrainerSettings`); `TrainerRulebook.defaults()` is the plain game. A rulebook makes trainers and levels them up, and every refusal is a list of plain sentences (`TrainerProblem`: a stable `code` the app can translate, the `field` and an English `message`), thrown together as `InvalidTrainerException`.

| Class | What it does |
|---|---|
| `Ability`, `AbilityScores`, `Difficulty` | The six abilities and the modifier `floor((score − 10) / 2)`; the DC guide (5 to 30) |
| `AbilityScoreRules`, `ScoreMethod` | Point buy (27 points, 8 to 15, cost 0/1/2/3/4/5/7/9), the standard array, 4d6 drop the lowest, and the checks for each |
| `TrainerClasses`, `TrainerClass`, `ClassFeature`, `FeatureEffect`, `EffectKeys` | The class list as data (`src/main/resources/trainer/classes.json`, the nine classes of 4.4), read and written as JSON; the fixed vocabulary of feature effects, so an admin can build a feature without code |
| `SkillDef` | The seventeen skills and the ability each uses |
| `TrainerSettings`, `KoMode` | The keys of 4.7 with their defaults (the XP keys are in `rules/TrainerXp`) |
| `Trainer`, `TrainerRulebook`, `Sheet` | The stored trainer; creation, the sheet (proficiency, AC, saves, skills, features), XP, level-ups with the Ability Score Improvement at 4, 8, 12, 16 and 19, and HP |
| `EffectTotals` | What the features of a level add up to (sums, multipliers, flags, skills with expertise or advantage) |
| `TrainerBonuses` | 4.2: the Handling bonus to accuracy (+1 pp per point, at most +10), to catching (×(1 + 0.05 × Handling)) and the DEX bonus to fleeing (+2 pp per point) |
| `KnockOut` | 4.6: the three knock-out modes, the money lost, and the death saving throws |
| `TrainerNames` | 3 to 16 letters, digits and underscores; unique in the game |

Level-ups are confirmed by the player: XP only raises `pendingLevels`, and `levelUp(trainer, improvement)` takes one level at a time. The starter Pokémon and the skin are chosen in the same wizard but belong to the API (phase 38).

## Cards (`net.quimmilho.pokednd.core.cards`)

| Class | What it does |
|---|---|
| `SignatureMoves` | The rule of chapter 9.3: candidates, score, pick order, level caps by stage |
| `SignatureOverrides` | Reads `cards/signature-overrides.json`, which fixes the four moves of any card by hand |

## Build

Needs Java 25. The Gradle wrapper downloads Gradle.

```sh
./gradlew build                  # compile, test, check dependencies
./gradlew publishToMavenLocal    # installs net.quimmilho.pokednd:core:1.0-SNAPSHOT for the API and Mod
```

On Windows PowerShell use `.\gradlew` instead of `./gradlew`.

Publish Core **before** building the API; the API builds against the jar in your Maven local folder.

## Layout

| Path | What |
|---|---|
| `src/main/java/net/quimmilho/pokednd/core/` | The library |
| `src/main/resources/showdown/` | The committed Showdown snapshot (JSON, with the MIT notice) |
| `src/main/resources/rules/` | The type chart and the power table as data |
| `../cards/signature-overrides.json` | Hand-written signature moves (outside Core; read by the importer) |
| `src/test/java/` | JUnit tests |
| `src/test/resources/dice/reference.json` | Output of the reference smart-dice JavaScript (Appendix B.3), to compare against |
| `gradle/libs.versions.toml` | Pinned library versions |
| `core_version` in `gradle.properties` | The version number that the API and Mod depend on |
