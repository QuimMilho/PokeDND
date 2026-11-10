/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import static net.quimmilho.pokednd.core.game.Arena.ADMIN;
import static net.quimmilho.pokednd.core.game.Arena.Answers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.quimmilho.pokednd.core.combat.CombatSettings;
import net.quimmilho.pokednd.core.dice.DiceSettings;
import net.quimmilho.pokednd.core.game.Battle.Action;
import net.quimmilho.pokednd.core.game.Battle.Combatant;
import net.quimmilho.pokednd.core.game.Battle.Participant;
import net.quimmilho.pokednd.core.game.Battle.Status;
import net.quimmilho.pokednd.core.game.Battle.Volatiles;
import net.quimmilho.pokednd.core.game.RollDesk.Actor;
import net.quimmilho.pokednd.core.rules.PokemonType;
import net.quimmilho.pokednd.core.rules.TypeChart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 46: the effects of moves (chapters 7.8 and 19.6). The first half has one test for every kind of effect that the move data
 * writes as plain fields (recoil, drain, healing, multi-hit, one-hit KO, fixed damage, self boosts, priority, switching out, chances of
 * side effects); the second half has one test for every one of the 25 scripted moves that Core runs by hand; the last tests are about
 * the moves that Core cannot run, which become tasks for the admin.
 *
 * <pre>
 *   Ana's Charmander, level 20, Fire.    HP 50, Atk 30, Def 28, Sp.Atk 32, Sp.Def 30, Speed 60 (+3)
 *   wild Rattata,     level 18, Normal.  HP 46, Atk 29, Def 30, Sp.Atk 33, Sp.Def 31, Speed 40 (+2)
 *   damage = floor(floor(floor(2 x level / 5 + 2) x power x attack / defense) / 50) + 2, then the multipliers
 * </pre>
 */
class MoveEffectsTest {

    private static final List<PokemonType> FIRE = List.of(PokemonType.FIRE);
    private static final List<PokemonType> NORMAL = List.of(PokemonType.NORMAL);
    private static final Map<String, Integer> ANA_FIRST = Map.of("ana", 12, "wild", 5);
    private static final Map<String, Integer> WILD_FIRST = Map.of("ana", 3, "wild", 18);
    private static final int[] NINE = {4, 5};
    private static final Actor ANA = Actor.player("u-ana");

    private Arena arena;

    @BeforeEach
    void setUp() {
        arena = new Arena(MoveBook.of(Moves.all(), List.of(Moves.berry("oran_berry", 80, "Poison"))), "ana", "bo");
    }

    // ------------------------------------------------------------------------------------------------------------------ scaffolding

    private Combatant charmander(String... moves) {
        return arena.mon("ana-1", "ana", "Charmander", 20, 50, FIRE, 30, 28, 32, 30, 60, moves);
    }

    private Combatant rattata(String... moves) {
        return arena.mon("wild-1", "wild", "Rattata", 18, 46, NORMAL, 29, 30, 33, 31, 40, moves);
    }

    private void duel(Combatant ana, Combatant wild) {
        arena.start(Battle.Type.WILD, BattleSettings.defaults(), List.of(Arena.trainer("ana", List.of("ana-1"), List.of()), Arena.wild("wild", "Rattata", "wild-1")),
                List.of(ana, wild));
    }

    private void round(Map<String, Integer> initiative, Action ana, Action wild, Answers answers) {
        arena.round(initiative);
        arena.declare("ana-1", ana);
        arena.declare("wild-1", wild);
        arena.resolve(answers);
        if (arena.battle().mustSwitch().isEmpty()) {
            arena.endRound();
        }
    }

    private Combatant ana() {
        return arena.mon("ana-1");
    }

    private Combatant foe() {
        return arena.mon("wild-1");
    }

    private static Combatant status(Combatant c, Status status, int turns) {
        return c.withCondition(c.condition().withStatus(status, turns));
    }

    private static Combatant hp(Combatant c, int hp) {
        return c.withHp(hp);
    }

    private static Combatant item(Combatant c, String itemId) {
        return c.withCondition(c.condition().withHeldItem(itemId));
    }

    private static Combatant gender(Combatant c, String gender) {
        return c.withBuild(c.build().withGender(gender));
    }

    private static Combatant level(Combatant c, int level) {
        return new Combatant(c.id(), c.ownerId(), c.name(), level, c.hp(), c.maxHp(), c.speed(), c.boss(), c.moves(), c.build(), c.condition());
    }

    private static Combatant types(Combatant c, PokemonType... types) {
        return c.withBuild(c.build().withTypes(List.of(types)));
    }

