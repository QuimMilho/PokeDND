/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static net.quimmilho.pokednd.core.game.Arena.ADMIN;
import static net.quimmilho.pokednd.core.game.Arena.Answers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.quimmilho.pokednd.core.game.Battle.Action;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.Result;
import net.quimmilho.pokednd.core.game.Battle.Status;
import net.quimmilho.pokednd.core.game.Battle.Step;
import net.quimmilho.pokednd.core.game.Battle.Volatiles;
import net.quimmilho.pokednd.core.rules.PokemonType;
import org.junit.jupiter.api.Test;

/**
 * Phase 45: hit rolls, critical hits, damage, types, stat stages, side effects and the status conditions of chapter 5.6, as scripted
 * battles in which the admin types every die. Every number in a test can be checked by hand with the formulas of chapters 7.5 and 7.6:
 *
 * <pre>
 *   Ana's Charmander, level 20, Fire.        HP 50, Atk 30, Def 28, Sp.Atk 32, Sp.Def 30, Speed 60 (+3)
 *   wild Bulbasaur,   level 18, Grass/Poison. HP 46, Atk 29, Def 30, Sp.Atk 33, Sp.Def 31, Speed 40 (+2)
 *   damage = floor(floor(floor(2 x level / 5 + 2) x power x attack / defense) / 50) + 2, then x crit x STAB x type
 *   Ember (power 40, 2d8) rolled 4 + 5 = 9 gives power 45:  floor(10 x 45 x 32 / 31) = 464, 464 / 50 = 9, + 2 = 11
 * </pre>
 */
class BattleCombatTest {

    private static final List<PokemonType> FIRE = List.of(PokemonType.FIRE);
    private static final List<PokemonType> GRASS_POISON = List.of(PokemonType.GRASS, PokemonType.POISON);
    private static final Map<String, Integer> ANA_FIRST = Map.of("ana", 12, "wild", 5);
    private static final Map<String, Integer> WILD_FIRST = Map.of("ana", 3, "wild", 18);
    /** Damage dice of a 40 to 50 power move (2d8): 4 + 5. */
    private static final int[] NINE = {4, 5};

    private Arena arena;

    /** Charmander against a wild Bulbasaur with this much HP. */
    private void duel(int foeHp, String charmanderMoves, String bulbasaurMoves) {
        arena = new Arena(MoveBook.of(Moves.all()), "ana");
        arena.start(Battle.Type.WILD, BattleSettings.defaults(), List.of(Arena.trainer("ana", List.of("ana-1"), List.of()), Arena.wild("wild", "Bulbasaur", "wild-1")),
                List.of(arena.mon("ana-1", "ana", "Charmander", 20, 50, FIRE, 30, 28, 32, 30, 60, charmanderMoves.split(",")),
                        arena.mon("wild-1", "wild", "Bulbasaur", 18, foeHp, GRASS_POISON, 29, 30, 33, 31, 40, bulbasaurMoves.split(","))));
    }

    private void duel(String charmanderMoves, String bulbasaurMoves) {
        duel(46, charmanderMoves, bulbasaurMoves);
    }

    private Combatant ana() {
        return arena.mon("ana-1");
    }

    private Combatant foe() {
        return arena.mon("wild-1");
    }

    private void round(Map<String, Integer> initiative, Action ana, Action wild, Answers answers) {
        roundOf("ana-1", initiative, ana, wild, answers);
    }

    private void roundOf(String anaMon, Map<String, Integer> initiative, Action ana, Action wild, Answers answers) {
        arena.round(initiative);
        arena.declare(anaMon, ana);
        arena.declare("wild-1", wild);
        arena.resolve(answers);
        arena.endRound();
    }

    // ============================================================================================================= hit and damage

