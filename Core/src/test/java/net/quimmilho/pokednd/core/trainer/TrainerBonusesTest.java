/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.quimmilho.pokednd.core.combat.CatchCalculator;
import net.quimmilho.pokednd.core.combat.FleeCalculator;
import net.quimmilho.pokednd.core.trainer.KnockOut.DeathSaves;
import net.quimmilho.pokednd.core.trainer.KnockOut.DeathSaves.State;
import net.quimmilho.pokednd.core.trainer.TrainerBonuses.CommandSettings;
import net.quimmilho.pokednd.core.trainer.TrainerRulebook.CreateRequest;
import org.junit.jupiter.api.Test;

/** How a sheet helps the Pokémon (chapter 4.2) and what happens at 0 HP (chapter 4.6), with worked examples. */
class TrainerBonusesTest {

    private static final double EPS = 1e-9;
    private static final CommandSettings COMMAND = CommandSettings.defaults();
    private static final CatchCalculator.Settings CATCH = CatchCalculator.Settings.defaults();
    private static final FleeCalculator.Settings FLEE = FleeCalculator.Settings.defaults();

    // ---------------------------------------------------------------- 4.2

    @Test
    void commandGivesOnePercentagePointPerHandlingPointUpToTen() {
        assertEquals(0.0, TrainerBonuses.commandAccuracyPp(0, COMMAND), EPS);
        assertEquals(1.0, TrainerBonuses.commandAccuracyPp(1, COMMAND), EPS);
        assertEquals(3.0, TrainerBonuses.commandAccuracyPp(3, COMMAND), EPS);
        assertEquals(10.0, TrainerBonuses.commandAccuracyPp(10, COMMAND), EPS);
        assertEquals(10.0, TrainerBonuses.commandAccuracyPp(12, COMMAND), EPS, "capped at +10");
        assertEquals(0.0, TrainerBonuses.commandAccuracyPp(-2, COMMAND), EPS, "a negative modifier gives no penalty");
    }

    @Test
    void commandSettingsChangeTheRate() {
        CommandSettings strong = new CommandSettings(2.5, 20);
        assertEquals(7.5, TrainerBonuses.commandAccuracyPp(3, strong), EPS);
        assertEquals(20.0, TrainerBonuses.commandAccuracyPp(9, strong), EPS);
        assertThrows(IllegalArgumentException.class, () -> new CommandSettings(-1, 10));
    }

    @Test
    void catchChanceIsMultipliedByOnePlusFivePercentPerHandlingPoint() {
        assertEquals(1.0, TrainerBonuses.catchMultiplier(0, 0, CATCH), EPS);
        assertEquals(1.2, TrainerBonuses.catchMultiplier(4, 0, CATCH), EPS);
        assertEquals(1.0, TrainerBonuses.catchMultiplier(-3, 0, CATCH), EPS, "never below x1 unless allowPenalty");
        assertEquals(0.85, TrainerBonuses.catchMultiplier(-3, 0, CATCH.withAllowPenalty(true)), EPS);
        // The Ranger's Wild Whisperer (+25 %) multiplies on top
        assertEquals(1.2 * 1.25, TrainerBonuses.catchMultiplier(4, 0.25, CATCH), EPS);
    }

    @Test
    void fleeGainsTwoPercentagePointsPerDexModifierPoint() {
        assertEquals(0.0, TrainerBonuses.fleeBonusPp(0, 0, FLEE), EPS);
        assertEquals(6.0, TrainerBonuses.fleeBonusPp(3, 0, FLEE), EPS);
        assertEquals(-2.0, TrainerBonuses.fleeBonusPp(-1, 0, FLEE), EPS);
        assertEquals(26.0, TrainerBonuses.fleeBonusPp(3, 20, FLEE), EPS, "Swift Retreat adds +20");
        assertEquals(9.0, TrainerBonuses.fleeBonusPp(3, 0, FLEE.withBonusPpPerPoint(3)), EPS);
    }

