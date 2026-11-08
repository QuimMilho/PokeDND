/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.quimmilho.pokednd.core.rules.PokemonSettings.XpShare;
import net.quimmilho.pokednd.core.rules.TrainerXp.Event;
import org.junit.jupiter.api.Test;

class ExperienceTest {

    /** The table of chapter 5.3, typed in separately from the formulas. Levels 2, 5, 10, 20, 30, 50, 75, 100. */
    private static final int[] LEVELS = {2, 5, 10, 20, 30, 50, 75, 100};
    private static final Map<Growth, int[]> TABLE = Map.of(
            Growth.FAST, new int[] {6, 100, 800, 6_400, 21_600, 100_000, 337_500, 800_000},
            Growth.MEDIUM_FAST, new int[] {8, 125, 1_000, 8_000, 27_000, 125_000, 421_875, 1_000_000},
            Growth.MEDIUM_SLOW, new int[] {9, 135, 560, 5_460, 21_760, 117_360, 429_235, 1_059_860},
            Growth.SLOW, new int[] {10, 156, 1_250, 10_000, 33_750, 156_250, 527_343, 1_250_000},
            Growth.ERRATIC, new int[] {15, 237, 1_800, 12_800, 37_800, 125_000, 326_531, 600_000},
            Growth.FLUCTUATING, new int[] {4, 65, 540, 5_440, 23_760, 142_500, 582_187, 1_640_000});

    @Test
    void theXpTableOfChapter5_3IsReproducedForAllSixGrowthGroups() {
        for (Growth g : Growth.values()) {
            int[] expected = TABLE.get(g);
            for (int i = 0; i < LEVELS.length; i++) {
                assertEquals(expected[i], g.totalXp(LEVELS[i]), g + " level " + LEVELS[i]);
            }
        }
    }

    @Test
    void xpIsStrictlyIncreasingPerLevelAndLevel100IsReachedForAllSixGroups() {
        for (Growth g : Growth.values()) {
            assertEquals(0, g.totalXp(1), g + ": level 1 is 0 XP");
            for (int level = 2; level <= 100; level++) {
                assertTrue(g.totalXp(level) > g.totalXp(level - 1), g + " must increase at level " + level);
            }
            assertEquals(100, g.levelFor(g.totalXp(100), 100), g + " reaches level 100");
            assertEquals(100, g.levelFor(Integer.MAX_VALUE, 100));
        }
    }

    @Test
    void levelsAreFoundFromTotalXp() {
        Growth g = Growth.MEDIUM_FAST;
        assertEquals(1, g.levelFor(0, 100));
        assertEquals(1, g.levelFor(7, 100));
        assertEquals(2, g.levelFor(8, 100));
        assertEquals(10, g.levelFor(1_000, 100), "1,000 XP is level 10");
        assertEquals(9, g.levelFor(999, 100));
        assertEquals(50, g.levelFor(10_000_000, 50), "the level cap stops it");
        assertEquals(271, g.xpToNext(9), "1,000 - 729");
    }