    @Test
    void emberDoesTheDamageOfTheSpecificationsWorkedExample() {
        duel("ember", "tackle");
        round(ANA_FIRST, Action.move("ember", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", NINE, "ana-1-side0", "fail"));

        // 11 x 1.5 (same type) x 2 (Fire against Grass; Poison is neutral) = 33
        assertEquals(46 - 33, foe().hp());
        assertEquals(Status.NONE, foe().status());
        assertTrue(arena.said("Charmander uses Ember on wild Bulbasaur."));
        assertTrue(arena.said("It's super effective!"));
        assertTrue(arena.said("wild Bulbasaur takes 33 damage (13/46 HP)."));
        assertEquals(24, ana().moves().get(0).pp(), "the move spent one PP");
    }

    @Test
    void aMoveAsksForItsRollsOneAtATimeAndNothingChangesUntilTheLastIsAnswered() {
        duel("ember", "tackle");
        arena.round(ANA_FIRST).declare("ana-1", Action.move("ember", "wild-1")).declare("wild-1", Action.skip());
        arena.play(BattleDesk.startResolving(arena.state()));

        // 1. the hit roll
        List<GameEvent> first = BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds);
        assertEquals(1, first.size());
        GameEvent.RollRequested hit = (GameEvent.RollRequested) first.get(0);
        assertEquals("b1-r1-ana-1-hit0", hit.requestId());
        assertEquals("u-ana", hit.rollerUserId(), "the owner of the Pokémon rolls");
        assertEquals(GameState.RollClass.NORMAL, hit.rollClass());
        assertEquals("Ember: hit", hit.reason());
        assertEquals(List.of("hit", "crit"), hit.bands().stream().map(GameState.Band::label).toList(), "a 100 % move has no miss band");
        arena.play(first);
        // asking again while it waits adds nothing: the same roll is not asked twice
        assertEquals(List.of(), BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds));
        assertEquals(46, foe().hp());

        // 2. the damage dice, once the hit is known
        arena.play(RollDesk.enter(arena.state(), ADMIN, "b1-r1-ana-1-hit0", List.of(10), null));
        List<GameEvent> second = BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds);
        GameEvent.RollRequested dice = (GameEvent.RollRequested) second.get(0);
        assertEquals("b1-r1-ana-1-dmg0", dice.requestId());
        assertEquals("2d8", dice.dice().toString(), "power 40 is 2d8 (chapter 7.6)");
        arena.play(second);
        arena.play(RollDesk.enter(arena.state(), ADMIN, dice.requestId(), List.of(4, 5), null));

