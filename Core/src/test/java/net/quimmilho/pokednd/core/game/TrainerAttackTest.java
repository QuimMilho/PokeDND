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
import net.quimmilho.pokednd.core.rules.PokemonType;
import net.quimmilho.pokednd.core.trainer.KoMode;
import net.quimmilho.pokednd.core.trainer.KnockOut;
import net.quimmilho.pokednd.core.trainer.Trainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 48: enemies attack a trainer (chapter 7.12) and the knock-out rules (chapter 4.6). The worked example of the specification:
 *
 * <pre>
 *   A level-20 Pidgeotto (Attack 32) uses Wing Attack (power 60, 2d10, rolled 11 = effective power 55) on a level-5 trainer with
 *   Defense 20 + 4 x 5 = 40:  floor(floor(10 x 55 x 32 / 40) / 50) + 2 = 10;  same-type bonus x1.5 = 15;  x 0.25 = 3.75, rounded up: 4 damage.
 *   Its attack total is 12 + level / 8 (2) = 14.  The trainer has AC 13 and rolls 1d20 + 3: 11 or more on the die dodges it.
 * </pre>
 */
class TrainerAttackTest {

    private static final Map<String, Integer> ANA_FIRST = Map.of("ana", 12, "wild", 5);
    private static final String AT_ANA = Battle.trainerTarget("ana");

    private Arena arena;

    @BeforeEach
    void setUp() {
        arena = new Arena(MoveBook.of(Moves.all()), "ana", "bo");
        arena.bonuses = new BattleBonuses(Map.of(), Map.of("ana", 3), Map.of(), Map.of(), Map.of(), false, java.util.Set.of()); // AC 13
    }

    // ------------------------------------------------------------------------------------------------------------------ scaffolding

    private void trainer(int level, int maxHp, int hp) {
        arena.play(List.of(new GameEvent.TrainerSet("ana", Fixtures.trainerAt("Ana", level, maxHp, hp))));
    }

    private Combatant charmander() {
        return arena.mon("ana-1", "ana", "Charmander", 20, 50, List.of(PokemonType.FIRE), 30, 28, 32, 30, 60, "ember", "tackle");
    }

    private Combatant pidgeotto(String... moves) {
        Combatant c = arena.mon("wild-1", "wild", "Pidgeotto", 20, 60, List.of(PokemonType.NORMAL, PokemonType.FLYING), 32, 28, 32, 30, 40, moves);
        return c;
    }

    private void duel(BattleSettings rules, Combatant wild) {
        arena.start(Battle.Type.WILD, rules, List.of(Arena.trainer("ana", List.of("ana-1"), List.of()), Arena.wild("wild", "Pidgeotto", "wild-1")), List.of(charmander(), wild));
    }

    private void attack(String move, Answers answers) {
        arena.round(ANA_FIRST).declare("ana-1", Action.skip()).declare("wild-1", Action.move(move, AT_ANA));
        arena.resolve(answers);
    }

    private Trainer ana() {
        return arena.state().trainers().get("ana");
    }

    private static final Answers WING_HITS = Answers.of("wild-1-tatk", 12, "wild-1-tdef", 10, "wild-1-tdmg", new int[] {5, 6});

    // ================================================================================================================== the attack

    @Test
    void theWorkedExampleOfTheSpecificationDoes4Damage() {
        trainer(5, 33, 33);
        duel(BattleSettings.defaults(), pidgeotto("wingattack"));
        attack("wingattack", WING_HITS);
        assertEquals(29, ana().hp(), "4 damage of 33");
        assertTrue(arena.said("Ana takes 4 damage from wild Pidgeotto's Wing Attack (29/33 HP)."));
        // the roll prompts: the enemy rolls 1d20 + 2; the trainer rolls 1d20 + 3 and needs 14 in all, which is 11 on the die
        assertEquals("u-ana", arena.asked.get("b1-r1-wild-1-tdef").rollerUserId(), "the trainer's player defends");
        assertEquals(GameState.RollClass.ENEMY, arena.asked.get("b1-r1-wild-1-tatk").rollClass());
        GameState.RollRequest defence = arena.asked.get("b1-r1-wild-1-tdef");
        assertEquals(11, defence.bands().get(1).from(), "11 or higher on the die");
        assertTrue(defence.explanation().contains("attack total is 14"), defence.explanation());
    }