    @Test
    void growthGroupsAreReadFromTheDataNames() {
        assertEquals(Growth.MEDIUM_SLOW, Growth.parse("medium_slow").orElseThrow());
        assertEquals(Growth.MEDIUM_SLOW, Growth.parse("Medium Slow").orElseThrow());
        assertEquals(Growth.FLUCTUATING, Growth.parse("fluctuating").orElseThrow());
        assertTrue(Growth.parse("sideways").isEmpty());
        assertTrue(Growth.parse(null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Growth.FAST.totalXp(0));
        assertThrows(IllegalArgumentException.class, () -> Growth.FAST.totalXp(101));
    }

    @Test
    void theWorkedExampleOfChapter5_3ADefeatedPidgey() {
        PokemonSettings s = PokemonSettings.defaults();
        // A level-12 wild Pidgey (b = 50) is defeated by a level-10 Pokémon.
        assertEquals(140, Experience.reward(50, 12, 10, true, false, false, s), "participant");
        assertEquals(70, Experience.reward(50, 12, 10, false, false, false, s), "team-mate who sat out");
        assertEquals(210, Experience.reward(50, 12, 10, true, true, false, s), "the Pidgey belonged to an NPC trainer");
    }

    @Test
    void theShareRuleAndTheMultiplierChangeTheReward() {
        PokemonSettings base = PokemonSettings.defaults();
        assertEquals(0, Experience.reward(50, 12, 10, false, false, false, base.withXpShare(XpShare.PARTICIPANTS_ONLY)));
        assertEquals(140, Experience.reward(50, 12, 10, true, false, false, base.withXpShare(XpShare.PARTICIPANTS_ONLY)));
        assertEquals(140, Experience.reward(50, 12, 10, false, false, false, base.withXpShare(XpShare.ALL)));
        assertEquals(280, Experience.reward(50, 12, 10, true, false, false, base.withXpMultiplier(2.0)));
        assertEquals(210, Experience.reward(50, 12, 10, true, false, true, base), "Lucky Egg is x1.5");
    }

    @Test
    void aHigherLevelReceiverGetsLessAndALowerOneGetsMore() {
        PokemonSettings s = PokemonSettings.defaults();
        int low = Experience.reward(100, 30, 10, true, false, false, s);
        int even = Experience.reward(100, 30, 30, true, false, false, s);
        int high = Experience.reward(100, 30, 60, true, false, false, s);
        assertTrue(low > even && even > high, low + " > " + even + " > " + high);
    }

    @Test
    void addingXpRaisesTheLevelUntilTheCap() {
        PokemonSettings s = PokemonSettings.defaults();
        Experience.Gain g = Experience.add(Growth.MEDIUM_FAST, 0, 1_000, s);
        assertEquals(new Experience.Gain(1_000, 10, 9), g);
        Experience.Gain capped = Experience.add(Growth.MEDIUM_FAST, 0, 2_000_000, s.withLevelCap(50));
        assertEquals(50, capped.level());
        assertEquals(125_000, capped.totalXp(), "XP stops at the cap");
    }

    // ------------------------------------------------------------------------------------------ trainers (4.5)

    @Test
    void theTrainerXpTableOfChapter4_5() {
        TrainerXp t = TrainerXp.defaults();
        int[] levels = {2, 3, 4, 5, 8, 10, 15, 20};
        long[] total = {50, 150, 300, 500, 1_400, 2_250, 5_250, 9_500};
        for (int i = 0; i < levels.length; i++) {
            assertEquals(total[i], t.totalXp(levels[i]), "level " + levels[i]);
        }
        assertEquals(0, t.totalXp(1));
    }

    @Test
    void trainersLevelUpFromXpAndStopAtTheCap() {
        TrainerXp t = TrainerXp.defaults();
        assertEquals(1, t.levelFor(49));
        assertEquals(2, t.levelFor(50));
        assertEquals(8, t.levelFor(1_400));
        assertEquals(20, t.levelFor(9_500));
        assertEquals(20, t.levelFor(1_000_000), "the cap is 20");
        assertEquals(10, t.withLevelCap(10).levelFor(1_000_000));
        assertEquals(3, t.withCurveFactor(10).levelFor(60), "10 × 3 × 2 = 60");
    }

    @Test
    void eachTrainerEventGivesTheXpOfChapter4_5() {
        TrainerXp t = TrainerXp.defaults();
        assertEquals(12, t.award(Event.DEFEAT_WILD, 12), "its level");
        assertEquals(150, t.award(Event.DEFEAT_TRAINER, 30), "5 × the highest level");
        assertEquals(500, t.award(Event.DEFEAT_BOSS, 50), "10 × its level");
        assertEquals(10, t.award(Event.CATCH, 0));
        assertEquals(20, t.award(Event.EVOLVE_OR_HATCH, 0));
        assertEquals(10, t.award(Event.DISCOVER_NODE, 0));
        assertEquals(15, t.award(Event.FIRST_SKILL_CHECK, 0));
        assertEquals(100, t.award(Event.QUEST, 0), "default quest");
        assertEquals(250, t.award(Event.QUEST, 250), "a quest sets its own");
        assertThrows(IllegalArgumentException.class, () -> t.award(Event.DEFEAT_WILD, 0));
    }

    @Test
    void theTrainerMultiplierScalesEverything() {
        TrainerXp twice = TrainerXp.defaults().withMultiplier(2.0);
        assertEquals(24, twice.award(Event.DEFEAT_WILD, 12));
        assertEquals(20, twice.award(Event.CATCH, 0));
        assertEquals(15, TrainerXp.defaults().withMultiplier(1.5).award(Event.CATCH, 0));
        assertEquals(22, TrainerXp.defaults().withMultiplier(1.5).award(Event.FIRST_SKILL_CHECK, 0), "22.5 rounds down");
        assertEquals(30, TrainerXp.defaults().withReward(Event.CATCH, new TrainerXp.Reward(30, false)).award(Event.CATCH, 0));
    }
}