    /** The first roll request of the next call, with the game moved to resolving. */
    private GameEvent.RollRequested nextRoll() {
        List<GameEvent> events = BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds);
        arena.play(events);
        return (GameEvent.RollRequested) events.get(0);
    }

    // =========================================================================================================== plain-data effects

    @Test
    void recoilIsAFractionOfTheDamageThatWasDone() {
        duel(charmander("doubleedge"), rattata("tackle"));
        round(ANA_FIRST, Action.move("doubleedge", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[] {3, 4, 5, 6}));
        // power 120 is 4d10: 18 x 5 = 90; 10 x 90 x 30 / 30 = 900, 900 / 50 = 18, + 2 = 20
        assertEquals(46 - 20, foe().hp());
        // 33 % of 20 = 6.6, rounded down
        assertEquals(50 - 6, ana().hp());
        assertTrue(arena.said("Charmander takes 6 damage from the recoil (44/50 HP)."));
    }

    @Test
    void drainHealsTheUserByAFractionOfTheDamage() {
        duel(hp(charmander("absorb"), 50).withHp(40), rattata("tackle"));
        round(ANA_FIRST, Action.move("absorb", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", 4));
        // power 20 is 1d6, rolled 4: 10 x 20 x 32 / 31 = 206, 206 / 50 = 4, + 2 = 6; half of it comes back
        assertEquals(46 - 6, foe().hp());
        assertEquals(40 + 3, ana().hp());
        assertTrue(arena.said("Charmander recovers 3 HP from the energy it drained (43/50 HP)."));
    }

    @Test
    void healingMovesRestoreHalfTheMaximumAndFailAtFullHealth() {
        duel(hp(charmander("recover"), 20), rattata("tackle"));
        round(ANA_FIRST, Action.move("recover"), Action.skip(), Answers.none());
        assertEquals(45, ana().hp(), "half of 50 is 25");
        round(ANA_FIRST, Action.move("recover"), Action.skip(), Answers.none());
        assertEquals(50, ana().hp(), "it stops at the maximum");
        round(ANA_FIRST, Action.move("recover"), Action.skip(), Answers.none());
        assertTrue(arena.said("But it failed!"));
        assertEquals(2, ana().moves().get(0).pp(), "the PP of the failed move is spent");
    }

    @Test
    void aMultiHitMoveRollsTheNumberOfHitsOnceAndTheDamageDiceOnceByDefault() {
        duel(charmander("doubleslap"), rattata("tackle"));
        round(ANA_FIRST, Action.move("doubleslap", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit", "ana-1-hits", "4 hits", "ana-1-dmg0", 3));
        // 2 to 5 hits (35/35/15/15 %): the die said 4 hits. Power 15 is 1d4, rolled 3: 10 x 15 x 30 / 30 = 150, 150 / 50 = 3, + 2 = 5 per hit
        assertEquals(46 - 4 * 5, foe().hp());
        assertTrue(arena.said("Hit 4 times!"));
        assertEquals(1, arena.asked.keySet().stream().filter(k -> k.contains("-dmg")).count(), "the damage dice were rolled once");
        GameState.RollRequest hits = arena.asked.get("b1-r1-ana-1-hits");
        assertEquals(6, hits.dice().sides(), "35/35/15/15 % is a d6: 1-2 two hits, 3-4 three, 5 four, 6 five (chapter 6.5)");
        assertEquals(List.of("2 hits", "3 hits", "4 hits", "5 hits"), hits.bands().stream().map(GameState.Band::label).toList());
    }

    @Test
    void withTheOtherSettingEveryHitRollsItsOwnDamageDice() {
        arena = new Arena(MoveBook.of(Moves.all()).with(CombatSettings.defaults().withMultihitDamageDice(CombatSettings.MultihitDamageDice.PER_HIT),
                TypeChart.standard(), DiceSettings.defaults()), "ana");
        duel(charmander("doubleslap"), rattata("tackle"));
        round(ANA_FIRST, Action.move("doubleslap", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit", "ana-1-hits", "4 hits",
                "ana-1-dmg0", 1, "ana-1-dmg1", 2, "ana-1-dmg2", 3, "ana-1-dmg3", 4));
        // 3 + 4 + 5 + 6 (a 1, 2, 3 and 4 on the 1d4)
        assertEquals(46 - 18, foe().hp());
    }

    @Test
    void aMoveThatAlwaysHitsTwiceHasNoRollForTheNumberOfHits() {
        duel(charmander("doublekick"), rattata("tackle"));
        round(ANA_FIRST, Action.move("doublekick", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", 6));
        assertTrue(arena.said("Hit 2 times!"));
        assertFalse(arena.asked.containsKey("b1-r1-ana-1-hits"));
    }

    @Test
    void aOneHitKnockoutNeedsLuckAndTheLevelsDecideTheOdds() {
        duel(charmander("fissure"), rattata("tackle"));
        round(ANA_FIRST, Action.move("fissure", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit"));
        // accuracy = 30 % + (20 - 18) = 32 %
        assertEquals(0.32, arena.asked.get("b1-r1-ana-1-hit0").trueChance(), 1e-9);
        assertEquals(0, foe().hp());
        assertTrue(arena.said("wild Rattata fainted!"));

        // a lower level than the target: it fails outright, with no roll
        arena = new Arena(MoveBook.of(Moves.all()), "ana");
        duel(level(charmander("fissure"), 10), rattata("tackle"));
        round(ANA_FIRST, Action.move("fissure", "wild-1"), Action.skip(), Answers.none());
        assertEquals(46, foe().hp());
        assertTrue(arena.asked.isEmpty());
        assertTrue(arena.said("Charmander's attack missed wild Rattata!"));
    }

    @Test
    void fixedDamageSkipsTheDiceAndTheTypeChartStillCanBlockIt() {
        duel(charmander("seismictoss", "dragonrage"), rattata("tackle"));
        round(ANA_FIRST, Action.move("seismictoss", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit"));
        assertEquals(46 - 20, foe().hp(), "Seismic Toss does the user's level: 20");
        assertTrue(arena.asked.keySet().stream().noneMatch(k -> k.contains("-dmg")), "no damage dice");
        round(ANA_FIRST, Action.move("dragonrage", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit"));
        assertEquals(46 - 20 - 40 < 0 ? 0 : 46 - 20 - 40, foe().hp(), "Dragon Rage does 40, and the damage stops at 0");

        arena = new Arena(MoveBook.of(Moves.all()), "ana");
        duel(charmander("seismictoss"), types(rattata("tackle"), PokemonType.GHOST));
        round(ANA_FIRST, Action.move("seismictoss", "wild-1"), Action.skip(), Answers.none());
        assertEquals(46, foe().hp(), "Fighting does not touch a Ghost");
        assertTrue(arena.said("It doesn't affect wild Rattata..."));
    }

    @Test
    void selfBoostsAndSelfDropsHappenAfterTheMoveHit() {
        duel(charmander("closecombat", "flamecharge"), rattata("tackle"));
        round(ANA_FIRST, Action.move("closecombat", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[] {5, 5, 5, 5}));
        // power 120 is 4d10 = 20 → 100; 10 x 100 x 30 / 30 = 1000, 1000 / 50 = 20, + 2 = 22, Fighting against Normal x2
        assertEquals(46 - 44, foe().hp());
        assertEquals(-1, ana().condition().stage("def"));
        assertEquals(-1, ana().condition().stage("spd"));
        assertTrue(arena.said("Charmander's Defense fell!"));

        // a side effect that always happens needs no roll (Flame Charge: Speed +1 on every hit)
        arena = new Arena(MoveBook.of(Moves.all()), "ana");
        duel(charmander("flamecharge"), rattata("tackle"));
        round(ANA_FIRST, Action.move("flamecharge", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", NINE));
        assertEquals(1, ana().condition().stage("spe"));
        assertEquals(90, ana().speed());
        assertTrue(arena.asked.keySet().stream().noneMatch(k -> k.contains("-side")));

        // and a miss gives nothing
        arena = new Arena(MoveBook.of(Moves.all()), "ana");
        Combatant blinded = charmander("closecombat");
        duel(blinded.withCondition(blinded.condition().withStage("accuracy", -6)), rattata("tackle"));
        round(ANA_FIRST, Action.move("closecombat", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "miss"));
        assertEquals(0, ana().condition().stage("def"));
    }

    @Test
    void priorityComesFromTheMoveDataAndPutsAMoveBeforeFasterPokemon() {
        assertEquals(1, arena.book.slot("quickattack").orElseThrow().priority());
        assertEquals(-6, arena.book.slot("roar").orElseThrow().priority());
        duel(charmander("tackle"), rattata("quickattack"));
        arena.round(ANA_FIRST).declare("ana-1", Action.move("tackle", "wild-1")).declare("wild-1", Action.move("quickattack", "ana-1"));
        arena.play(BattleDesk.startResolving(arena.state()));
        assertEquals(List.of("wild-1", "ana-1"), arena.battle().queue(), "Quick Attack goes first although Ana has the higher initiative");
    }

    @Test
    void aMoveThatSwitchesTheUserOutAsksTheOwnerForAReplacementBeforeAnythingElseHappens() {
        arena.start(Battle.Type.WILD, BattleSettings.defaults(),
                List.of(Arena.trainer("ana", List.of("ana-1"), List.of("ana-2")), Arena.wild("wild", "Rattata", "wild-1")),
                List.of(charmander("uturn"), arena.mon("ana-2", "ana", "Pidgey", 20, 40, List.of(PokemonType.NORMAL, PokemonType.FLYING), 28, 26, 24, 24, 56, "tackle"),
                        rattata("tackle")));
        arena.round(ANA_FIRST).declare("ana-1", Action.move("uturn", "wild-1")).declare("wild-1", Action.move("tackle", "ana-1"));
        arena.resolve(Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[] {4, 5, 6}));
        // power 70 is 3d8 = 15 → 75: 10 x 75 x 30 / 30 = 750, 750 / 50 = 15, + 2 = 17
        assertEquals(46 - 17, foe().hp());
        assertEquals(List.of("ana-1"), arena.battle().mustSwitch());
        assertEquals(List.of("wild-1"), arena.battle().queue(), "the foe's turn waits");
        assertThrows(BattleRefusedException.class, () -> BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds));
        assertThrows(BattleRefusedException.class, () -> BattleDesk.sendReplacement(arena.state(), Actor.player("u-bo"), "ana", "ana-2"), "only the owner chooses");

        arena.play(BattleDesk.sendReplacement(arena.state(), ANA, "ana", "ana-2"));
        assertEquals(List.of(), arena.battle().mustSwitch());
        assertEquals(List.of("ana-2"), arena.battle().participants().get("ana").active());
        arena.keepResolving(Answers.of("wild-1-hit0", "hit", "wild-1-dmg0", NINE));
        assertTrue(arena.mon("ana-2").hp() < 40, "the foe's Tackle hit the Pokémon that came in");
        assertEquals(50, arena.mon("ana-1").hp());
    }

    @Test
    void aMoveThatForcesASwitchSendsInARandomPokemonFromTheReserve() {
        arena.start(Battle.Type.WILD, BattleSettings.defaults(),
                List.of(Arena.trainer("ana", List.of("ana-1"), List.of()), Arena.wildWithReserve("wild", "Rattata", List.of("wild-1"), List.of("wild-2"))),
                List.of(charmander("roar"), rattata("tackle"), arena.mon("wild-2", "wild", "Pidgey", 18, 40, NORMAL, 28, 26, 24, 24, 56, "tackle")));
        arena.round(ANA_FIRST).declare("ana-1", Action.move("roar", "wild-1")).declare("wild-1", Action.skip());
        arena.resolve(Answers.none());
        assertEquals(List.of("wild-2"), arena.battle().participants().get("wild").active());
        assertEquals(List.of("wild-1"), arena.battle().participants().get("wild").reserve());
        assertTrue(arena.state().draws().stream().anyMatch(d -> d.purpose().endsWith("force-wild-1")), "the random choice is kept in the log");
        assertEquals(arena.log.replay(), arena.state());

        // nobody to send in: it fails
        arena = new Arena(MoveBook.of(Moves.all()), "ana");
        duel(charmander("roar"), rattata("tackle"));
        round(ANA_FIRST, Action.move("roar", "wild-1"), Action.skip(), Answers.none());
        assertTrue(arena.said("But it failed!"));
    }

    @Test
    void severalSideEffectsEachRollTheirOwnChance() {
        duel(charmander("firefang"), rattata("tackle"));
        round(ANA_FIRST, Action.move("firefang", "wild-1"), Action.move("tackle", "ana-1"),
                Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[] {6, 6}, "ana-1-side0", "fail", "ana-1-side1", "success"));
        assertTrue(arena.asked.containsKey("b1-r1-ana-1-side0") && arena.asked.containsKey("b1-r1-ana-1-side1"), "two rolls: burn and flinch");
        assertEquals(Status.NONE, foe().status(), "the burn did not happen");
        assertTrue(arena.said("wild Rattata flinched and couldn't move!"), "the flinch did, and the foe lost its turn");
        assertEquals(50, ana().hp());
    }

    @Test
    void aSideEffectThatLowersTheTargetsStatOnlyHappensWhenTheDieSaysSo() {
        duel(charmander("acid"), rattata("tackle"));
        round(ANA_FIRST, Action.move("acid"), Action.skip(), Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", NINE, "ana-1-side0", "success"));
        assertEquals(-1, foe().condition().stage("spd"));
        assertTrue(arena.said("wild Rattata's Sp. Def fell!"));
    }

    @Test
    void noSideEffectIsRolledForAPokemonThatFaintedFromTheHit() {
        duel(charmander("flamethrower"), hp(rattata("tackle"), 5));
        round(ANA_FIRST, Action.move("flamethrower", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[] {4, 5, 6}));
        assertEquals(0, foe().hp());
        assertFalse(arena.asked.containsKey("b1-r1-ana-1-side0"), "a fainted Pokémon cannot be burned, so nobody rolls");
    }

    @Test
    void struggleIsUsedWhenNoMoveHasPpLeftAndHurtsTheUserAQuarterOfItsMaxHp() {
        duel(charmander("tackle"), rattata("tackle"));
        Combatant empty = ana().withMoves(ana().moves().stream().map(m -> new Battle.MoveSlot(m.moveId(), m.name(), 0, m.maxPp(), m.priority(), m.target())).toList());
        arena.play(BattleDesk.change(arena.state(), ADMIN, empty));
        assertTrue(ana().outOfPp());
        round(ANA_FIRST, Action.struggle("wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", NINE));
        // typeless, power 50 (2d8 = 9 → 45): 10 x 45 x 30 / 30 = 450, / 50 = 9, + 2 = 11; no same-type bonus; it never misses
        assertEquals(46 - 11, foe().hp());
        assertEquals(50 - 12, ana().hp(), "1/4 of 50");
        assertTrue(arena.said("Charmander has no moves left and uses Struggle on wild Rattata."));
        assertTrue(arena.battle().tasks().isEmpty(), "Struggle is a rule of the game, not a task");
        assertEquals(List.of("hit", "crit"), arena.asked.get("b1-r1-ana-1-hit0").bands().stream().map(GameState.Band::label).toList());
    }

    @Test
    void aSelfDestructingMoveKnocksOutTheUser() {
        duel(charmander("explosion"), rattata("tackle"));
        round(ANA_FIRST, Action.move("explosion"), Action.skip(), Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[] {12, 12, 12, 12, 12, 12, 12, 12}));
        assertEquals(0, foe().hp());
        assertEquals(0, ana().hp());
        assertTrue(arena.said("Charmander takes 50 damage as it blew itself up (0/50 HP)."));
        assertTrue(arena.said("Charmander fainted!"));
        assertTrue(arena.battle().over());
    }

    @Test
    void highCritMovesAndMovesThatAlwaysCritChangeTheBands() {
        duel(charmander("slash", "frostbreath"), rattata("tackle"));
        round(ANA_FIRST, Action.move("slash", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[] {4, 5, 6}));
        GameState.RollRequest slash = arena.asked.get("b1-r1-ana-1-hit0");
        // crit stage +1 is 10 %: a 100 % move splits 90 % hit / 10 % crit, which is a d10 with the crit on the 10
        assertEquals(10, slash.dice().sides());
        assertEquals(new GameState.Band("crit", 10, 10), slash.bands().get(slash.bands().size() - 1));
        round(ANA_FIRST, Action.move("frostbreath", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "crit", "ana-1-dmg0", NINE));
        assertTrue(arena.said("A critical hit!"));
        GameState.RollRequest breath = arena.asked.get("b1-r2-ana-1-hit0");
        assertEquals(List.of("miss", "crit"), breath.bands().stream().map(GameState.Band::label).toList(), "every hit is a crit: no plain-hit band");
    }

    @Test
    void movesThatUseAnotherStatOrStealBoosts() {
        duel(charmander("bodypress", "spectralthief"), item(rattata("tackle"), null));
        round(ANA_FIRST, Action.move("bodypress", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[] {5, 5, 5}));
        // Body Press attacks with the Defense (28): power 80 is 3d10 = 15 → 75; 10 x 75 x 28 / 30 = 700, 700 / 50 = 14, + 2 = 16, Fighting against Normal x2
        assertEquals(46 - 32, foe().hp());

        arena = new Arena(MoveBook.of(Moves.all()), "ana");
        duel(charmander("spectralthief"), types(rattata("tackle"), PokemonType.FIRE).withCondition(
                new Battle.Condition(Status.NONE, 0, Map.of("atk", 2), Map.of(), null)));
        round(ANA_FIRST, Action.move("spectralthief", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "crit", "ana-1-dmg0", new int[] {4, 5, 6}));
        assertEquals(0, foe().condition().stage("atk"));
        assertEquals(2, ana().condition().stage("atk"));
    }

    // ======================================================================================================== the 25 scripted moves

    @Test
    void restHealsEverythingCuresTheStatusAndPutsTheUserToSleepForTwoTurns() {
        duel(status(hp(charmander("rest"), 20), Status.BURN, 0), rattata("tackle"));
        round(ANA_FIRST, Action.move("rest"), Action.skip(), Answers.none());
        assertEquals(50, ana().hp());
        assertEquals(Status.SLEEP, ana().status());
        assertEquals(2, ana().condition().statusTurns());
        assertTrue(arena.said("Charmander went to sleep and became healthy!"));
        assertTrue(arena.said("Charmander is no longer burned."));

    }

    @Test
    void restFailsAtFullHealth() {
        duel(charmander("rest"), rattata("tackle"));
        round(ANA_FIRST, Action.move("rest"), Action.skip(), Answers.none());
        assertTrue(arena.said("But it failed!"));
        assertEquals(Status.NONE, ana().status());
    }

    @Test
    void restCuresEveryStatus() {
        for (Status status : List.of(Status.BURN, Status.POISON, Status.TOXIC, Status.PARALYSIS, Status.FREEZE)) {
            arena = new Arena(MoveBook.of(Moves.all()), "ana");
            duel(status(hp(charmander("rest"), 20), status, 0), rattata("tackle"));
            // a frozen Pokémon has to thaw before it can move
            round(ANA_FIRST, Action.move("rest"), Action.skip(), status == Status.FREEZE ? Answers.of("ana-1-thaw", "success") : Answers.of("ana-1-paralysis", "fail"));
            assertEquals(Status.SLEEP, ana().status(), status.name());
            assertEquals(50, ana().hp(), status.name());
        }
    }

    @Test
    void sleepTalkCallsAnotherMoveOfTheUserWhileItSleeps() {
        duel(status(charmander("sleeptalk", "ember"), Status.SLEEP, 2), rattata("tackle"));
        round(ANA_FIRST, Action.move("sleeptalk"), Action.skip(),
                Answers.of("call-hit0", 10, "call-dmg0", NINE, "call-side0", "fail"));
        assertTrue(arena.said("Charmander uses Ember in its sleep!"));
        // Ember against Normal: 11 x 1.5 (same type) = 16
        assertEquals(46 - 16, foe().hp());
        assertEquals(1, ana().condition().statusTurns(), "the sleep counts down");
        assertEquals(9, ana().moves().get(0).pp(), "Sleep Talk's PP is spent, not Ember's");
        assertEquals(25, ana().moves().get(1).pp());
    }

    @Test
    void sleepTalkFailsWhenTheUserIsAwake() {
        duel(charmander("sleeptalk", "ember"), rattata("tackle"));
        round(ANA_FIRST, Action.move("sleeptalk"), Action.skip(), Answers.none());
        assertTrue(arena.said("But it failed!"));
        assertEquals(46, foe().hp());
    }

    @Test
    void helpingHandMakesTheAlliesNextMoveHitOneAndAHalfTimesHarder() {
        startWithAlly(charmander("helpinghand"), arena.mon("bo-1", "bo", "Squirtle", 20, 50, List.of(PokemonType.WATER), 30, 28, 28, 30, 43, "tackle"));
        arena.round(Map.of("ana", 12, "bo", 10, "wild", 5)).declare("ana-1", Action.move("helpinghand", "bo-1")).declare("bo-1", Action.move("tackle", "wild-1"))
                .declare("wild-1", Action.skip());
        arena.resolve(Answers.of("bo-1-hit0", 10, "bo-1-dmg0", NINE));
        // Tackle is 11 (see the first test of this class), x 1.5 = 16
        assertEquals(46 - 16, foe().hp());
        assertTrue(arena.said("Charmander is ready to help Bo's Squirtle!"));
        assertFalse(arena.mon("bo-1").condition().has(Volatiles.HELPING_HAND), "used up by the move");

        // it fails when the ally has moved already
        arena = new Arena(MoveBook.of(Moves.all()), "ana", "bo");
        startWithAlly(charmander("helpinghand"), arena.mon("bo-1", "bo", "Squirtle", 20, 50, List.of(PokemonType.WATER), 30, 28, 28, 30, 43, "tackle"));
        arena.round(Map.of("ana", 12, "bo", 10, "wild", 5)).declare("ana-1", Action.move("helpinghand", "bo-1")).declare("bo-1", Action.move("tackle", "wild-1"))
                .declare("wild-1", Action.skip());
        arena.play(BattleDesk.startResolving(arena.state()));
        arena.play(BattleDesk.reorder(arena.state(), ADMIN, List.of("bo-1", "ana-1", "wild-1")));
        arena.keepResolving(Answers.of("bo-1-hit0", 10, "bo-1-dmg0", NINE));
        assertTrue(arena.said("But it failed!"));
        assertEquals(46 - 11, foe().hp());
    }

    private void startWithAlly(Combatant ana, Combatant bo) {
        arena.start(Battle.Type.WILD, BattleSettings.defaults(),
                List.of(Arena.trainer("ana", List.of("ana-1"), List.of()), Arena.trainer("bo", List.of("bo-1"), List.of()), Arena.wild("wild", "Rattata", "wild-1")),
                List.of(ana, bo, rattata("tackle")));
    }

    @Test
    void gigaImpactAndHyperBeamMakeTheUserRechargeOnTheNextTurn() {
        duel(charmander("gigaimpact", "tackle"), hp(rattata("tackle"), 46).withHp(46));
        // Rattata has 46 HP; Giga Impact is 5d12 = 5 x 6 = 30 → 150: 10 x 150 x 30 / 30 = 1500, 1500 / 50 = 30, + 2 = 32
        round(ANA_FIRST, Action.move("gigaimpact", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[] {6, 6, 6, 6, 6}));
        assertEquals(46 - 32, foe().hp());
        assertTrue(ana().condition().has(Volatiles.RECHARGE));
        // the next turn is lost, whatever was chosen, and no PP is spent
        round(ANA_FIRST, Action.move("tackle", "wild-1"), Action.skip(), Answers.none());
        assertTrue(arena.said("Charmander must recharge!"));
        assertFalse(ana().condition().has(Volatiles.RECHARGE));
        assertEquals(35, ana().moves().get(1).pp());
        // and then it moves again
        round(ANA_FIRST, Action.move("tackle", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", NINE));
        assertEquals(34, ana().moves().get(1).pp());
    }

    @Test
    void hyperBeamDoesNotMakeTheUserRechargeWhenItMisses() {
        duel(charmander("hyperbeam"), rattata("tackle"));
        round(ANA_FIRST, Action.move("hyperbeam", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "miss"));
        assertFalse(ana().condition().has(Volatiles.RECHARGE));
    }

    @Test
    void everyMoveThatMakesTheUserRechargeWorksThroughTheSameCondition() {
        duel(charmander("blastburn"), rattata("tackle"));
        round(ANA_FIRST, Action.move("blastburn", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[] {6, 6, 6, 6, 6}));
        assertTrue(ana().condition().has(Volatiles.RECHARGE));
        assertTrue(arena.book.runsCondition("volatileStatus:mustrecharge"));
        assertEquals(MoveBook.Handling.IMPLEMENTED, arena.book.handling(arena.book.entry("blastburn").orElseThrow()));
    }

    @Test
    void protectBlocksTheNextMoveAndWorksLessOftenWhenUsedInARow() {
        duel(charmander("protect", "tackle"), rattata("tackle"));
        // Protect has priority +4, so it comes before the foe's Tackle, which is stopped without a hit roll
        round(ANA_FIRST, Action.move("protect"), Action.move("tackle", "ana-1"), Answers.none());
        assertEquals(2, arena.lines().stream().filter(l -> l.contains("Charmander protected itself!")).count(), "once when it is used, once when it blocks");
        assertEquals(50, ana().hp());
        assertTrue(arena.asked.isEmpty(), "no hit roll for a move that is blocked");
        assertEquals(1, ana().condition().volatileValue(Volatiles.STALL));
        assertFalse(ana().condition().has(Volatiles.PROTECT), "it ends with the round");

        // used again at once: a 1 in 3 chance (a d6, 5 and 6), rolled by Ana
        round(ANA_FIRST, Action.move("protect"), Action.move("tackle", "ana-1"), Answers.of("ana-1-stall", "fail", "wild-1-hit0", 10, "wild-1-dmg0", NINE));
        assertTrue(arena.said("But it failed!"));
        assertTrue(ana().hp() < 50, "this time the Tackle got through");
        assertEquals(0, ana().condition().volatileValue(Volatiles.STALL), "the streak starts again");
    }

    @Test
    void protectFailsForTheLastPokemonToMove() {
        duel(charmander("protect"), rattata("tackle"));
        arena.round(ANA_FIRST).declare("ana-1", Action.move("protect")).declare("wild-1", Action.skip());
        arena.play(BattleDesk.startResolving(arena.state()));
        arena.play(BattleDesk.reorder(arena.state(), ADMIN, List.of("wild-1", "ana-1")));
        arena.keepResolving(Answers.none());
        assertTrue(arena.said("But it failed!"));
    }

    @Test
    void endureLeavesOneHpFromAHitThatWouldKnockOutThePokemon() {
        duel(hp(charmander("endure"), 5), rattata("tackle"));
        round(ANA_FIRST, Action.move("endure"), Action.move("tackle", "ana-1"), Answers.of("wild-1-hit0", 10, "wild-1-dmg0", NINE));
        assertEquals(1, ana().hp());
        assertTrue(arena.said("Charmander endured the hit!"));
    }

    @Test
    void substituteCostsAQuarterOfTheHpAndTakesTheHitsForTheUser() {
        duel(arena.mon("ana-1", "ana", "Charmander", 20, 100, FIRE, 30, 28, 32, 30, 60, "substitute"), rattata("tackle"));
        round(ANA_FIRST, Action.move("substitute"), Action.move("tackle", "ana-1"), Answers.of("wild-1-hit0", 10, "wild-1-dmg0", NINE));
        // 100 / 4 = 25 HP for the decoy; the Tackle does 15 (9 x 45 x 29 / 28 = 419, / 50 = 8, + 2 = 10, x 1.5 for the same type)
        assertEquals(75, ana().hp());
        assertEquals(10, ana().condition().volatileValue(Volatiles.SUBSTITUTE));
        assertTrue(arena.said("Charmander put in a substitute!"));
        assertTrue(arena.said("Charmander's substitute took the hit!"));
        round(ANA_FIRST, Action.skip(), Action.move("tackle", "ana-1"), Answers.of("wild-1-hit0", 10, "wild-1-dmg0", NINE));
        assertEquals(75, ana().hp(), "the decoy broke and what was left of the hit is lost");
        assertFalse(ana().condition().has(Volatiles.SUBSTITUTE));
        assertTrue(arena.said("Charmander's substitute faded!"));
    }

    @Test
    void substituteFailsWhenTheUserIsTooWeakOrAlreadyHasOne() {
        duel(hp(charmander("substitute"), 12), rattata("tackle"));
        round(ANA_FIRST, Action.move("substitute"), Action.skip(), Answers.none());
        assertTrue(arena.said("It was too weak to make a substitute!"));
        assertEquals(12, ana().hp());
    }

    @Test
    void teraBlastIsNormalAndUsesTheBetterOfAttackAndSpecialAttack() {
        // no Terastal types in the game: Normal. This user has more Attack than Sp. Atk, so it is physical
        Combatant strong = arena.mon("wild-1", "wild", "Rattata", 18, 46, NORMAL, 40, 30, 20, 31, 40, "terablast");
        duel(hp(charmander("tackle"), 50), strong);
        round(WILD_FIRST, Action.skip(), Action.move("terablast", "ana-1"), Answers.of("wild-1-hit0", 10, "wild-1-dmg0", new int[] {5, 5, 5}));
        // power 80 is 3d10 = 15 → 75: 9 x 75 x 40 / 28 = 964, 964 / 50 = 19, + 2 = 21, x 1.5 (Normal type user)
        assertEquals(50 - 31, ana().hp());
    }

    @Test
    void facadeDoublesItsPowerWhenTheUserHasAStatusAndIgnoresTheBurn() {
        duel(status(charmander("facade"), Status.BURN, 0), rattata("tackle"));
        arena.round(ANA_FIRST).declare("ana-1", Action.move("facade", "wild-1")).declare("wild-1", Action.skip());
        arena.play(BattleDesk.startResolving(arena.state()));
        arena.play(BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds));
        arena.play(RollDesk.enter(arena.state(), ADMIN, "b1-r1-ana-1-hit0", List.of(10), null));
        GameEvent.RollRequested dice = nextRoll();
        assertEquals("4d12", dice.dice().toString(), "power 70 doubled is 140: 4d12");
        arena.play(RollDesk.enter(arena.state(), ADMIN, dice.requestId(), List.of(6, 6, 6, 6), null));
        arena.keepResolving(Answers.none());
        // 24 → 120: 10 x 120 x 30 / 30 = 1200, 1200 / 50 = 24, + 2 = 26; the burn does not halve it
        assertEquals(46 - 26, foe().hp());
    }

    @Test
    void facadeIsNormalPowerWithoutAStatus() {
        duel(charmander("facade"), rattata("tackle"));
        arena.round(ANA_FIRST).declare("ana-1", Action.move("facade", "wild-1")).declare("wild-1", Action.skip());
        arena.play(BattleDesk.startResolving(arena.state()));
        arena.play(BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds));
        arena.play(RollDesk.enter(arena.state(), ADMIN, "b1-r1-ana-1-hit0", List.of(10), null));
        assertEquals("3d8", nextRoll().dice().toString());
    }

    @Test
    void snoreOnlyWorksWhileAsleep() {
        duel(charmander("snore"), rattata("tackle"));
        round(ANA_FIRST, Action.move("snore", "wild-1"), Action.skip(), Answers.none());
        assertTrue(arena.said("But it failed!"));
        assertEquals(46, foe().hp());

        arena = new Arena(MoveBook.of(Moves.all()), "ana");
        duel(status(charmander("snore"), Status.SLEEP, 2), rattata("tackle"));
        round(ANA_FIRST, Action.move("snore", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", new int[] {4, 5}, "ana-1-side0", "fail"));
        // power 50 is 2d8 = 9 → 45: 10 x 45 x 32 / 31 = 464, / 50 = 9, + 2 = 11
        assertEquals(46 - 11, foe().hp());
        assertEquals(1, ana().condition().statusTurns(), "the sleep counts down all the same");
        assertEquals(Status.SLEEP, ana().status());
    }

    @Test
    void roundHitsTwiceAsHardWhenAnAllyUsedItEarlierInTheRound() {
        startWithAlly(charmander("round"), arena.mon("bo-1", "bo", "Squirtle", 20, 50, List.of(PokemonType.WATER), 30, 28, 28, 30, 43, "round"));
        arena.round(Map.of("ana", 10, "bo", 15, "wild", 5)).declare("ana-1", Action.move("round", "wild-1")).declare("bo-1", Action.move("round", "wild-1"))
                .declare("wild-1", Action.skip());
        arena.resolve(Answers.of("bo-1-hit0", 10, "bo-1-dmg0", new int[] {5, 5}, "ana-1-hit0", 10, "ana-1-dmg0", new int[] {4, 5, 6, 5}));
        assertEquals("2d10", arena.asked.get("b1-r1-bo-1-dmg0").dice().toString(), "the first one: power 60");
        assertEquals("4d10", arena.asked.get("b1-r1-ana-1-dmg0").dice().toString(), "the second one: power 120");
    }

    @Test
    void attractMakesATargetOfTheOtherGenderMayNotMove() {
        duel(gender(charmander("attract"), "M"), gender(rattata("tackle"), "F"));
        round(ANA_FIRST, Action.move("attract", "wild-1"), Action.skip(), Answers.none());
        assertTrue(foe().condition().has(Volatiles.ATTRACT));
        assertTrue(arena.said("wild Rattata fell in love with Ana's Charmander!"));
        // each turn it may be unable to move: 50 %
        round(ANA_FIRST, Action.skip(), Action.move("tackle", "ana-1"), Answers.of("wild-1-love", "success"));
        assertTrue(arena.said("wild Rattata is immobilized by love!"));
        assertEquals(50, ana().hp());
    }

    @Test
    void attractFailsBetweenTheSameGenderOrWithoutGender() {
        duel(gender(charmander("attract"), "M"), gender(rattata("tackle"), "M"));
        round(ANA_FIRST, Action.move("attract", "wild-1"), Action.skip(), Answers.none());
        assertTrue(arena.said("But it failed!"));
        assertFalse(foe().condition().has(Volatiles.ATTRACT));
    }

    @Test
    void hiddenPowerTakesItsTypeFromTheIvsOfTheUser() {
        duel(charmander("hiddenpower").withBuild(charmander("hiddenpower").build().withHiddenPower(PokemonType.ICE)),
                types(rattata("tackle"), PokemonType.GRASS, PokemonType.POISON));
        round(ANA_FIRST, Action.move("hiddenpower", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", new int[] {5, 6}));
        // power 60 is 2d10 = 11 → 55: 10 x 55 x 32 / 31 = 567, / 50 = 11, + 2 = 13; Ice against Grass x2
        assertEquals(46 - 26, foe().hp());
        assertTrue(arena.said("It's super effective!"));
    }

    @Test
    void hiddenPowerTypeFromIvsFollowsTheOfficialFormula() {
        assertEquals(PokemonType.FIGHTING, net.quimmilho.pokednd.core.rules.HiddenPower.type(net.quimmilho.pokednd.core.rules.StatBlock.all(0)));
        assertEquals(PokemonType.DARK, net.quimmilho.pokednd.core.rules.HiddenPower.type(net.quimmilho.pokednd.core.rules.StatBlock.all(31)));
        // only the Sp. Def IV is odd: 32 x 15 / 63 = 7 → Steel
        assertEquals(PokemonType.STEEL, net.quimmilho.pokednd.core.rules.HiddenPower.type(new net.quimmilho.pokednd.core.rules.StatBlock(30, 30, 30, 30, 31, 30)));
    }

    @Test
    void returnGetsStrongerWithFriendshipAndFrustrationWeakerThePowerBecomesDice() {
        duel(charmander("return", "frustration").withBuild(charmander("return").build().withFriendship(255)), rattata("tackle"));
        arena.round(ANA_FIRST).declare("ana-1", Action.move("return", "wild-1")).declare("wild-1", Action.skip());
        arena.play(BattleDesk.startResolving(arena.state()));
        arena.play(BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds));
        arena.play(RollDesk.enter(arena.state(), ADMIN, "b1-r1-ana-1-hit0", List.of(10), null));
        assertEquals("3d12", nextRoll().dice().toString(), "friendship 255 x 2 / 5 = 102: 3d12");

        arena = new Arena(MoveBook.of(Moves.all()), "ana");
        duel(charmander("return", "frustration").withBuild(charmander("return").build().withFriendship(255)), rattata("tackle"));
        arena.round(ANA_FIRST).declare("ana-1", Action.move("frustration", "wild-1")).declare("wild-1", Action.skip());
        arena.play(BattleDesk.startResolving(arena.state()));
        arena.play(BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds));
        arena.play(RollDesk.enter(arena.state(), ADMIN, "b1-r1-ana-1-hit0", List.of(10), null));
        assertEquals("d4", nextRoll().dice().toString(), "(255 - 255) x 2 / 5 = 0, at least power 1: a d4");
    }

    @Test
    void endeavorBringsTheTargetDownToTheUsersHp() {
        duel(hp(charmander("endeavor"), 20), rattata("tackle"));
        round(ANA_FIRST, Action.move("endeavor", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit"));
        assertEquals(20, foe().hp());
        assertTrue(arena.asked.keySet().stream().noneMatch(k -> k.contains("-dmg")), "no dice");
        // it fails when the target has no more HP than the user
        round(ANA_FIRST, Action.move("endeavor", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit"));
        assertTrue(arena.said("But it failed!"));
        assertEquals(20, foe().hp());
    }

    @Test
    void secretPowerMayParalyzeBecauseThereIsNoTerrain() {
        duel(charmander("secretpower"), rattata("tackle"));
        round(ANA_FIRST, Action.move("secretpower", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", new int[] {4, 5, 6}, "ana-1-side0", "success"));
        assertEquals(Status.PARALYSIS, foe().status());
    }

    @Test
    void cursedByAGhostLosesAQuarterEachRoundAndAnyoneElseTradesSpeedForAttackAndDefense() {
        duel(charmander("curse"), rattata("tackle"));
        round(ANA_FIRST, Action.move("curse", "wild-1"), Action.skip(), Answers.none());
        assertEquals(1, ana().condition().stage("atk"));
        assertEquals(1, ana().condition().stage("def"));
        assertEquals(-1, ana().condition().stage("spe"));
        assertEquals(40, ana().speed(), "Speed 60 at -1 is 40");

        arena = new Arena(MoveBook.of(Moves.all()), "ana");
        duel(types(charmander("curse"), PokemonType.GHOST), rattata("tackle"));
        round(ANA_FIRST, Action.move("curse", "wild-1"), Action.skip(), Answers.none());
        assertEquals(25, ana().hp(), "the Ghost pays half of its HP");
        assertTrue(foe().condition().has(Volatiles.CURSE));
        // 46 / 4 = 11 at the end of the round, and again in the next
        assertEquals(46 - 11, foe().hp());
        round(ANA_FIRST, Action.skip(), Action.skip(), Answers.none());
        assertEquals(46 - 22, foe().hp());
    }

    @Test
    void knockOffHitsHarderAndTakesTheHeldItem() {
        duel(charmander("knockoff"), item(rattata("tackle"), "oran_berry"));
        round(ANA_FIRST, Action.move("knockoff", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", new int[] {6, 6}));
        // power 65 is 2d12 = 12 → 60: 10 x 60 x 30 / 30 = 600, / 50 = 12, + 2 = 14, x 1.5 because of the item
        assertEquals(46 - 21, foe().hp());
        assertEquals(null, foe().condition().heldItem());
        assertTrue(arena.said("Charmander knocked off wild Rattata's Oran Berry!"));

        arena = new Arena(MoveBook.of(Moves.all()), "ana");
        duel(charmander("knockoff"), rattata("tackle"));
        round(ANA_FIRST, Action.move("knockoff", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", new int[] {6, 6}));
        assertEquals(46 - 14, foe().hp(), "no item, no bonus");
    }

    @Test
    void uproarLastsThreeTurnsLocksTheUserInAndKeepsEveryoneAwake() {
        // a big enough foe to survive three Uproars
        duel(charmander("uproar", "tackle"), arena.mon("wild-1", "wild", "Snorlax", 40, 400, NORMAL, 29, 30, 33, 31, 40, "hypnosis", "tackle"));
        Answers dice = Answers.of("ana-1-hit0", 10, "ana-1-dmg0", new int[] {1, 1, 1});
        round(ANA_FIRST, Action.move("uproar", "wild-1"), Action.skip(), dice);
        assertEquals(2, ana().condition().volatileValue(Volatiles.UPROAR));
        assertTrue(arena.said("Charmander caused an uproar!"));
        assertEquals(9, ana().moves().get(0).pp());
        // the next turn it uses Uproar again whatever was chosen, without spending PP
        round(ANA_FIRST, Action.move("tackle", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", new int[] {1, 1, 1}));
        assertEquals(1, ana().condition().volatileValue(Volatiles.UPROAR));
        assertEquals(9, ana().moves().get(0).pp());
        assertEquals(35, ana().moves().get(1).pp(), "Tackle was not used");
        // the third turn is the last
        round(ANA_FIRST, Action.skip(), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", new int[] {1, 1, 1}));
        assertFalse(ana().condition().has(Volatiles.UPROAR));
        assertTrue(arena.said("Charmander calmed down."));
    }

    @Test
    void anUproarWakesSleepersAndStopsPokemonFromFallingAsleep() {
        duel(charmander("uproar"), rattata("hypnosis").withCondition(new Battle.Condition(Status.SLEEP, 3, Map.of(), Map.of(), null)));
        round(ANA_FIRST, Action.move("uproar", "wild-1"), Action.move("hypnosis", "ana-1"),
                Answers.of("ana-1-hit0", 10, "ana-1-dmg0", new int[] {1, 1, 1}, "wild-1-hit0", "hit"));
        assertEquals(Status.NONE, foe().status(), "the sleeper woke up in the uproar");
        assertTrue(arena.said("wild Rattata woke up in the uproar!"));
        assertEquals(Status.NONE, ana().status(), "and Hypnosis cannot put anybody to sleep while it lasts");
        assertTrue(arena.said("But it failed!"));
    }

    @Test
    void covetTakesTheTargetsItemWhenTheUserHasNone() {
        duel(charmander("covet"), item(rattata("tackle"), "oran_berry"));
        round(ANA_FIRST, Action.move("covet", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", new int[] {5, 5}));
        assertEquals("oran_berry", ana().condition().heldItem());
        assertEquals(null, foe().condition().heldItem());
        assertTrue(arena.said("Charmander stole wild Rattata's Oran Berry!"));

        // it takes nothing from a target when the user already holds something
        arena = new Arena(MoveBook.of(Moves.all()), "ana");
        duel(item(charmander("covet"), "leftovers"), item(rattata("tackle"), "oran_berry"));
        round(ANA_FIRST, Action.move("covet", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", new int[] {5, 5}));
        assertEquals("leftovers", ana().condition().heldItem());
        assertEquals("oran_berry", foe().condition().heldItem());
    }

    @Test
    void rolloutRepeatsForFiveTurnsAndDoublesItsPowerEveryTime() {
        duel(charmander("rollout"), arena.mon("wild-1", "wild", "Snorlax", 40, 1000, NORMAL, 29, 30, 33, 31, 40, "tackle"));
        String[] dice = {"d10", "2d10", "4d10", "8d12", "8d12"};
        int[][] faces = {{5}, {5, 5}, {5, 5, 5, 5}, {1, 1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1, 1}};
        for (int turn = 0; turn < 5; turn++) {
            Action chosen = turn == 0 ? Action.move("rollout", "wild-1") : Action.skip(); // after the first use it is not chosen any more
            round(ANA_FIRST, chosen, Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", faces[turn]));
            assertEquals(dice[turn], arena.asked.get("b1-r" + (turn + 1) + "-ana-1-dmg0").dice().toString(), "turn " + (turn + 1) + ": power 30 x 2^" + turn);
        }
        assertFalse(ana().condition().has(Volatiles.ROLLOUT), "five turns are all there are");
        assertEquals(19, ana().moves().get(0).pp(), "one PP for the whole run");
    }

    @Test
    void rolloutEndsWhenItMisses() {
        duel(charmander("rollout"), arena.mon("wild-1", "wild", "Snorlax", 40, 1000, NORMAL, 29, 30, 33, 31, 40, "tackle"));
        round(ANA_FIRST, Action.move("rollout", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", 5));
        assertTrue(ana().condition().has(Volatiles.ROLLOUT));
        round(ANA_FIRST, Action.skip(), Action.skip(), Answers.of("ana-1-hit0", "miss"));
        assertFalse(ana().condition().has(Volatiles.ROLLOUT));
        assertEquals(0, ana().condition().volatileValue(Volatiles.ROLLOUT_HITS));
    }

    @Test
    void stompingTantrumDoublesItsPowerAfterAMoveThatFailed() {
        duel(charmander("stompingtantrum", "tackle"), arena.mon("wild-1", "wild", "Snorlax", 40, 1000, NORMAL, 29, 30, 33, 31, 40, "tackle"));
        round(ANA_FIRST, Action.move("tackle", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", NINE));
        round(ANA_FIRST, Action.move("stompingtantrum", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", new int[] {5, 5, 5}));
        assertEquals("3d8", arena.asked.get("b1-r2-ana-1-dmg0").dice().toString(), "after a move that worked: power 75");

        Combatant blinded = charmander("stompingtantrum", "tackle");
        arena = new Arena(MoveBook.of(Moves.all()), "ana");
        duel(blinded.withCondition(blinded.condition().withStage("accuracy", -6)), arena.mon("wild-1", "wild", "Snorlax", 40, 1000, NORMAL, 29, 30, 33, 31, 40, "tackle"));
        round(ANA_FIRST, Action.move("tackle", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "miss"));
        assertTrue(ana().condition().has(Volatiles.LAST_FAILED));
        round(ANA_FIRST, Action.move("stompingtantrum", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", "hit", "ana-1-dmg0", new int[] {5, 5, 5, 5, 5}));
        assertEquals("5d12", arena.asked.get("b1-r2-ana-1-dmg0").dice().toString(), "after a miss: power 150");
        assertFalse(ana().condition().has(Volatiles.LAST_FAILED), "and it hit this time");
    }

    @Test
    void naturalGiftUsesTheBerryTheUserHoldsAndUsesItUp() {
        duel(item(charmander("naturalgift"), "oran_berry"), rattata("tackle"));
        arena.round(ANA_FIRST).declare("ana-1", Action.move("naturalgift", "wild-1")).declare("wild-1", Action.skip());
        arena.play(BattleDesk.startResolving(arena.state()));
        arena.play(BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds));
        arena.play(RollDesk.enter(arena.state(), ADMIN, "b1-r1-ana-1-hit0", List.of(10), null));
        assertEquals("3d10", nextRoll().dice().toString(), "the berry gives power 80: 3d10");
        arena.play(RollDesk.enter(arena.state(), ADMIN, "b1-r1-ana-1-dmg0", List.of(5, 5, 5), null));
        arena.keepResolving(Answers.none());
        assertEquals(null, ana().condition().heldItem());
        assertTrue(arena.said("Charmander's Oran Berry was used up!"));
        // power 80 → 75: 10 x 75 x 30 / 30 = 750, 750 / 50 = 15, + 2 = 17; the berry makes the move Poison, which is neutral against Normal
        assertEquals(46 - 17, foe().hp());

        arena = new Arena(MoveBook.of(Moves.all(), List.of(Moves.berry("oran_berry", 80, "Poison"))), "ana");
        duel(charmander("naturalgift"), rattata("tackle"));
        round(ANA_FIRST, Action.move("naturalgift", "wild-1"), Action.skip(), Answers.none());
        assertTrue(arena.said("But it failed!"));
    }

    // ================================================================================================================= the admin

    @Test
    void aScriptedMoveThatIsNotWrittenRunsItsPlainPartsAndLeavesTheRestToTheAdmin() {
        duel(charmander("brickbreak"), rattata("tackle"));
        round(ANA_FIRST, Action.move("brickbreak", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", new int[] {4, 5, 6}));
        // Fighting against Normal x2: power 75 is 3d8 = 15 → 75: 750 / 50 = 15, + 2 = 17, x 2
        assertEquals(46 - 34, foe().hp());
        assertEquals(1, arena.battle().tasks().size());
        Battle.AdminTask task = arena.battle().tasks().get(0);
        assertEquals("brickbreak", task.moveId());
        assertEquals("ana-1", task.actorId());
        assertTrue(task.text().contains("Brick Break"));
        assertTrue(arena.said("The admin has to apply by hand: Apply Brick Break: Brick Break (test move)."));

        // the player cannot close it; the admin can
        assertThrows(BattleRefusedException.class, () -> BattleDesk.completeTask(arena.state(), ANA, task.id()));
        arena.play(BattleDesk.completeTask(arena.state(), ADMIN, task.id()));
        assertEquals(List.of(), arena.battle().tasks());
        assertThrows(BattleRefusedException.class, () -> BattleDesk.completeTask(arena.state(), ADMIN, task.id()), "it is closed already");
    }

    @Test
    void aMoveTheBookDoesNotKnowBecomesATaskAndNeverACrash() {
        duel(charmander("mysterymove"), rattata("tackle"));
        round(ANA_FIRST, Action.move("mysterymove", "wild-1"), Action.skip(), Answers.none());
        assertEquals(1, arena.battle().tasks().size());
        assertTrue(arena.battle().tasks().get(0).text().contains("Mysterymove"));
        assertEquals(46, foe().hp());
        assertTrue(arena.asked.isEmpty());
        assertEquals(Battle.Step.ROUND_END, arena.battle().step(), "the round goes on");
        assertEquals(9, ana().moves().get(0).pp(), "the move still used its PP");
    }

    @Test
    void aMoveWhoseDataHasNoEffectIsCompletelyLeftToTheAdmin() {
        var zmove = new net.quimmilho.pokednd.core.importer.Catalog.MoveEntry("breakneckblitz", "Breakneck Blitz", "Normal", "Physical", 100,
                java.util.Optional.empty(), 1, 0, "normal", "A Z-Move.", net.quimmilho.pokednd.core.importer.Catalog.EffectClass.MANUAL, List.of("z_max"),
                List.of(), null);
        arena = new Arena(MoveBook.of(java.util.stream.Stream.concat(Moves.all().stream(), java.util.stream.Stream.of(zmove)).toList()), "ana");
        duel(charmander("breakneckblitz"), rattata("tackle"));
        round(ANA_FIRST, Action.move("breakneckblitz", "wild-1"), Action.skip(), Answers.none());
        assertEquals(46, foe().hp(), "nothing runs by itself");
        assertEquals(1, arena.battle().tasks().size());
        assertTrue(arena.battle().tasks().get(0).text().contains("A Z-Move."));
    }

    @Test
    void aMoveWithATypeTheChartDoesNotKnowIsLeftToTheAdmin() {
        var odd = Moves.auto("curseodd", "Odd Move", "???", "Special", 50, 100, 10, 0, "normal", "{\"secondary\":null}");
        arena = new Arena(MoveBook.of(java.util.stream.Stream.concat(Moves.all().stream(), java.util.stream.Stream.of(odd)).toList()), "ana");
        duel(charmander("curseodd"), rattata("tackle"));
        round(ANA_FIRST, Action.move("curseodd", "wild-1"), Action.skip(), Answers.none());
        assertEquals(1, arena.battle().tasks().size());
    }

    @Test
    void aTaskIsKeptWhenTheSameMoveIsUsedAgainInALaterRoundOfTheSameBattle() {
        duel(charmander("brickbreak"), rattata("tackle"));
        round(ANA_FIRST, Action.move("brickbreak", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", new int[] {1, 1, 1}));
        round(ANA_FIRST, Action.move("brickbreak", "wild-1"), Action.skip(), Answers.of("ana-1-hit0", 10, "ana-1-dmg0", new int[] {1, 1, 1}));
        assertEquals(2, arena.battle().tasks().size(), "one task for each use");
        assertEquals(arena.log.replay(), arena.state());
    }

    @Test
    void theAdminCancellingAMoveInTheMiddleDropsItsOpenRolls() {
        duel(charmander("ember"), rattata("tackle"));
        arena.round(ANA_FIRST).declare("ana-1", Action.move("ember", "wild-1")).declare("wild-1", Action.skip());
        arena.play(BattleDesk.startResolving(arena.state()));
        arena.play(BattleDesk.resolveNext(arena.state(), arena.book, arena.rolls, Arena.NOW, arena.seeds));
        assertEquals(1, BattleDesk.awaiting(arena.state()).size());
        arena.play(BattleDesk.cancelAction(arena.state(), ADMIN, "ana-1"));
        assertEquals(List.of(), BattleDesk.awaiting(arena.state()));
        assertEquals(List.of("wild-1"), arena.battle().queue());
        assertEquals(46, foe().hp());
    }


}