    @Test
    void aTieFavoursTheTrainer() {
        trainer(5, 33, 33);
        duel(BattleSettings.defaults(), pidgeotto("wingattack"));
        attack("wingattack", Answers.of("wild-1-tatk", 12, "wild-1-tdef", 11)); // 11 + 3 = 14 = the attack total: dodged, no damage dice
        assertEquals(33, ana().hp());
        assertTrue(arena.said("is dodged by Ana"));
        assertFalse(arena.asked.containsKey("b1-r1-wild-1-tdmg"), "no damage is rolled for a dodged attack");
    }

    @Test
    void aNaturalTwentyAlwaysHitsAndIsCriticalAndTheTrainerRollsNothing() {
        trainer(5, 33, 33);
        duel(BattleSettings.defaults(), pidgeotto("wingattack"));
        attack("wingattack", Answers.of("wild-1-tatk", 20, "wild-1-tdmg", new int[] {5, 6}));
        assertFalse(arena.asked.containsKey("b1-r1-wild-1-tdef"));
        // 10 x 1.5 (critical) x 1.5 (same type) = 22; x 0.25 = 5.5 rounded up = 6
        assertEquals(33 - 6, ana().hp());
        assertTrue(arena.said("A critical hit!"));
    }

    @Test
    void aNaturalOneAlwaysMissesAndTheTrainerRollsNothing() {
        trainer(5, 33, 33);
        duel(BattleSettings.defaults(), pidgeotto("wingattack"));
        attack("wingattack", Answers.of("wild-1-tatk", 1));
        assertFalse(arena.asked.containsKey("b1-r1-wild-1-tdef"));
        assertEquals(33, ana().hp());
        assertTrue(arena.said("misses completely"));
    }

    @Test
    void aClassFeatureTakesDamageOffAndNeverBelowZero() {
        trainer(5, 33, 33);
        arena.bonuses = new BattleBonuses(Map.of(), Map.of("ana", 3), Map.of("ana", 3), Map.of(), Map.of(), false, java.util.Set.of());
        duel(BattleSettings.defaults(), pidgeotto("wingattack"));
        attack("wingattack", WING_HITS);
        assertEquals(33 - 1, ana().hp(), "4 - 3");
        arena = new Arena(MoveBook.of(Moves.all()), "ana", "bo");
        trainer(5, 33, 33);
        arena.bonuses = new BattleBonuses(Map.of(), Map.of("ana", 3), Map.of("ana", 9), Map.of(), Map.of(), false, java.util.Set.of());
        duel(BattleSettings.defaults(), pidgeotto("wingattack"));
        attack("wingattack", WING_HITS);
        assertEquals(33, ana().hp());
        assertTrue(arena.said("Ana takes no damage."));
    }

    @Test
    void theNumbersOfTheAttackAreSettings() {
        trainer(5, 33, 33);
        BattleSettings rules = BattleSettings.fromValues(Map.of("combat.trainer.damageScale", "0.5", "combat.trainer.defenseBase", "10", "combat.trainer.defensePerLevel", "2",
                "combat.trainer.attackLevelDivisor", "4"));
        duel(rules, pidgeotto("wingattack"));
        // Defense 10 + 2 x 5 = 20: floor(floor(10 x 55 x 32 / 20) / 50) + 2 = 19; x1.5 = 28; x 0.5 = 14
        attack("wingattack", Answers.of("wild-1-tatk", 12, "wild-1-tdef", 10, "wild-1-tdmg", new int[] {5, 6}));
        assertEquals(33 - 14, ana().hp());
        assertTrue(arena.asked.get("b1-r1-wild-1-tdef").explanation().contains("attack total is 17"), "12 + 20 / 4 = 17");
    }

    @Test
    void aMoveThatIsNotAnAttackHasNoEffectOnATrainer() {
        trainer(5, 33, 33);
        duel(BattleSettings.defaults(), pidgeotto("wingattack", "sandattack"));
        attack("sandattack", Answers.none());
        assertEquals(33, ana().hp());
        assertTrue(arena.said("has no effect on Ana"));
    }

    // ============================================================================================================ who may be a target

