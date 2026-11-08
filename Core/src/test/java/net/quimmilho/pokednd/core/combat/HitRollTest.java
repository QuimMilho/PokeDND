/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.combat;

import static net.quimmilho.pokednd.core.combat.HitRoll.Result.CRIT;
import static net.quimmilho.pokednd.core.combat.HitRoll.Result.HIT;
import static net.quimmilho.pokednd.core.combat.HitRoll.Result.MISS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.quimmilho.pokednd.core.combat.HitRoll.Input;
import net.quimmilho.pokednd.core.combat.HitRoll.Odds;
import net.quimmilho.pokednd.core.combat.HitRoll.Result;
import net.quimmilho.pokednd.core.dice.DiceSettings;
import org.junit.jupiter.api.Test;

class HitRollTest {

    private static final CombatSettings SETTINGS = CombatSettings.defaults();

    private static Odds odds(Input input) {
        return HitRoll.compute(input, SETTINGS);
    }

    /** "1–4" for a band, or "-" when it has no faces. */
    private static String range(Odds odds, Result result) {
        return odds.band(result).range();
    }

    // ------------------------------------------------------------------ the worked examples of chapter 6.5

    @Test
    void eightyPercentAccuracyAndFivePercentCritIsAD20() {
        Odds o = odds(Input.plain(80));
        assertEquals(20, o.choice().die());
        assertEquals("1–4", range(o, MISS));
        assertEquals("5–19", range(o, HIT));
        assertEquals("20", range(o, CRIT));
        assertEquals(0.0, o.choice().maxErrorPp(), 1e-9, "exact");
    }

    @Test
    void ninetyPercentAccuracyAndTenPercentCritIsAD10() {
        Odds o = odds(Input.plain(90).withCritStage(1));
        assertEquals(10, o.choice().die());
        assertEquals("1", range(o, MISS));
        assertEquals("2–9", range(o, HIT));
        assertEquals("10", range(o, CRIT));
    }

    @Test
    void hundredPercentAccuracyAndFivePercentCritIsAD20WithNoMissBand() {
        Odds o = odds(Input.plain(100));
        assertEquals(20, o.choice().die());
        assertEquals("-", range(o, MISS));
        assertEquals("1–19", range(o, HIT));
        assertEquals("20", range(o, CRIT));
        assertFalse(o.canMiss());
    }

    @Test
    void aHundredPercentMoveAfterOneMinusOneAccuracyStageHitsSeventyFivePercent() {
        Odds o = odds(Input.plain(100).withAccuracyStage(-1));
        assertEquals(0.75, o.hitChance(), 1e-9);
        assertEquals(20, o.choice().die());
        assertEquals("1–5", range(o, MISS));
        assertEquals("6–19", range(o, HIT));
        assertEquals("20", range(o, CRIT));
    }

    @Test
    void hundredPercentAccuracyAndCritStagePlusThreeIsAD4() {
        Odds o = odds(Input.plain(100).withCritStage(3));
        assertEquals(4, o.choice().die());
        assertEquals("-", range(o, MISS));
        assertEquals("1–3", range(o, HIT));
        assertEquals("4", range(o, CRIT));
    }

    // ------------------------------------------------------------------ moves that cannot miss

    @Test
    void aMoveThatAlwaysHitsNeverProducesAMissBand() {
        for (int critStage = 0; critStage <= 4; critStage++) {
            Odds o = odds(Input.neverMisses().withCritStage(critStage));
            assertFalse(o.canMiss(), "crit stage " + critStage);
            assertEquals(0, o.band(MISS).faces());
            assertEquals(0.0, o.missChance(), 1e-12);
            for (int roll = 1; roll <= o.choice().die(); roll++) {
                assertTrue(o.resolve(roll) != MISS, "roll " + roll + " at crit stage " + critStage);
            }
        }
        // Even with the worst stages and penalties the dash move still cannot miss.
        Odds worst = odds(Input.neverMisses().withAccuracyStage(-6).withEvasionStage(6).withBonusesPp(-50, -50));
        assertFalse(worst.canMiss());
        assertEquals(20, worst.choice().die());
        assertEquals("1–19", range(worst, HIT));
        assertEquals("20", range(worst, CRIT));
    }

    @Test
    void aMoveThatCannotMissAndCannotCritNeedsNoRoll() {
        CombatSettings noCrits = SETTINGS.withCritChancesPct(List.of(0, 0, 0, 0));
        Odds o = HitRoll.compute(Input.neverMisses(), noCrits);
        assertFalse(o.rolled());
        assertEquals(HIT, o.certain());
        assertThrows(IllegalArgumentException.class, () -> o.resolve(1));
    }

    @Test
    void aMoveThatAlwaysCritsHasNoCritRollButStillRollsToHit() {
        Odds frostBreath = odds(new Input(Optional.of(90), 0, 0, 0, 0, 0, true));
        assertEquals(10, frostBreath.choice().die());
        assertEquals("1", range(frostBreath, MISS));
        assertEquals("-", range(frostBreath, HIT), "every hit is a crit");
        assertEquals("2–10", range(frostBreath, CRIT));
        Odds sure = odds(new Input(Optional.empty(), 0, 0, 0, 0, 0, true));
        assertFalse(sure.rolled());
        assertEquals(CRIT, sure.certain());
    }

    // ------------------------------------------------------------------ stages, bonuses, clamping