        // 3. the chance of the burn: 10 % is a d10 where only the top face burns, rolled by the attacker's owner
        List<GameEvent> third = BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds);
        GameEvent.RollRequested chance = (GameEvent.RollRequested) third.get(0);
        assertEquals("b1-r1-ana-1-side0", chance.requestId());
        assertEquals(10, chance.dice().sides());
        assertEquals("u-ana", chance.rollerUserId());
        assertEquals(List.of("fail", "success"), chance.bands().stream().map(GameState.Band::label).toList());
        assertEquals(10, chance.bands().get(1).from(), "10 % = one face of ten");
        assertEquals(46, foe().hp(), "still nothing has happened to the foe");
        arena.play(third);
        arena.play(RollDesk.enter(arena.state(), ADMIN, chance.requestId(), List.of(3), null));

        // 4. everything is known: the outcome comes in one go and the action leaves the queue
        List<GameEvent> last = BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds);
        assertEquals(GameEvent.ActionResolved.class, last.get(0).getClass());
        assertEquals(Result.DONE, ((GameEvent.ActionResolved) last.get(0)).result());
        assertTrue(last.stream().anyMatch(e -> e instanceof GameEvent.HpChanged h && h.delta() == -33));
        arena.play(last);
        assertEquals(13, foe().hp());
        assertEquals(List.of("wild-1"), arena.battle().queue());
    }

    @Test
    void aRollTheEnemyMakesIsAnEnemyRollForTheAdminWhenRollsArePhysical() {
        duel("ember", "tackle");
        arena.round(WILD_FIRST).declare("ana-1", Action.skip()).declare("wild-1", Action.move("tackle", "ana-1"));
        arena.play(BattleDesk.startResolving(arena.state()));
        GameEvent.RollRequested hit = (GameEvent.RollRequested) BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds).get(0);
        assertEquals(GameState.RollClass.ENEMY, hit.rollClass());
        assertEquals(GameState.Roller.ADMIN, hit.roller(), "dice.enemy.mode = adminPhysical: the admin rolls for the wild Pokémon");
        assertEquals("b1-r1-wild-1-hit0", hit.requestId());
    }

    @Test
    void whenTheSystemRollsForTheEnemyTheMoveGoesThroughInOneCall() {
        arena = new Arena(MoveBook.of(Moves.all()), RollSettings.defaults().withSystemRolls(RollSettings.SystemRolls.AUTO, RollSettings.SystemRolls.AUTO), "ana");
        arena.start(Battle.Type.WILD, BattleSettings.defaults(), List.of(Arena.trainer("ana", List.of("ana-1"), List.of()), Arena.wild("wild", "Bulbasaur", "wild-1")),
                List.of(arena.mon("ana-1", "ana", "Charmander", 20, 50, FIRE, 30, 28, 32, 30, 60, "ember"),
                        arena.mon("wild-1", "wild", "Bulbasaur", 18, 46, GRASS_POISON, 29, 30, 33, 31, 40, "tackle")));
        arena.round(WILD_FIRST).declare("ana-1", Action.skip()).declare("wild-1", Action.move("tackle", "ana-1"));
        arena.play(BattleDesk.startResolving(arena.state()));
        // the skip, then the whole of Tackle: the system answers the hit and damage dice at once, so the calls do not wait
        for (int i = 0; i < 4 && !arena.battle().queue().isEmpty(); i++) {
            arena.play(BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds));
        }
        assertEquals(List.of(), arena.battle().queue());
        assertTrue(ana().hp() < 50 || arena.said("missed"), "Tackle hit or missed");
        assertEquals(List.of(), BattleDesk.awaiting(arena.state()), "no roll is left open");
        assertTrue(arena.state().rolls().stream().anyMatch(r -> r.source().equals("digital") && r.requestId().contains("wild-1-")), "the dice are digital and logged");
        assertEquals(arena.log.replay(), arena.state(), "the log replays to the same state, with the same dice");
    }

    @Test
    void anInaccurateMoveCanMiss() {
        duel("willowisp", "tackle");
        round(ANA_FIRST, Action.move("willowisp", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "miss"));
        assertEquals(Status.NONE, foe().status());
        assertTrue(arena.said("Charmander's attack missed wild Bulbasaur!"));
    }

    @Test
    void aCriticalHitDoesOneAndAHalfTimesMore() {
        duel(80, "ember", "tackle");
        round(ANA_FIRST, Action.move("ember", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "crit", "ana-1-dmg0", NINE, "ana-1-side0", "fail"));
        // 11 x 1.5 (crit) x 1.5 (same type) x 2 = 49.5, rounded down
        assertEquals(80 - 49, foe().hp());
        assertTrue(arena.said("A critical hit!"));
    }

    @Test
    void aNotVeryEffectiveMoveAndAMoveWithoutTheSameTypeBonus() {
        duel("watergun,tackle", "tackle");
        round(ANA_FIRST, Action.move("watergun", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", NINE));
        // 11 x 0.5 (Water against Grass); Charmander is not a Water type, so no bonus
        assertEquals(46 - 5, foe().hp());
        assertTrue(arena.said("It's not very effective..."));

        round(ANA_FIRST, Action.move("tackle", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", NINE));
        // Tackle: floor(10 x 45 x 30 / 30) = 450, 450 / 50 = 9, + 2 = 11, neutral
        assertEquals(46 - 5 - 11, foe().hp());
    }

    @Test
    void aMoveThatTheTypeChartBlocksDoesNothingAndNoDiceAreAsked() {
        arena = new Arena(MoveBook.of(Moves.all()), "ana");
        arena.start(Battle.Type.WILD, BattleSettings.defaults(), List.of(Arena.trainer("ana", List.of("ana-1"), List.of()), Arena.wild("wild", "Gastly", "wild-1")),
                List.of(arena.mon("ana-1", "ana", "Charmander", 20, 50, FIRE, 30, 28, 32, 30, 60, "tackle"),
                        arena.mon("wild-1", "wild", "Gastly", 18, 30, List.of(PokemonType.GHOST), 29, 30, 33, 31, 40, "tackle")));
        round(ANA_FIRST, Action.move("tackle", "wild-1"), Action.skip(), Answers.none());
        assertEquals(30, foe().hp());
        assertTrue(arena.said("It doesn't affect wild Gastly..."));
        assertTrue(arena.asked.isEmpty(), "no hit roll and no damage dice for a move that cannot hurt");
    }

    @Test
    void aMovePowerOnlyAsksForTheDiceItNeedsAndASureHitOnlyRollsForTheCrit() {
        duel("swift", "tackle");
        arena.round(ANA_FIRST).declare("ana-1", Action.move("swift")).declare("wild-1", Action.skip());
        arena.play(BattleDesk.startResolving(arena.state()));
        GameEvent.RollRequested hit = (GameEvent.RollRequested) BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds).get(0);
        // Swift never misses (the dash in the data): the d20 only decides whether it is a crit
        assertEquals(List.of("hit", "crit"), hit.bands().stream().map(GameState.Band::label).toList());
    }

    // ==================================================================================================================== stat stages

    @Test
    void statStagesChangeTheDamageAndTheSpeed() {
        duel("tackle,swordsdance", "growl,tackle");
        // the foe growls: Charmander's Attack goes down one stage (30 x 2/3 = 20)
        round(WILD_FIRST, Action.skip(), Action.move("growl"), Answers.none());
        assertEquals(-1, ana().condition().stage("atk"));
        assertTrue(arena.said("Charmander's Attack fell!"));

        round(ANA_FIRST, Action.move("tackle", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", NINE));
        // floor(10 x 45 x 20 / 30) = 300, 300 / 50 = 6, + 2 = 8
        assertEquals(46 - 8, foe().hp());

        // Swords Dance: +2, so the stage is +1 now (30 x 3/2 = 45)
        round(ANA_FIRST, Action.move("swordsdance"), Action.skip(), Answers.none());
        assertEquals(1, ana().condition().stage("atk"));
        assertTrue(arena.said("Charmander's Attack rose sharply!"));
    }

    @Test
    void aStageStopsAtPlusAndMinusSix() {
        duel("swordsdance", "tackle");
        for (int i = 0; i < 4; i++) {
            round(ANA_FIRST, Action.move("swordsdance"), Action.skip(), Answers.none());
        }
        assertEquals(6, ana().condition().stage("atk"), "+2 four times stops at +6");
        assertTrue(arena.said("Charmander's Attack won't go any higher!"));
    }

    @Test
    void theSpeedStageAndParalysisChangeTheSpeedTheInitiativeUses() {
        duel("ember", "tackle");
        Combatant c = ana();
        Combatant faster = c.withCondition(c.condition().withStage("spe", 2));
        assertEquals(120, faster.speed(), "60 at +2 is doubled");
        Combatant slower = c.withCondition(c.condition().withStatus(Status.PARALYSIS, 0));
        assertEquals(30, slower.speed(), "paralysis halves the Speed");
        assertEquals(60, slower.withCondition(Battle.Condition.NONE).speed(), "and it comes back when the status is gone");
    }

    @Test
    void anAccuracyStageChangesTheHitChanceAndIsShownOnThePrompt() {
        duel("ember", "sandattack,tackle");
        round(WILD_FIRST, Action.skip(), Action.move("sandattack", "ana-1"), Answers.none());
        assertEquals(-1, ana().condition().stage("accuracy"));
        // Ana's Ember, 100 % x 3/4 = 75 %
        arena.round(ANA_FIRST).declare("ana-1", Action.move("ember", "wild-1")).declare("wild-1", Action.skip());
        arena.play(BattleDesk.startResolving(arena.state()));
        GameEvent.RollRequested hit = (GameEvent.RollRequested) BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds).get(0);
        assertEquals("b1-r2-ana-1-hit0", hit.requestId());
        assertEquals(0.75, hit.trueChance(), 1e-9, "the chance to hit is 75 %");
        assertEquals(List.of("miss", "hit", "crit"), hit.bands().stream().map(GameState.Band::label).toList());
        assertEquals(List.of(new RollModifier(RollModifier.Kind.STAGE, "accuracy stage", -1)), hit.modifiers());
    }

    // ======================================================================================================================= statuses

    @Test
    void burnStartsFromASideEffectTakesAbitEachRoundHalvesPhysicalDamageAndEnds() {
        duel("ember", "tackle");
        // start: Ember's 10 % side effect succeeds
        round(ANA_FIRST, Action.move("ember", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", NINE, "ana-1-side0", "success"));
        assertEquals(Status.BURN, foe().status());
        assertTrue(arena.said("wild Bulbasaur was burned!"));
        // each turn: 1/16 of 46 = 2 at the end of the round (33 + 2 gone)
        assertEquals(46 - 33 - 2, foe().hp());
        assertTrue(arena.said("wild Bulbasaur takes 2 damage from the burn (11/46 HP)."));

        // physical damage is halved: Tackle with a burn is 5 where it is 10 without
        round(WILD_FIRST, Action.skip(), Action.move("tackle", "ana-1"), Answers.of("wild-1-hit0", 10, "wild-1-dmg0", NINE));
        assertEquals(50 - 5, ana().hp());
        assertEquals(46 - 33 - 2 - 2, foe().hp(), "and another 2 from the burn");

        // end: the admin cures it, and it takes nothing any more
        arena.play(BattleDesk.change(arena.state(), ADMIN, foe().withCondition(foe().condition().withStatus(Status.NONE, 0))));
        int before = foe().hp();
        round(ANA_FIRST, Action.skip(), Action.skip(), Answers.none());
        assertEquals(before, foe().hp());
    }

    @Test
    void poisonTakesAnEighthEachRound() {
        duel("poisonpowder", "tackle");
        round(ANA_FIRST, Action.move("poisonpowder", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit"));
        // Bulbasaur is Grass/Poison: a Poison type cannot be poisoned
        assertEquals(Status.NONE, foe().status());
        assertTrue(arena.said("But it failed!"));

        // a Fire type can: Bulbasaur poisons Charmander
        duel("tackle", "poisonpowder");
        round(WILD_FIRST, Action.skip(), Action.move("poisonpowder", "ana-1"), Answers.of("wild-1-hit0", "hit"));
        assertEquals(Status.POISON, ana().status());
        assertTrue(arena.said("Charmander was poisoned!"));
        assertEquals(50 - 6, ana().hp(), "start: 1/8 of 50 = 6 at the end of the first round");
        round(ANA_FIRST, Action.skip(), Action.skip(), Answers.none());
        assertEquals(50 - 12, ana().hp(), "each turn: 6 again");
        // end: switched out it is still poisoned (the status stays), cured by the admin it stops
        arena.play(BattleDesk.change(arena.state(), ADMIN, ana().withCondition(ana().condition().withStatus(Status.NONE, 0))));
        round(ANA_FIRST, Action.skip(), Action.skip(), Answers.none());
        assertEquals(50 - 12, ana().hp());
    }

    @Test
    void badPoisonGrowsEveryRoundAndStartsAgainWhenThePokemonIsSwitchedOut() {
        arena = new Arena(MoveBook.of(Moves.all()), "ana");
        arena.start(Battle.Type.WILD, BattleSettings.defaults(),
                List.of(Arena.trainer("ana", List.of("ana-1"), List.of("ana-2")), Arena.wild("wild", "Bulbasaur", "wild-1")),
                List.of(arena.mon("ana-1", "ana", "Charmander", 20, 160, FIRE, 30, 28, 32, 30, 60, "tackle"),
                        arena.mon("ana-2", "ana", "Pidgey", 20, 100, List.of(PokemonType.NORMAL, PokemonType.FLYING), 30, 28, 32, 30, 50, "tackle"),
                        arena.mon("wild-1", "wild", "Bulbasaur", 18, 46, GRASS_POISON, 29, 30, 33, 31, 40, "toxic")));
        round(WILD_FIRST, Action.skip(), Action.move("toxic", "ana-1"), Answers.of("wild-1-hit0", "hit"));
        assertEquals(Status.TOXIC, ana().status());
        assertTrue(arena.said("Charmander was badly poisoned!"));
        assertEquals(160 - 10, ana().hp(), "start: 1/16 of 160");
        round(ANA_FIRST, Action.skip(), Action.skip(), Answers.none());
        assertEquals(160 - 10 - 20, ana().hp(), "each turn: 2/16");
        round(ANA_FIRST, Action.skip(), Action.skip(), Answers.none());
        assertEquals(160 - 10 - 20 - 30, ana().hp(), "3/16");

        // end of the climb: out and in again, it starts at 1/16 once more
        round(ANA_FIRST, Action.switchTo("ana-2"), Action.skip(), Answers.none());
        assertEquals(Status.TOXIC, arena.mon("ana-1").status(), "the status stays on the Pokémon in the reserve");
        assertEquals(Map.of(), arena.mon("ana-1").condition().volatiles(), "the count is gone");
        int before = arena.mon("ana-1").hp();
        roundOf("ana-2", ANA_FIRST, Action.switchTo("ana-1"), Action.skip(), Answers.none());
        assertEquals(before - 10, ana().hp(), "1/16 again at the end of the round it came back");
        round(ANA_FIRST, Action.skip(), Action.skip(), Answers.none());
        assertEquals(before - 10 - 20, ana().hp(), "and 2/16 after that");
    }

    @Test
    void paralysisHalvesSpeedAndMayStopAMoveEachTurn() {
        duel("thunderwave", "tackle,thunderwave");
        round(ANA_FIRST, Action.move("thunderwave", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit"));
        // start
        assertEquals(Status.PARALYSIS, foe().status());
        assertEquals(20, foe().speed(), "Speed 40, halved");
        assertTrue(arena.said("wild Bulbasaur is paralyzed! It may be unable to move!"));

        // each turn: a 25 % chance (a d4, only the 4 stops it). With a 1 it moves, and PP is spent
        round(WILD_FIRST, Action.skip(), Action.move("tackle", "ana-1"), Answers.of("wild-1-paralysis", "fail", "wild-1-hit0", 10, "wild-1-dmg0", NINE));
        assertTrue(ana().hp() < 50, "it attacked");
        assertEquals(34, foe().moves().get(0).pp());
        // with the 4 it does not
        int hp = ana().hp();
        round(WILD_FIRST, Action.skip(), Action.move("tackle", "ana-1"), Answers.of("wild-1-paralysis", "success"));
        assertEquals(hp, ana().hp());
        assertTrue(arena.said("wild Bulbasaur is fully paralyzed!"));
        assertEquals(34, foe().moves().get(0).pp(), "a move that could not be used spends no PP");

        // end: cured, the Speed comes back
        arena.play(BattleDesk.change(arena.state(), ADMIN, foe().withCondition(foe().condition().withStatus(Status.NONE, 0))));
        assertEquals(40, foe().speed());
    }

    @Test
    void sleepLastsAsManyTurnsAsTheDieSaysAndThenThePokemonWakesAndMoves() {
        duel("hypnosis", "tackle");
        // start: the hit roll, then d6: 1-2 = 1 turn, 3-4 = 2 turns, 5-6 = 3 turns
        round(ANA_FIRST, Action.move("hypnosis", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit", "ana-1-slp0-wild-1", "2 turns"));
        assertEquals(Status.SLEEP, foe().status());
        assertEquals(2, foe().condition().statusTurns());
        assertTrue(arena.said("wild Bulbasaur fell asleep!"));

        // each turn: it cannot act and the count goes down
        round(WILD_FIRST, Action.skip(), Action.move("tackle", "ana-1"), Answers.none());
        assertEquals(1, foe().condition().statusTurns());
        assertTrue(arena.said("wild Bulbasaur is fast asleep."));
        assertEquals(50, ana().hp());
        assertEquals(35, foe().moves().get(0).pp(), "no PP spent while asleep");
        round(WILD_FIRST, Action.skip(), Action.move("tackle", "ana-1"), Answers.none());
        assertEquals(0, foe().condition().statusTurns());
        assertEquals(50, ana().hp(), "two turns lost");

        // end: it wakes up and acts in the same turn
        round(WILD_FIRST, Action.skip(), Action.move("tackle", "ana-1"), Answers.of("wild-1-hit0", 10, "wild-1-dmg0", NINE));
        assertEquals(Status.NONE, foe().status());
        assertTrue(arena.said("wild Bulbasaur woke up!"));
        assertTrue(ana().hp() < 50);
    }

    @Test
    void freezeStopsTheMoveUntilTheDieThawsItAndFireThawsItToo() {
        duel("icebeam,ember", "tackle");
        // start: Ice Beam's 10 % side effect
        round(ANA_FIRST, Action.move("icebeam", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", new int[] {3, 4, 5}, "ana-1-side0", "success"));
        assertEquals(Status.FREEZE, foe().status());
        assertTrue(arena.said("wild Bulbasaur was frozen solid!"));
        // each turn: a 20 % chance to thaw (a d10, 9 and 10 thaw). With a low number it stays frozen
        int hp = ana().hp();
        round(WILD_FIRST, Action.skip(), Action.move("tackle", "ana-1"), Answers.of("wild-1-thaw", "fail"));
        assertTrue(arena.said("wild Bulbasaur is frozen solid!"));
        assertEquals(hp, ana().hp());
        // end: with a high number it thaws and moves
        round(WILD_FIRST, Action.skip(), Action.move("tackle", "ana-1"), Answers.of("wild-1-thaw", "success", "wild-1-hit0", 10, "wild-1-dmg0", NINE));
        assertEquals(Status.NONE, foe().status());
        assertTrue(arena.said("wild Bulbasaur thawed out!"));
        assertTrue(ana().hp() < hp);
    }

    @Test
    void aFireMoveThawsAFrozenTarget() {
        duel(200, "icebeam,ember", "tackle");
        round(ANA_FIRST, Action.move("icebeam", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", new int[] {3, 4, 5}, "ana-1-side0", "success"));
        assertEquals(Status.FREEZE, foe().status());
        round(ANA_FIRST, Action.move("ember", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", NINE, "ana-1-side0", "fail"));
        assertEquals(Status.NONE, foe().status());
        assertTrue(arena.said("wild Bulbasaur thawed out!"), arena.lines().toString());
    }

    @Test
    void confusionLastsTwoToFiveTurnsAndMayMakeThePokemonHurtItself() {
        duel("tackle", "tackle,confuseray");
        // start: Confuse Ray never misses; the duration is a d4 plus 1
        round(WILD_FIRST, Action.skip(), Action.move("confuseray", "ana-1"), Answers.of("wild-1-conf0-ana-1", "3 turns"));
        assertEquals(3, ana().condition().volatileValue(Volatiles.CONFUSION));
        assertTrue(arena.said("Charmander became confused!"));

        // each turn: the count goes down and a d6 (5 and 6) makes it hurt itself instead of moving
        round(ANA_FIRST, Action.move("tackle", "wild-1"), Action.skip(), Answers.of("ana-1-confused", "success"));
        assertEquals(2, ana().condition().volatileValue(Volatiles.CONFUSION));
        assertTrue(arena.said("Charmander is confused!"));
        assertTrue(arena.said("It hurt itself in its confusion!"));
        // floor(2 x 20 / 5 + 2) = 10; 10 x 40 x 30 / 28 = 428; 428 / 50 = 8; + 2 = 10
        assertEquals(50 - 10, ana().hp());
        assertEquals(46, foe().hp(), "the attack did not happen");

        round(ANA_FIRST, Action.move("tackle", "wild-1"), Action.skip(), Answers.of("ana-1-confused", "fail", "ana-1-hit0", 10, "ana-1-dmg0", NINE));
        assertEquals(1, ana().condition().volatileValue(Volatiles.CONFUSION));
        assertEquals(46 - 11, foe().hp(), "this time it attacked");

        // end: on the next turn it snaps out of it and moves without a roll
        round(ANA_FIRST, Action.move("tackle", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", NINE));
        assertEquals(0, ana().condition().volatileValue(Volatiles.CONFUSION));
        assertTrue(arena.said("Charmander snapped out of its confusion!"));
        assertEquals(46 - 22, foe().hp());
    }

    @Test
    void aFlinchLosesTheTurnOfAPokemonThatHasNotMovedYetAndEndsWithTheRound() {
        duel("bite", "tackle");
        // Ana is faster: Bite's 30 % side effect is a d10 where 8-10 flinches; the slower foe loses its turn
        round(ANA_FIRST, Action.move("bite", "wild-1"), Action.move("tackle", "ana-1"),
                Answers.of("ana-1-hit0", 10, "ana-1-dmg0", new int[] {4, 5}, "ana-1-side0", "success"));
        assertTrue(arena.said("wild Bulbasaur flinched and couldn't move!"), arena.lines().toString());
        assertEquals(50, ana().hp(), "the foe's Tackle never happened");
        assertFalse(foe().condition().has(Volatiles.FLINCH), "and the flinch is gone at the end of the round");

        // a Pokémon that already moved is not made to flinch
        duel("bite", "tackle");
        round(WILD_FIRST, Action.move("bite", "wild-1"), Action.move("tackle", "ana-1"),
                Answers.of("wild-1-hit0", 10, "wild-1-dmg0", NINE, "ana-1-hit0", 10, "ana-1-dmg0", new int[] {4, 5}, "ana-1-side0", "success"));
        assertFalse(arena.said("flinched"));
    }

    // ================================================================================================================== fainting

    @Test
    void aFaintedPokemonLeavesTheFightItsActionIsCancelledAndTheBattleEnds() {
        duel(5, "ember", "tackle");
        round(ANA_FIRST, Action.move("ember", "wild-1"), Action.move("tackle", "ana-1"),
                Answers.of("ana-1-hit0", 10, "ana-1-dmg0", NINE, "ana-1-side0", "fail"));
        assertEquals(0, foe().hp(), "the damage stops at the HP that was left");
        assertTrue(arena.said("wild Bulbasaur takes 5 damage (0/5 HP)."));
        assertTrue(arena.said("wild Bulbasaur fainted!"));
        assertTrue(arena.said("wild Bulbasaur fainted before it could act."));
        assertEquals(50, ana().hp());
        assertEquals(List.of(), arena.battle().standing().stream().filter(c -> c.id().equals("wild-1")).toList(), "it is no longer on the field");
        assertEquals(Battle.Step.ROUND_END, arena.battle().step());

        assertTrue(arena.battle().over());
        arena.play(BattleDesk.endIfOver(arena.state()));
        assertEquals(Step.ENDED, arena.battle().step());
        assertTrue(arena.said("The battle ends: only one side is left."));
        assertEquals(arena.log.replay(), arena.state());
    }

    @Test
    void aPokemonThatFaintsFromBurnLosesItsStatusAndTheNextTargetCanBeChosen() {
        duel("ember", "tackle");
        arena.play(BattleDesk.change(arena.state(), ADMIN, foe().withHp(2).withCondition(foe().condition().withStatus(Status.BURN, 0))));
        round(ANA_FIRST, Action.skip(), Action.skip(), Answers.none());
        assertEquals(0, foe().hp());
        assertTrue(arena.said("wild Bulbasaur takes 2 damage from the burn (0/46 HP)."));
        assertEquals(Status.NONE, foe().status(), "a fainted Pokémon has no status");
    }

    @Test
    void theBattleLogReplaysFromTheEventsWithAllTheNewEvents() {
        duel("ember,thunderwave", "tackle");
        round(ANA_FIRST, Action.move("ember", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", NINE, "ana-1-side0", "success"));
        round(ANA_FIRST, Action.move("thunderwave", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit"));
        assertEquals(arena.log.replay(), arena.state());
        assertTrue(arena.log.entries().stream().map(e -> e.type()).anyMatch(t -> t.equals(EventTypes.HP_CHANGED)));
        assertTrue(arena.log.entries().stream().map(e -> e.type()).anyMatch(t -> t.equals(EventTypes.STATUS_SET)));
    }
}