    @Test
    void aPlayersPokemonCannotAimAtAnotherTrainer() {
        // a PvP battle: nobody is an enemy, so trainers are never targets
        Combatant bo = arena.mon("bo-1", "bo", "Squirtle", 20, 50, List.of(PokemonType.WATER), 30, 28, 32, 30, 50, "tackle");
        arena.start(Battle.Type.PVP, BattleSettings.defaults(), List.of(Arena.trainer("ana", List.of("ana-1"), List.of()),
                        new Battle.Participant("bo", Battle.Kind.TRAINER, 1, "Bo", "bo", "u-bo", List.of("bo-1"), List.of())),
                List.of(charmander(), bo));
        arena.round(Map.of("ana", 12, "bo", 5));
        assertThrows(BattleRefusedException.class, () -> arena.declare("ana-1", Action.move("tackle", Battle.trainerTarget("bo"))));
    }

    @Test
    void anEnemyCannotAimAtItsOwnSideOrAtATrainerAt0Hp() {
        trainer(5, 33, 0);
        duel(BattleSettings.defaults(), pidgeotto("wingattack"));
        arena.round(ANA_FIRST);
        assertThrows(BattleRefusedException.class, () -> arena.declare("wild-1", Action.move("wingattack", AT_ANA)), "the trainer is down");
    }

    @Test
    void aMoveThatHitsAGroupCannotBeAimedAtATrainer() {
        trainer(5, 33, 33);
        duel(BattleSettings.defaults(), pidgeotto("wingattack", "swift"));
        arena.round(ANA_FIRST);
        assertThrows(BattleRefusedException.class, () -> arena.declare("wild-1", Action.move("swift", AT_ANA)));
    }

    // =================================================================================================================== knock-out

    private void knockOutAna(BattleSettings rules) {
        trainer(5, 33, 3);
        duel(rules, pidgeotto("wingattack"));
        attack("wingattack", WING_HITS);
    }

    @Test
    void aTrainerAtZeroHpLeavesTheBattleWithTheirPokemon() {
        knockOutAna(BattleSettings.defaults());
        assertEquals(0, ana().hp());
        assertEquals("knockedOut", arena.battle().left().get("ana"));
        assertTrue(arena.said("Ana is knocked out!"));
        assertEquals(List.of("wild-1"), arena.battle().standing().stream().map(Combatant::id).toList());
    }

    @Test
    void withFaintRespawnTheTrainerGoesBackToTheHealerWhenTheBattleIsOver() {
        knockOutAna(BattleSettings.defaults());
        GameState.Knockout ko = arena.state().knockouts().get("ana");
        assertEquals(KoMode.FAINT_RESPAWN, ko.mode());
        assertEquals(KnockOut.DeathSaves.State.RESPAWN, ko.saves().state());
        arena.endRound();
        arena.play(BattleDesk.endIfOver(arena.state()));
        assertEquals(33, ana().hp(), "full HP");
        assertEquals(2700, ana().money(), "10 % of 3000 lost (combat.whiteout.moneyLossPct)");
        assertTrue(arena.state().knockouts().isEmpty());
    }

    @Test
    void theMoneyLostIsASetting() {
        knockOutAna(BattleSettings.defaults().withKnockOut(new BattleSettings.KnockOutRules(KoMode.FAINT_RESPAWN, false, 25)));
        arena.endRound();
        arena.play(BattleDesk.endIfOver(arena.state()));
        assertEquals(2250, ana().money());
    }

    @Test
    void withTheAdminModeTheBattleGetsATaskAndNothingIsAutomatic() {
        knockOutAna(BattleSettings.defaults().withKnockOut(new BattleSettings.KnockOutRules(KoMode.ADMIN, false, 10)));
        assertEquals(1, arena.battle().tasks().size());
        assertTrue(arena.battle().tasks().get(0).text().contains("knock-out of Ana"));
        arena.endRound();
        arena.play(BattleDesk.endIfOver(arena.state()));
        assertEquals(0, ana().hp(), "the admin decides");
        assertEquals(3000, ana().money());
        assertEquals(KoMode.ADMIN, arena.state().knockouts().get("ana").mode());
    }

    // ----------------------------------------------------------------------------------------------------------------- death saves