    @Test
    void aSheetConvertsItsOwnModifiers() {
        TrainerRulebook rules = TrainerRulebook.defaults();
        // WIS 14 (+2) and proficient in Pokémon Handling at level 1 (+2): Handling +4. DEX 12 (+1). Costs 7 + 7 + ... see scores
        AbilityScores scores = AbilityScores.of(8, 12, 12, 8, 14, 13);
        Trainer t = rules.create(new CreateRequest("Ana", "ranger", ScoreMethod.POINT_BUY, scores, List.of("pokemon_handling", "survival")));
        Sheet s = rules.sheet(t);
        assertEquals(4, s.handlingModifier());
        assertEquals(4.0, s.commandAccuracyPp(COMMAND), EPS);
        assertEquals(1.2, s.catchMultiplier(CATCH), EPS);
        assertEquals(2.0, s.fleeBonusPp(FLEE), EPS);

        // A level 6 Ranger has Wild Whisperer (+25 % catch) and Pathfinder's +1 movement
        Trainer six = rules.addXp(t, rules.settings().xp().totalXp(6));
        for (int i = 2; i <= 6; i++) {
            six = rules.levelUp(six, i == 4 ? java.util.Map.of(Ability.STR, 2) : java.util.Map.of());
        }
        Sheet s6 = rules.sheet(six);
        assertEquals(3, s6.proficiencyBonus());
        assertEquals(5, s6.handlingModifier(), "WIS +2 and proficiency +3");
        assertEquals(1.25 * 1.25, s6.catchMultiplier(CATCH), EPS);
        assertEquals(1, s6.movementBonus());
    }

    // ---------------------------------------------------------------- 4.6

    private static Trainer sample() {
        return TrainerRulebook.defaults().create(new CreateRequest("Ana", "ace_trainer", ScoreMethod.POINT_BUY,
                AbilityScores.of(15, 14, 13, 11, 10, 9), List.of("pokemon_handling", "insight")));
    }

    @Test
    void damageAndHealingStayBetweenZeroAndTheMaximum() {
        Trainer t = sample(); // 9 HP
        assertEquals(9, t.maxHp());
        assertEquals(4, t.damage(5).hp());
        assertEquals(0, t.damage(100).hp());
        assertTrue(t.damage(100).isDown());
        assertEquals(9, t.damage(5).heal(100).hp());
        assertEquals(9, t.damage(5).healFully().hp());
        assertThrows(IllegalArgumentException.class, () -> t.damage(-1));
        assertThrows(IllegalArgumentException.class, () -> t.heal(-1));
    }

    @Test
    void hpRecoversTenPercentPerRoundButAtLeastOnePoint() {
        Trainer t = sample().withHp(1);
        assertEquals(2, t.recoverRound(10).hp(), "10 % of 9 is 0, so 1 point");
        Trainer big = sample().withHp(1);
        big = new Trainer(big.name(), big.classId(), big.level(), big.xp(), big.scores(), big.skills(), 100, 50, big.money(), big.items());
        assertEquals(60, big.recoverRound(10).hp());
        assertEquals(100, big.recoverRound(100).hp());
        assertEquals(50, big.recoverRound(0).hp(), "off");
        assertEquals(0, big.withHp(0).recoverRound(10).hp(), "a trainer at 0 HP does not recover by walking");
        assertEquals(100, big.withHp(100).recoverRound(10).hp());
    }

    @Test
    void theKnockOutModeDecidesWhatHappens() {
        assertEquals(KnockOut.Outcome.RESPAWN, KnockOut.onZeroHp(KoMode.FAINT_RESPAWN));
        assertEquals(KnockOut.Outcome.DEATH_SAVES, KnockOut.onZeroHp(KoMode.DEATH_SAVES));
        assertEquals(KnockOut.Outcome.ADMIN_DECIDES, KnockOut.onZeroHp(KoMode.ADMIN));
        assertEquals(KoMode.DEATH_SAVES, KoMode.parse("deathSaves"));
        assertThrows(IllegalArgumentException.class, () -> KoMode.parse("explode"));
    }

