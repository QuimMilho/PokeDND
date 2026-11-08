/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class StatsTest {

    private static final StatBlock GARCHOMP = new StatBlock(108, 130, 95, 80, 85, 102);

    @Test
    void aLevel78GarchompMatchesTheOfficialWorkedExample() {
        // The well-known example from the games' stat page: IVs 24/12/30/16/23/5, EVs 74/190/91/48/84/23, Adamant.
        StatBlock ivs = new StatBlock(24, 12, 30, 16, 23, 5);
        StatBlock evs = new StatBlock(74, 190, 91, 48, 84, 23);
        StatBlock stats = Stats.compute(GARCHOMP, ivs, evs, 78, Nature.find("adamant").orElseThrow());
        assertEquals(new StatBlock(289, 278, 193, 135, 171, 171), stats);
    }

    @Test
    void aLevel100GarchompHasItsMinimumAndMaximumStats() {
        Nature hardy = Nature.find("hardy").orElseThrow();
        assertEquals(new StatBlock(326, 265, 195, 165, 175, 209),
                Stats.compute(GARCHOMP, StatBlock.all(0), StatBlock.all(0), 100, hardy), "no IVs, no EVs");
        assertEquals(420, Stats.hp(108, 31, 252, 100));
        assertEquals(394, Stats.other(130, 31, 252, 100, 1.1), "Attack with +10 % nature, max IV and EV");
    }

    @Test
    void blisseyHasTheHighestHpOfTheGames() {
        assertEquals(714, Stats.hp(255, 31, 252, 100));
    }

    @Test
    void theNatureChangesOnlyTheStatsItNames() {
        StatBlock ivs = StatBlock.all(31);
        StatBlock evs = StatBlock.all(0);
        StatBlock neutral = Stats.compute(GARCHOMP, ivs, evs, 50, Nature.find("serious").orElseThrow());
        StatBlock jolly = Stats.compute(GARCHOMP, ivs, evs, 50, Nature.find("jolly").orElseThrow()); // +Spe -SpA
        assertEquals(neutral.hp(), jolly.hp(), "HP is never changed");
        assertEquals(neutral.attack(), jolly.attack());
        assertEquals(neutral.speed() * 11 / 10, jolly.speed());
        assertEquals(neutral.specialAttack() * 9 / 10, jolly.specialAttack());
    }

    @Test
    void naturesSwitchedOffCountAsNeutral() {
        PokemonSettings off = PokemonSettings.defaults().withNaturesEnabled(false);
        Nature jolly = Nature.find("jolly").orElseThrow();
        assertTrue(off.natureToUse(jolly).neutral());
        assertEquals(jolly, PokemonSettings.defaults().natureToUse(jolly));
    }

    @Test
    void eachEvNeedsFourPointsToCountOnce() {
        assertEquals(Stats.hp(100, 0, 0, 100), Stats.hp(100, 0, 3, 100));
        assertEquals(Stats.hp(100, 0, 0, 100) + 1, Stats.hp(100, 0, 4, 100));
    }

    @Test
    void numbersOutsideTheLimitsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Stats.hp(100, 32, 0, 50), "IV above 31");
        assertThrows(IllegalArgumentException.class, () -> Stats.hp(100, -1, 0, 50), "IV below 0");
        assertThrows(IllegalArgumentException.class, () -> Stats.hp(100, 0, 253, 50), "EV above 252");
        assertThrows(IllegalArgumentException.class, () -> Stats.hp(100, 0, 0, 0), "level 0");
        assertThrows(IllegalArgumentException.class, () -> Stats.hp(100, 0, 0, 101), "level 101");
        StatBlock tooMany = new StatBlock(252, 252, 252, 0, 0, 0); // 756 in total
        assertThrows(IllegalArgumentException.class,
                () -> Stats.compute(GARCHOMP, StatBlock.all(0), tooMany, 50, Nature.find("hardy").orElseThrow()));
        StatBlock exactly510 = new StatBlock(252, 252, 6, 0, 0, 0);
        Stats.compute(GARCHOMP, StatBlock.all(0), exactly510, 50, Nature.find("hardy").orElseThrow());
    }

    @Test
    void statStagesFollowTheTwoOverTwoRule() {
        assertEquals(1.0, Stats.stageMultiplier(0));
        assertEquals(1.5, Stats.stageMultiplier(1));
        assertEquals(2.0, Stats.stageMultiplier(2));
        assertEquals(4.0, Stats.stageMultiplier(6));
        assertEquals(2.0 / 3.0, Stats.stageMultiplier(-1), 1e-12);
        assertEquals(0.5, Stats.stageMultiplier(-2));
        assertEquals(0.25, Stats.stageMultiplier(-6));
        assertEquals(4.0, Stats.stageMultiplier(9), "kept within +6");
        assertEquals(0.25, Stats.stageMultiplier(-9), "kept within -6");
        assertEquals(150, Stats.applyStage(100, 1));
        assertEquals(66, Stats.applyStage(100, -1), "rounded down");
    }

    @Test
    void accuracyStagesFollowTheThreeOverThreeRuleOfChapter7_5() {
        assertEquals(0.50, Stats.accuracyStageMultiplier(-3), 1e-12);
        assertEquals(0.60, Stats.accuracyStageMultiplier(-2), 1e-12);
        assertEquals(0.75, Stats.accuracyStageMultiplier(-1), 1e-12);
        assertEquals(1.00, Stats.accuracyStageMultiplier(0), 1e-12);
        assertEquals(4.0 / 3.0, Stats.accuracyStageMultiplier(1), 1e-12);
        assertEquals(5.0 / 3.0, Stats.accuracyStageMultiplier(2), 1e-12);
        assertEquals(2.00, Stats.accuracyStageMultiplier(3), 1e-12);
    }

    @Test
    void randomIvsAreInRangeAndFixedIvsAreAllTheSame() {
        RandomGenerator random = new java.util.Random(7);
        boolean differ = false;
        for (int i = 0; i < 200; i++) {
            StatBlock ivs = Ivs.generate(PokemonSettings.defaults(), random);
            for (Stat s : Stat.values()) {
                assertTrue(ivs.get(s) >= 0 && ivs.get(s) <= 31);
            }
            differ |= ivs.hp() != ivs.attack();
        }
        assertTrue(differ, "random IVs differ between stats");
        assertEquals(StatBlock.all(15), Ivs.generate(PokemonSettings.defaults().withFixedIvs(15), random), "default fixed value");
        assertEquals(StatBlock.all(31), Ivs.generate(PokemonSettings.defaults().withFixedIvs(31), random));
        assertThrows(IllegalArgumentException.class, () -> PokemonSettings.defaults().withFixedIvs(32));
    }

    @Test
    void defaultsAreTheOnesOfChapter5_12() {
        PokemonSettings d = PokemonSettings.defaults();
        assertEquals(100, d.levelCap());
        assertEquals(PokemonSettings.XpShare.GEN6, d.xpShare());
        assertEquals(1.0, d.xpMultiplier());
        assertEquals(PokemonSettings.IvMode.RANDOM, d.ivMode());
        assertEquals(15, d.ivFixedValue());
        assertTrue(d.naturesEnabled());
        assertEquals(PokemonSettings.IvMode.FIXED, PokemonSettings.IvMode.parse("Fixed"));
        assertEquals(PokemonSettings.XpShare.PARTICIPANTS_ONLY, PokemonSettings.XpShare.parse("participantsOnly"));
    }

    @Test
    void ivCandiesStayWithinZeroAndThirtyOne() {
        assertEquals(16, Ivs.adjust(15, 1));
        assertEquals(31, Ivs.adjust(31, 1));
        assertEquals(0, Ivs.adjust(0, -1));
    }

    @Test
    void evGainsStopAt252PerStatAnd510InTotal() {
        StatBlock start = new StatBlock(0, 250, 0, 0, 0, 0);
        assertEquals(new StatBlock(0, 252, 0, 0, 0, 0), Evs.gain(start, new StatBlock(0, 3, 0, 0, 0, 0)));
        StatBlock nearFull = new StatBlock(252, 252, 0, 0, 0, 0); // 504 so far
        assertEquals(new StatBlock(252, 252, 4, 2, 0, 0), Evs.gain(nearFull, new StatBlock(0, 0, 4, 4, 4, 0)),
                "only 6 points are left in total");
        assertEquals(StatBlock.all(0).with(Stat.SPEED, 2), Evs.gain(StatBlock.all(0), StatBlock.all(0).with(Stat.SPEED, 2)));
    }
}