    private void knockedOutWithDeathSaves(boolean deathAllowed) {
        knockOutAna(BattleSettings.defaults().withKnockOut(new BattleSettings.KnockOutRules(KoMode.DEATH_SAVES, deathAllowed, 10)));
        arena.endRound();
        arena.play(BattleDesk.endIfOver(arena.state()));
        assertEquals(0, ana().hp(), "still down after the battle");
    }

    private void saveWith(int die) {
        arena.play(TrainerDesk.deathSaveRequest(arena.state(), arena.rolls, "ana", Arena.NOW, arena.seeds));
        String id = arena.state().pendingRolls().keySet().iterator().next();
        arena.play(RollDesk.enter(arena.state(), Arena.ADMIN, id, List.of(die), null));
        arena.play(TrainerDesk.deathSaveResult(arena.state(), arena.battle().rules(), "ana", id));
    }

    @Test
    void threeSuccessfulDeathSavesStabiliseTheTrainerAt0Hp() {
        knockedOutWithDeathSaves(false);
        saveWith(10);
        saveWith(15);
        assertEquals(KnockOut.DeathSaves.State.UNCONSCIOUS, arena.state().knockouts().get("ana").saves().state());
        saveWith(12);
        assertEquals(KnockOut.DeathSaves.State.STABLE, arena.state().knockouts().get("ana").saves().state());
        assertEquals(0, ana().hp());
        assertThrows(BattleRefusedException.class, () -> TrainerDesk.deathSaveRequest(arena.state(), arena.rolls, "ana", Arena.NOW, arena.seeds));
    }

    @Test
    void aNaturalTwentyBringsTheTrainerBackAt1Hp() {
        knockedOutWithDeathSaves(false);
        saveWith(9);
        saveWith(20);
        assertEquals(1, ana().hp());
        assertTrue(arena.state().knockouts().isEmpty());
    }

    @Test
    void aNaturalOneCountsAsTwoFailuresAndThreeFailuresRespawnTheTrainer() {
        knockedOutWithDeathSaves(false);
        saveWith(1);
        assertEquals(2, arena.state().knockouts().get("ana").saves().failures());
        saveWith(5);
        assertEquals(33, ana().hp(), "three failures: back at the healer");
        assertEquals(2700, ana().money());
        assertTrue(arena.state().knockouts().isEmpty());
    }

    @Test
    void withDeathAllowedThreeFailuresKillTheTrainerAndTheAdminDecides() {
        knockedOutWithDeathSaves(true);
        saveWith(2);
        saveWith(3);
        saveWith(4);
        assertEquals(KnockOut.DeathSaves.State.DEAD, arena.state().knockouts().get("ana").saves().state());
        assertEquals(0, ana().hp());
        assertEquals(3000, ana().money());
    }

    @Test
    void anAllyCanStabiliseAndARollIsNeverCountedTwice() {
        knockedOutWithDeathSaves(false);
        saveWith(4);
        arena.play(TrainerDesk.stabilise(arena.state(), "ana"));
        assertEquals(KnockOut.DeathSaves.State.STABLE, arena.state().knockouts().get("ana").saves().state());

        arena = new Arena(MoveBook.of(Moves.all()), "ana", "bo");
        arena.bonuses = new BattleBonuses(Map.of(), Map.of("ana", 3), Map.of(), Map.of(), Map.of(), false, java.util.Set.of());
        knockedOutWithDeathSaves(false);
        arena.play(TrainerDesk.deathSaveRequest(arena.state(), arena.rolls, "ana", Arena.NOW, arena.seeds));
        arena.play(RollDesk.enter(arena.state(), Arena.ADMIN, "ko-ana-1", List.of(12), null));
        arena.play(TrainerDesk.deathSaveResult(arena.state(), arena.battle().rules(), "ana", "ko-ana-1"));
        assertThrows(BattleRefusedException.class, () -> TrainerDesk.deathSaveResult(arena.state(), arena.battle().rules(), "ana", "ko-ana-1"),
                "the same roll is not counted twice");
    }

    @Test
    void outsideABattleARespawnCanBeAskedForDirectly() {
        trainer(5, 33, 0);
        arena.play(TrainerDesk.respawn(arena.state(), "ana", 10));
        assertEquals(33, ana().hp());
        assertEquals(2700, ana().money());
    }
}
