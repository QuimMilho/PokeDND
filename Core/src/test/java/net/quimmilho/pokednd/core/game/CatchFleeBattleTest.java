/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static net.quimmilho.pokednd.core.game.Arena.Answers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.quimmilho.pokednd.core.game.Battle.Action;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.Status;
import net.quimmilho.pokednd.core.rules.PokemonType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 48: catching in a battle (chapter 7.10) and running away (7.11), with fixed rolls. The numbers are the examples of the
 * specification:
 *
 * <pre>
 *   a wild Bulbasaur (catch rate 45, HP 46) at 6 HP, caught with a Poké Ball:   41.1 / 255 = 16.1 %   d6, need 6
 *   the same, asleep:                                                           40.3 %                d10, need 7+
 *   the same, with an Ultra Ball:                                               32.2 %                d6, need 5+
 *   running away, Speed 60 against 80:  first try  (96 + 30) / 256 = 49 %       d4, need 3+
 *                                       second try (96 + 60) / 256 = 61 %       d8, need 4+
 * </pre>
 */
class CatchFleeBattleTest {

    private static final Map<String, Integer> ANA_FIRST = Map.of("ana", 12, "wild", 5);

    private Arena arena;

    @BeforeEach
    void setUp() {
        arena = new Arena(MoveBook.of(Moves.all()), "ana", "bo");
    }

    // ------------------------------------------------------------------------------------------------------------------ scaffolding

    private Combatant charmander(int speed) {
        return arena.mon("ana-1", "ana", "Charmander", 20, 50, List.of(PokemonType.FIRE), 30, 28, 32, 30, speed, "ember", "tackle");
    }

    private Combatant bulbasaur(int hp, int speed) {
        return bulbasaurOf("wild", hp, speed);
    }

    private Combatant bulbasaurOf(String owner, int hp, int speed) {
        Combatant c = arena.mon("wild-1", owner, "Bulbasaur", 18, 46, List.of(PokemonType.GRASS, PokemonType.POISON), 29, 30, 33, 31, speed, "tackle");
        c = c.withHp(hp);
        return c.withBuild(c.build().withSpecies("bulbasaur", 45));
    }

    private void duel(Combatant ana, Combatant wild) {
        arena.start(Battle.Type.WILD, BattleSettings.defaults(), List.of(Arena.trainer("ana", List.of("ana-1"), List.of()), Arena.wild("wild", "Bulbasaur", "wild-1")),
                List.of(ana, wild));
    }

    private Combatant foe() {
        return arena.mon("wild-1");
    }