    @Test
    void faintAndRespawnHealsAndTakesSomeMoney() {
        Trainer down = sample().withMoney(1234).damage(100);
        Trainer back = KnockOut.respawn(down, 10);
        assertEquals(back.maxHp(), back.hp());
        assertEquals(1234 - 123, back.money());
        assertEquals(0, KnockOut.moneyLost(1234, 0));
        assertEquals(1234, KnockOut.moneyLost(1234, 100));
        assertThrows(IllegalArgumentException.class, () -> KnockOut.moneyLost(10, 101));
    }

    @Test
    void deathSavesTenOrMoreSucceedsAndNineOrLessFails() {
        DeathSaves d = DeathSaves.start();
        d = d.roll(10, false);
        assertEquals(1, d.successes());
        d = d.roll(9, false);
        assertEquals(1, d.failures());
        d = d.roll(15, false).roll(11, false);
        assertEquals(State.STABLE, d.state(), "three successes");
        assertTrue(d.isOver());
        assertThrows(IllegalStateException.class, () -> DeathSaves.start().roll(10, false).roll(10, false).roll(10, false).roll(10, false));
    }

    @Test
    void aNaturalTwentyBringsTheTrainerBackAtOneHp() {
        DeathSaves d = DeathSaves.start().roll(3, false).roll(20, false);
        assertEquals(State.REVIVED, d.state());
        assertEquals(1, d.applyRevival(sample().damage(100)).hp());
        assertThrows(IllegalStateException.class, () -> DeathSaves.start().applyRevival(sample()));
    }

    @Test
    void aNaturalOneCountsAsTwoFailures() {
        DeathSaves d = DeathSaves.start().roll(1, false);
        assertEquals(2, d.failures());
        assertEquals(State.UNCONSCIOUS, d.state());
        assertEquals(State.RESPAWN, d.roll(5, false).state(), "the third failure falls back to faint and respawn");
        assertEquals(State.DEAD, d.roll(5, true).state(), "with trainer.ko.deathAllowed the trainer dies");
        assertEquals(State.RESPAWN, DeathSaves.start().roll(2, false).roll(2, false).roll(2, false).state());
        assertEquals(State.DEAD, DeathSaves.start().roll(2, true).roll(2, true).roll(1, true).state());
    }

    @Test
    void anAllyCanStabiliseTheTrainerAndBadDiceAreRefused() {
        DeathSaves d = DeathSaves.start().roll(4, false).stabilise();
        assertEquals(State.STABLE, d.state());
        assertEquals(1, d.failures());
        assertThrows(IllegalStateException.class, d::stabilise);
        assertThrows(IllegalArgumentException.class, () -> DeathSaves.start().roll(0, false));
        assertThrows(IllegalArgumentException.class, () -> DeathSaves.start().roll(21, false));
        assertFalse(DeathSaves.start().isOver());
    }

    @Test
    void theDefaultSettingsAreTheOnesInChapter4_7() {
        TrainerSettings s = TrainerSettings.defaults();
        assertEquals("pointBuy", ScoreMethod.allowedText(s.allowedMethods()));
        assertEquals(27, s.pointBuyBudget());
        assertEquals(3000, s.startMoney());
        assertEquals(java.util.Map.of("poke_ball", 5, "potion", 3), s.startItems());
        assertEquals(10, s.hpRecoverPerRoundPct());
        assertEquals(KoMode.FAINT_RESPAWN, s.koMode());
        assertFalse(s.deathAllowed());
        assertEquals(1, s.xp().levelStart());
        assertEquals(20, s.xp().levelCap());
        assertEquals(25, s.xp().curveFactor());
        assertThrows(IllegalArgumentException.class, () -> s.withHpRecoverPerRoundPct(101));
        assertThrows(IllegalArgumentException.class, () -> s.withAllowedMethods(java.util.Set.of()));
    }
}