    @Test
    void accuracyAndEvasionStagesFollowTheTableOfChapter7_5() {
        double[] expected = {0.50, 0.60, 0.75, 1.00, 1.0 + 1.0 / 3.0, 1.0 + 2.0 / 3.0, 2.00};
        for (int s = -3; s <= 3; s++) {
            // A 40 % move so that the multiplier is not hidden by the 100 % clamp.
            Odds o = odds(Input.plain(40).withAccuracyStage(s));
            assertEquals(0.40 * expected[s + 3], o.hitChance(), 1e-9, "stage " + s);
        }
        assertEquals(0.30, odds(Input.plain(40).withEvasionStage(1)).hitChance(), 1e-9, "+1 evasion is -1 accuracy");
        assertEquals(0.40, odds(Input.plain(40).withAccuracyStage(2).withEvasionStage(2)).hitChance(), 1e-9, "they cancel");
        assertEquals(0.40 / 3.0, odds(Input.plain(40).withAccuracyStage(-9)).hitChance(), 1e-9, "kept within -6, which is x3/9");
    }

    @Test
    void turningOffAccuracyStagesIgnoresThem() {
        CombatSettings off = SETTINGS.withAccuracyStageEffects(false);
        assertEquals(1.0, HitRoll.compute(Input.plain(100).withAccuracyStage(-6), off).hitChance(), 1e-9);
    }

    @Test
    void commandAndFeatureBonusesAddPercentagePointsAndTheChanceStaysBetween0And100() {
        assertEquals(0.84, odds(Input.plain(80).withBonusesPp(4, 0)).hitChance(), 1e-9);
        assertEquals(0.86, odds(Input.plain(80).withBonusesPp(4, 2)).hitChance(), 1e-9);
        assertEquals(1.0, odds(Input.plain(95).withBonusesPp(10, 10)).hitChance(), 1e-9, "no more than 100 %");
        assertEquals(0.0, odds(Input.plain(30).withBonusesPp(-50, 0)).hitChance(), 1e-9, "no less than 0 %");
    }

    @Test
    void theCritShareIsPartOfTheHitChanceAndIsCutDownWhenTheHitChanceIsLower() {
        Odds o = odds(Input.plain(3)); // 3 % to hit, 5 % crit before the cut
        assertEquals(0.03, o.hitChance(), 1e-9);
        assertEquals(0.03, o.critChance(), 1e-9);
        assertEquals(0.0, o.band(HIT).trueChance(), 1e-9);
        assertEquals(100, o.choice().die());
        double total = o.band(MISS).trueChance() + o.band(HIT).trueChance() + o.band(CRIT).trueChance();
        assertEquals(1.0, total, 1e-9);
    }

    @Test
    void critChanceByStageMatchesChapter7_5AndTheSettingChangesIt() {
        double[] expected = {0.05, 0.10, 0.15, 0.25, 0.25};
        for (int stage = 0; stage <= 4; stage++) {
            assertEquals(expected[stage], odds(Input.plain(100).withCritStage(stage)).critChance(), 1e-9, "stage " + stage);
        }
        CombatSettings custom = SETTINGS.withCritChancesPct(List.of(10, 20, 30, 50));
        assertEquals(0.5, HitRoll.compute(Input.plain(100).withCritStage(7), custom).critChance(), 1e-9);
        assertEquals(0.05, odds(Input.plain(100).withCritStage(-2)).critChance(), 1e-9, "below zero counts as zero");
    }

    // ------------------------------------------------------------------ reading a roll

    @Test
    void everyFaceOfTheDieMeansExactlyOneOutcome() {
        Odds o = odds(Input.plain(80));
        for (int roll = 1; roll <= 4; roll++) {
            assertEquals(MISS, o.resolve(roll));
        }
        for (int roll = 5; roll <= 19; roll++) {
            assertEquals(HIT, o.resolve(roll));
        }
        assertEquals(CRIT, o.resolve(20));
        assertThrows(IllegalArgumentException.class, () -> o.resolve(0));
        assertThrows(IllegalArgumentException.class, () -> o.resolve(21));
    }

    @Test
    void rollingLowToSucceedMirrorsTheBands() {
        DiceSettings low = DiceSettings.defaults().withDirection(DiceSettings.Direction.LOW);
        Odds o = HitRoll.compute(Input.plain(80), SETTINGS, low);
        assertEquals(CRIT, o.resolve(1));
        assertEquals(HIT, o.resolve(2));
        assertEquals(MISS, o.resolve(20));
    }

    // ------------------------------------------------------------------ one-hit-KO moves

    @Test
    void aOneHitKoMoveHasAccuracy30PlusTheLevelDifference() {
        assertEquals(0.30, HitRoll.oneHitKo(50, 50, DiceSettings.defaults()).hitChance(), 1e-9);
        assertEquals(0.40, HitRoll.oneHitKo(50, 40, DiceSettings.defaults()).hitChance(), 1e-9);
        assertEquals(1.0, HitRoll.oneHitKo(100, 10, DiceSettings.defaults()).hitChance(), 1e-9, "capped at 100 %");
        Odds o = HitRoll.oneHitKo(50, 50, DiceSettings.defaults());
        assertEquals(0.0, o.critChance(), "a one-hit KO never crits");
        assertEquals("-", range(o, CRIT));
    }

    @Test
    void aOneHitKoMoveFailsOutrightIfTheAttackerIsLowerLevel() {
        Odds o = HitRoll.oneHitKo(49, 50, DiceSettings.defaults());
        assertFalse(o.rolled());
        assertEquals(MISS, o.certain());
        assertEquals(0.0, o.hitChance());
    }
}