    /** A round in which Ana does {@code ana} and the wild Pokémon does nothing; the first roll that is asked is returned, and nothing is answered. */
    private GameEvent.RollRequested askFor(Action ana) {
        arena.round(ANA_FIRST).declare("ana-1", ana).declare("wild-1", Action.skip());
        arena.play(BattleDesk.startResolving(arena.state()));
        List<GameEvent> events = BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds);
        arena.play(events);
        return (GameEvent.RollRequested) events.get(0);
    }

    private void round(Action ana, Answers answers) {
        arena.round(ANA_FIRST).declare("ana-1", ana).declare("wild-1", Action.skip());
        arena.resolve(answers);
        arena.endRound();
    }

    private int balls() {
        return arena.state().trainers().get("ana").items().getOrDefault("poke_ball", 0);
    }

    // ================================================================================================================== catching

    @Test
    void aPokeBallAtAWeakBulbasaurIsOneInSixAndTheBallIsUsedUp() {
        duel(charmander(60), bulbasaur(6, 40));
        GameEvent.RollRequested ask = askFor(Action.ball("poke_ball", "wild-1"));
        assertEquals("b1-r1-ana-1-ball", ask.requestId());
        assertEquals(6, ask.dice().sides(), "16.1 % is a d6");
        assertEquals(List.of("fail", "success"), ask.bands().stream().map(GameState.Band::label).toList());
        assertEquals(6, ask.bands().get(1).from(), "need 6");
        assertEquals("u-ana", ask.rollerUserId(), "the trainer's player throws");
        assertEquals(5, balls(), "nothing is used up before the roll is answered");
        arena.play(RollDesk.enter(arena.state(), Arena.ADMIN, ask.requestId(), List.of(6), null));
        arena.keepResolving(Answers.none());
        assertEquals(4, balls());
        assertEquals(List.of("wild-1"), List.copyOf(arena.battle().caught().keySet()));
        assertEquals(6, arena.battle().caught().get("wild-1").hp(), "it keeps the HP it had");
        assertEquals("ana", arena.battle().caught().get("wild-1").participantId());
        assertEquals("poke_ball", arena.battle().caught().get("wild-1").ball());
        assertTrue(foe().fainted(), "a caught Pokémon leaves the field");
        assertTrue(arena.said("Gotcha! wild Bulbasaur was caught!"));
        assertTrue(arena.said("Ana throws a Poke Ball at wild Bulbasaur."));
        arena.endRound();
        arena.play(BattleDesk.endIfOver(arena.state()));
        assertEquals(Battle.Step.ENDED, arena.battle().step(), "the wild side has nobody left");
    }

    @Test
    void aBallThatFailsShakesLessWhenTheRollWasFurtherFromTheCatch() {
        int[] faces = {1, 3, 5};
        int[] expected = {0, 1, 2};
        for (int i = 0; i < faces.length; i++) {
            arena = new Arena(MoveBook.of(Moves.all()), "ana", "bo");
            duel(charmander(60), bulbasaur(6, 40));
            round(Action.ball("poke_ball", "wild-1"), Answers.of("ana-1-ball", faces[i]));
            assertEquals(4, balls(), "the ball is used up when it fails too");
            assertTrue(arena.battle().caught().isEmpty());
            assertFalse(foe().fainted());
            String line = switch (expected[i]) {
                case 0 -> "broke free";
                case 1 -> "appeared to be caught";
                default -> "Almost had it";
            };
            assertTrue(arena.said(line), "the roll " + faces[i] + " shakes " + expected[i] + " times: " + arena.lines());
        }
    }

    @Test
    void aSleepingPokemonIsCaughtFortyPercentOfTheTime() {
        duel(charmander(60), bulbasaur(6, 40).withCondition(foeCondition(Status.SLEEP)));
        GameEvent.RollRequested ask = askFor(Action.ball("poke_ball", "wild-1"));
        assertEquals(10, ask.dice().sides(), "40.3 % is a d10");
        assertEquals(7, ask.bands().get(1).from(), "need 7 or more");
    }

    @Test
    void anUltraBallIsOneInThree() {
        duel(charmander(60), bulbasaur(6, 40));
        giveBall("ultra_ball");
        GameEvent.RollRequested ask = askFor(Action.ball("ultra_ball", "wild-1"));
        assertEquals(6, ask.dice().sides(), "32.2 % is a d6");
        assertEquals(5, ask.bands().get(1).from(), "need 5 or more");
    }

    @Test
    void aMasterBallAlwaysCatchesAndAsksForNoRoll() {
        duel(charmander(60), bulbasaur(46, 40));
        giveBall("master_ball");
        round(Action.ball("master_ball", "wild-1"), Answers.none());
        assertEquals(List.of("wild-1"), List.copyOf(arena.battle().caught().keySet()));
        assertEquals(0, arena.state().trainers().get("ana").items().getOrDefault("master_ball", 0), "the last Master Ball is used up");
    }

    @Test
    void aBossCannotBeCaught() {
        Combatant boss = bulbasaur(6, 40);
        boss = new Combatant(boss.id(), boss.ownerId(), boss.name(), boss.level(), boss.hp(), boss.maxHp(), boss.speed(), true, boss.moves(), boss.build(), boss.condition());
        duel(charmander(60), boss);
        arena.round(ANA_FIRST);
        assertThrows(BattleRefusedException.class, () -> arena.declare("ana-1", Action.ball("poke_ball", "wild-1")));
    }

    @Test
    void aTrainersPokemonCannotBeCaughtUnlessTheGameAllowsIt() {
        arena.start(Battle.Type.NPC_TRAINER, BattleSettings.defaults(),
                List.of(Arena.trainer("ana", List.of("ana-1"), List.of()),
                        new Battle.Participant("rival", Battle.Kind.NPC, 1, "Rival", null, null, List.of("wild-1"), List.of())),
                List.of(charmander(60), bulbasaurOf("rival", 6, 40)));
        arena.round(Map.of("ana", 12, "rival", 5));
        assertThrows(BattleRefusedException.class, () -> arena.declare("ana-1", Action.ball("poke_ball", "wild-1")), "combat.catch.allowTrainerOwned is off");
    }

    @Test
    void catchingCanBeSwitchedOff() {
        arena.start(Battle.Type.WILD, BattleSettings.defaults().withCatch(false, false),
                List.of(Arena.trainer("ana", List.of("ana-1"), List.of()), Arena.wild("wild", "Bulbasaur", "wild-1")), List.of(charmander(60), bulbasaur(6, 40)));
        arena.round(ANA_FIRST);
        assertThrows(BattleRefusedException.class, () -> arena.declare("ana-1", Action.ball("poke_ball", "wild-1")));
    }

    @Test
    void theHandlingBonusMakesTheCatchMoreLikely() {
        duel(charmander(60), bulbasaur(6, 40));
        arena.round(ANA_FIRST).declare("ana-1", Action.ball("poke_ball", "wild-1")).declare("wild-1", Action.skip());
        arena.play(BattleDesk.startResolving(arena.state()));
        // a trainer whose Handling and class features make the catch ×2: 32.2 % (a d6 needing 5 or more)
        BattleBonuses twice = new BattleBonuses(Map.of(), Map.of(), Map.of(), Map.of("ana", 2.0), Map.of(), false, java.util.Set.of());
        List<GameEvent> events = BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds, twice);
        GameEvent.RollRequested ask = (GameEvent.RollRequested) events.get(0);
        assertEquals(6, ask.dice().sides());
        assertEquals(5, ask.bands().get(1).from());
    }

    // ================================================================================================================== fleeing

    @Test
    void theFirstTryAtSpeed60Against80IsFortyNinePercentAndTheSecondSixtyOne() {
        duel(charmander(60), bulbasaur(46, 80));
        GameEvent.RollRequested first = askFor(Action.flee());
        assertEquals("b1-r1-ana-1-flee", first.requestId());
        assertEquals(4, first.dice().sides(), "49 % is a d4");
        assertEquals(3, first.bands().get(1).from(), "need 3 or more");
        arena.play(RollDesk.enter(arena.state(), Arena.ADMIN, first.requestId(), List.of(2), null));
        arena.keepResolving(Answers.none());
        arena.endRound();
        assertEquals(1, arena.battle().fleeAttempts().get("ana"));
        assertTrue(arena.said("Ana couldn't get away!"));
        assertEquals(Battle.Step.ROUND_END, arena.battle().step(), "the battle goes on");

        GameEvent.RollRequested second = askFor(Action.flee());
        assertEquals("b1-r2-ana-1-flee", second.requestId());
        assertEquals(8, second.dice().sides(), "61 % is a d8");
        assertEquals(4, second.bands().get(1).from(), "need 4 or more");
        arena.play(RollDesk.enter(arena.state(), Arena.ADMIN, second.requestId(), List.of(4), null));
        arena.keepResolving(Answers.none());
        assertEquals("fled", arena.battle().left().get("ana"));
        assertTrue(arena.said("Ana got away safely!"));
        arena.play(BattleDesk.endIfOver(arena.state()));
        assertEquals(Battle.Step.ENDED, arena.battle().step(), "nobody is left on one side");
    }

    @Test
    void aTrainerAsFastAsTheOpponentAlwaysEscapesWithoutARoll() {
        duel(charmander(80), bulbasaur(46, 80));
        round(Action.flee(), Answers.none());
        assertEquals("fled", arena.battle().left().get("ana"));
    }

    @Test
    void theDexterityBonusAndClassFeaturesAddPercentagePoints() {
        duel(charmander(60), bulbasaur(46, 80));
        arena.round(ANA_FIRST).declare("ana-1", Action.flee()).declare("wild-1", Action.skip());
        arena.play(BattleDesk.startResolving(arena.state()));
        // +22 points: 49 % becomes 71 %
        BattleBonuses fast = new BattleBonuses(Map.of(), Map.of(), Map.of(), Map.of(), Map.of("ana", 22.0), false, java.util.Set.of());
        GameEvent.RollRequested ask = (GameEvent.RollRequested) BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds, fast).get(0);
        assertTrue(ask.trueChance() > 0.70 && ask.trueChance() < 0.72, "49.2 % + 22 points: " + ask.trueChance());
    }

    @Test
    void youCannotRunFromAnotherTrainerByDefault() {
        arena.start(Battle.Type.NPC_TRAINER, BattleSettings.defaults(),
                List.of(Arena.trainer("ana", List.of("ana-1"), List.of()),
                        new Battle.Participant("rival", Battle.Kind.NPC, 1, "Rival", null, null, List.of("wild-1"), List.of())),
                List.of(charmander(60), bulbasaurOf("rival", 46, 80)));
        arena.round(Map.of("ana", 12, "rival", 5));
        assertThrows(BattleRefusedException.class, () -> arena.declare("ana-1", Action.flee()));
    }

    @Test
    void onlyTheTrainerWhoRunsLeavesAndTheOthersFight() {
        Combatant ally = arena.mon("bo-1", "bo", "Squirtle", 20, 60, List.of(PokemonType.WATER), 30, 28, 32, 30, 50, "tackle");
        arena.start(Battle.Type.WILD, BattleSettings.defaults(),
                List.of(Arena.trainer("ana", List.of("ana-1"), List.of()), Arena.trainer("bo", List.of("bo-1"), List.of()), Arena.wild("wild", "Bulbasaur", "wild-1")),
                List.of(charmander(100), ally, bulbasaur(46, 80)));
        arena.round(Map.of("ana", 12, "bo", 10, "wild", 5)).declare("ana-1", Action.flee()).declare("bo-1", Action.skip()).declare("wild-1", Action.skip());
        arena.resolve(Answers.none());
        assertEquals("fled", arena.battle().left().get("ana"));
        arena.endRound();
        assertEquals(List.of(), BattleDesk.endIfOver(arena.state()), "the battle is not over");
        assertEquals(Battle.Step.ROUND_END, arena.battle().step(), "Bo and the wild Pokémon are still there");
        assertEquals(List.of("bo-1", "wild-1"), arena.battle().standing().stream().map(Combatant::id).toList(), "Ana's Pokémon left with Ana");
    }

    // ============================================================================================ what the sheets add

    @Test
    void theBonusesComeFromTheSheetsOfTheTrainers() {
        duel(charmander(60), bulbasaur(6, 40));
        BattleBonuses bonuses = BattleBonuses.of(arena.state(), net.quimmilho.pokednd.core.trainer.TrainerRulebook.defaults());
        // an Ace Trainer of level 1 with DEX 14 (+2) and Pokémon Handling +2 (WIS 10, proficient)
        assertEquals(2, bonuses.defenseRoll("ana"), "AC 12 = 10 + DEX 2: the trainer adds 2 to the defence roll");
        assertEquals(1.1, bonuses.catchMultiplier("ana"), 1e-9, "1 + 0.05 x 2");
        assertEquals(4.0, bonuses.fleePp("ana"), 1e-9, "DEX 2 x 2 percentage points");
        assertEquals(4.0, bonuses.accuracyPp("ana"), 1e-9, "command +2 and Commanding Presence +2");
        assertEquals(0, bonuses.damageReduction("ana"));
        assertEquals(1.0, bonuses.catchMultiplier("wild"), "a wild Pokémon has no sheet");
    }

    // ------------------------------------------------------------------------------------------------------------------ helpers

    private static Battle.Condition foeCondition(Status status) {
        return Battle.Condition.NONE.withStatus(status, status == Status.SLEEP ? 2 : 0);
    }

    private void giveBall(String ball) {
        net.quimmilho.pokednd.core.trainer.Trainer ana = arena.state().trainers().get("ana");
        Map<String, Integer> bag = new java.util.LinkedHashMap<>(ana.items());
        bag.put(ball, 1);
        arena.play(List.of(new GameEvent.TrainerSet("ana", ana.withItems(bag))));
    }
}
