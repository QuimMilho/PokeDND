/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.quimmilho.pokednd.core.rules.TrainerXp;
import net.quimmilho.pokednd.core.trainer.TrainerRulebook.CreateRequest;
import org.junit.jupiter.api.Test;

/** Levelling up (chapter 4.5), the HP rule and the sheet (chapter 4.1). */
class TrainerLevellingTest {

    private static final TrainerRulebook RULES = TrainerRulebook.defaults();
    /** STR 15, DEX 14, CON 13 (+1), INT 11, WIS 10, CHA 9: 27 points. */
    private static final AbilityScores SCORES = AbilityScores.of(15, 14, 13, 11, 10, 9);
    /** Where the improvements go. Never CON: the HP check below assumes CON stays +1. */
    private static final Ability[] IMPROVE = {Ability.STR, Ability.DEX, Ability.INT, Ability.WIS, Ability.CHA};

    private static Trainer make(TrainerClass cls) {
        List<String> skills = cls.skillChoices().from().subList(0, 2);
        return RULES.create(new CreateRequest("Ana", cls.id(), ScoreMethod.POINT_BUY, SCORES, skills));
    }

    @Test
    void theTotalXpTableMatchesChapter4_5() {
        TrainerXp xp = RULES.settings().xp();
        long[][] table = {{2, 50}, {3, 150}, {4, 300}, {5, 500}, {8, 1400}, {10, 2250}, {15, 5250}, {20, 9500}};
        for (long[] row : table) {
            assertEquals(row[1], xp.totalXp((int) row[0]), "level " + row[0]);
        }
    }

    @Test
    void everyClassReachesLevel20FollowingChapter4_5() {
        for (TrainerClass cls : RULES.classes().classes()) {
            Trainer t = make(cls);
            int die = cls.hitDie();
            int perLevelBonus = cls.id().equals("hiker") ? 1 : 0;
            int expectedHp = die + 1 + perLevelBonus;

            t = RULES.addXp(t, RULES.settings().xp().totalXp(20));
            assertEquals(1, t.level(), "the level only moves when the player confirms");
            assertEquals(19, RULES.pendingLevels(t), cls.id());

            for (int level = 2; level <= 20; level++) {
                TrainerRulebook.LevelUpPreview preview = RULES.previewLevelUp(t);
                assertEquals(level, preview.level(), cls.id());
                assertEquals(TrainerRulebook.proficiencyBonus(level), preview.proficiencyBonus());
                assertEquals(cls.featuresAt(level), preview.newFeatures(), cls.id() + " level " + level);

                boolean asi = Set.of(4, 8, 12, 16, 19).contains(level);
                assertEquals(asi, preview.improvement(), cls.id() + " level " + level);
                Map<Ability, Integer> improvement = Map.of();
                if (asi) {
                    improvement = Map.of(IMPROVE[Math.min(4, level / 4)], 2);
                }
                // d6 gives 4, d8 gives 5, d10 gives 6 (rounded-up average) + CON +1 (+1 more for the Hiker)
                expectedHp += (die / 2 + 1) + 1 + perLevelBonus;
                assertEquals((die / 2 + 1) + 1 + perLevelBonus, preview.hpGain(), cls.id() + " level " + level);
                t = RULES.levelUp(t, improvement);
                assertEquals(level, t.level());
                assertEquals(expectedHp, t.maxHp(), cls.id() + " HP at level " + level);
            }
            assertEquals(0, RULES.pendingLevels(t), cls.id());
            assertEquals(9500, t.xp());
            assertEquals(t.maxHp(), t.hp(), "level-ups add to current HP too");
            // Five improvements of 2 points each were handed out
            int total = 0;
            for (Ability a : Ability.values()) {
                total += t.scores().get(a);
            }
            assertEquals(15 + 14 + 13 + 11 + 10 + 9 + 10, total, cls.id());
            for (Ability a : Ability.values()) {
                assertTrue(t.scores().get(a) <= 20, cls.id() + " " + a);
            }
            assertTrue(RULES.checkLevelUp(t, Map.of()).stream().anyMatch(p -> p.code().equals("no_level_pending")));
        }
    }

    @Test
    void xpWithoutConfirmingKeepsTheLevelAndTheCapStops() {
        Trainer t = make(RULES.classes().find("ranger").orElseThrow());
        assertEquals(0, RULES.pendingLevels(t));
        assertEquals(0, RULES.pendingLevels(RULES.addXp(t, 49)));
        assertEquals(1, RULES.pendingLevels(RULES.addXp(t, 50)));
        assertEquals(2, RULES.pendingLevels(RULES.addXp(t, 150)));
        assertEquals(19, RULES.pendingLevels(RULES.addXp(t, 1_000_000)), "level 20 is the cap");
        assertThrows(IllegalArgumentException.class, () -> RULES.addXp(t, -1));
    }

    @Test
    void aLevelUpNeedsEnoughXpAndSaysHowMuchIsMissing() {
        Trainer t = RULES.addXp(make(RULES.classes().find("ranger").orElseThrow()), 20);
        List<TrainerProblem> problems = RULES.checkLevelUp(t, Map.of());
        assertEquals("no_level_pending", problems.get(0).code());
        assertEquals("The trainer needs 30 more XP for level 2.", problems.get(0).message());
        assertThrows(InvalidTrainerException.class, () -> RULES.levelUp(t, Map.of()));
        assertThrows(InvalidTrainerException.class, () -> RULES.previewLevelUp(t));
    }

    @Test
    void theImprovementRulesAreChecked() {
        Trainer atThree = levelTo(make(RULES.classes().find("ace_trainer").orElseThrow()), 3);
        Trainer ready = RULES.addXp(atThree, 1000);
        // Level 4 gives an improvement: exactly 2 points
        assertEquals("improvement_points", RULES.checkLevelUp(ready, Map.of()).get(0).code());
        assertEquals("improvement_points", RULES.checkLevelUp(ready, Map.of(Ability.STR, 3)).get(0).code());
        assertEquals("improvement_points", RULES.checkLevelUp(ready, Map.of(Ability.STR, 1)).get(0).code());
        assertEquals(List.of(), RULES.checkLevelUp(ready, Map.of(Ability.STR, 1, Ability.DEX, 1)));
        assertEquals("improvement_negative", RULES.checkLevelUp(ready, Map.of(Ability.STR, 4, Ability.DEX, -2)).get(0).code());
        Trainer four = RULES.levelUp(ready, Map.of(Ability.STR, 1, Ability.DEX, 1));
        assertEquals(16, four.scores().get(Ability.STR));
        assertEquals(15, four.scores().get(Ability.DEX));
        // Level 5 has none
        assertEquals("improvement_not_allowed", RULES.checkLevelUp(four, Map.of(Ability.STR, 2)).get(0).code());
    }

    @Test
    void noScoreGoesAboveTwenty() {
        TrainerRulebook rules = RULES.withSettings(TrainerSettings.defaults().withAllowedMethods(ScoreMethod.parseAllowed("any")));
        Trainer t = rules.create(new CreateRequest("Ana", "ace_trainer", ScoreMethod.ROLL_4D6_DROP_LOWEST,
                AbilityScores.of(18, 12, 12, 12, 12, 12), List.of("insight", "athletics")));
        t = rules.addXp(t, 9500);
        t = levelTo(rules, t, 3);
        t = rules.levelUp(t, Map.of(Ability.STR, 2)); // level 4: STR 18 -> 20
        assertEquals(20, t.scores().get(Ability.STR));
        t = levelTo(rules, t, 7);
        List<TrainerProblem> problems = rules.checkLevelUp(t, Map.of(Ability.STR, 2)); // level 8 would make 22
        assertEquals("score_cap", problems.get(0).code());
        assertEquals("Strength would be 22, but no score goes above 20.", problems.get(0).message());
        assertEquals(List.of(), rules.checkLevelUp(t, Map.of(Ability.DEX, 2)));
    }

    @Test
    void conModifierAtLevelUpCountsFromThatLevelOn() {
        Trainer t = make(RULES.classes().find("ace_trainer").orElseThrow()); // CON 13, +1
        t = levelTo(RULES.addXp(t, 300), 3);
        Trainer four = RULES.levelUp(RULES.addXp(t, 10_000), Map.of(Ability.CON, 1, Ability.STR, 1));
        // CON 14 is +2: level 4 gives 5 + 2. Earlier levels are not recalculated.
        assertEquals(t.maxHp() + 7, four.maxHp());
    }

    @Test
    void healthOnLevelUpAddsToCurrentHp() {
        Trainer t = RULES.addXp(make(RULES.classes().find("hiker").orElseThrow()), 50).damage(5);
        Trainer two = RULES.levelUp(t, Map.of());
        assertEquals(t.maxHp() + 8, two.maxHp()); // d10: 6 + 1 CON + 1 Hiker
        assertEquals(t.hp() + 8, two.hp());
    }

    // ---------------------------------------------------------------- the sheet

    @Test
    void theSheetShowsAcSavesAndSkills() {
        Trainer t = RULES.create(new CreateRequest("Ana", "ace_trainer", ScoreMethod.POINT_BUY, SCORES, List.of("pokemon_handling", "athletics")));
        Sheet s = RULES.sheet(t);
        assertEquals(2, s.proficiencyBonus());
        assertEquals(12, s.armorClass(), "10 + DEX 14 (+2)");
        assertEquals(Map.of(Ability.STR, 2, Ability.DEX, 2, Ability.CON, 1, Ability.INT, 0, Ability.WIS, 0, Ability.CHA, -1), s.modifiers());
        // The Ace Trainer is proficient in DEX and CHA saves
        assertEquals(4, s.savingThrows().get(Ability.DEX));
        assertEquals(1, s.savingThrows().get(Ability.CHA));
        assertEquals(2, s.savingThrows().get(Ability.STR));
        // Athletics (STR +2, proficient +2), Pokémon Handling (WIS +0, proficient +2), Insight (WIS +0, not proficient)
        assertEquals(4, s.skillBonus("athletics"));
        assertEquals(2, s.skillBonus("pokemon_handling"));
        assertEquals(0, s.skillBonus("insight"));
        assertEquals(2, s.handlingModifier());
        assertEquals(Set.of("athletics", "pokemon_handling"), s.proficient());
        assertThrows(IllegalArgumentException.class, () -> s.skillBonus("juggling"));
    }

    @Test
    void expertiseDoublesTheBonusAndGrantsProficiency() {
        Trainer t = RULES.create(new CreateRequest("Ana", "researcher", ScoreMethod.POINT_BUY, SCORES, List.of("medicine", "insight")));
        Sheet s = RULES.sheet(t);
        // Field Notes: expertise in Pokémon Lore. INT +0, plus proficiency twice (+2 +2)
        assertEquals(4, s.skillBonus("pokemon_lore"));
        assertTrue(s.proficient().contains("pokemon_lore"));
        assertEquals(2, s.skillBonus("medicine"));
        assertEquals(0, s.skillBonus("nature"));
    }

    @Test
    void anAcBonusOfAClassCounts() {
        TrainerClass armoured = new TrainerClass("guard", "Guard", "", 10, Ability.STR, List.of(Ability.STR, Ability.CON),
                new TrainerClass.SkillChoices(0, List.of()), 2, 0, Map.of(), List.of());
        TrainerRulebook rules = RULES.withClasses(new TrainerClasses(List.of(armoured)));
        Trainer t = rules.create(new CreateRequest("Gus", "guard", ScoreMethod.POINT_BUY, SCORES, List.of()));
        assertEquals(10 + 2 + 2, rules.sheet(t).armorClass());
    }

    @Test
    void featuresAddUpAtTheRightLevels() {
        Sheet ace1 = RULES.sheet(atLevel("ace_trainer", 1));
        assertEquals(2.0, ace1.featureAccuracyPp());
        assertEquals(0, ace1.effects().value("crit.stageBonus"));
        Sheet ace20 = RULES.sheet(atLevel("ace_trainer", 20));
        assertEquals(5.0, ace20.featureAccuracyPp(), "2 + 3");
        assertEquals(1, ace20.effects().intValue("crit.stageBonus"));
        assertTrue(ace20.effects().has("initiative.advantage"));
        assertFalse(ace1.effects().has("initiative.advantage"));

        Sheet collector20 = RULES.sheet(atLevel("collector", 20));
        assertEquals(25, collector20.effects().intValue("shop.discountPct"), "5 + 20: buy x0.75");
        assertEquals(50, collector20.effects().intValue("shop.sellBonusPct"), "5 + 45: sell x1.5");
        assertEquals(5, collector20.effects().intValue("bag.extraSlots"), "Hidden Stash: +5 slots");

        assertEquals(2.0, RULES.sheet(atLevel("breeder", 1)).effects().value("friendship.gainMultiplier"));
        assertEquals(1.5, RULES.sheet(atLevel("coordinator", 6)).effects().value("friendship.gainMultiplier"));
        assertEquals(1.0, RULES.sheet(atLevel("ace_trainer", 20)).effects().value("friendship.gainMultiplier"), "a multiplier nobody sets is 1");
        assertEquals(1, RULES.sheet(atLevel("coordinator", 3)).effects().intValue("battle.styleDie"));
        assertEquals(2, RULES.sheet(atLevel("coordinator", 10)).effects().intValue("battle.styleDie"), "Appeal works twice");
        assertEquals(3, RULES.sheet(atLevel("ranger", 14)).movementBonus(), "+1 and +2");
        assertEquals(1, RULES.sheet(atLevel("ranger", 1)).movementBonus());
        assertTrue(RULES.sheet(atLevel("psychic", 20)).effects().has("map.revealAll"));
        assertFalse(RULES.sheet(atLevel("psychic", 14)).effects().has("map.revealAll"));
        assertEquals(Set.of("survival", "perception"), RULES.sheet(atLevel("ranger", 1)).effects().advantage());
        assertTrue(RULES.sheet(atLevel("ranger", 1)).hasAdvantage("survival"));
        assertTrue(RULES.sheet(atLevel("researcher", 14)).canReroll("nature"));
        assertFalse(RULES.sheet(atLevel("researcher", 10)).canReroll("nature"));
    }

    @Test
    void theHikersDamageReductionIsTheProficiencyBonus() {
        assertEquals(2, RULES.sheet(atLevel("hiker", 1)).damageReduction());
        assertEquals(3, RULES.sheet(atLevel("hiker", 5)).damageReduction());
        assertEquals(6, RULES.sheet(atLevel("hiker", 20)).damageReduction());
        assertEquals(0, RULES.sheet(atLevel("ace_trainer", 20)).damageReduction());
    }

    @Test
    void narrativeEffectsAreKeptForTheAdmin() {
        List<String> notes = RULES.sheet(atLevel("breeder", 3)).effects().narrative();
        assertEquals(1, notes.size());
        assertTrue(notes.get(0).contains("Eggs hatch twice as fast"), notes.get(0));
    }

    // ---------------------------------------------------------------- helpers

    private static Trainer atLevel(String classId, int level) {
        TrainerClass cls = RULES.classes().find(classId).orElseThrow();
        return levelTo(RULES.addXp(make(cls), RULES.settings().xp().totalXp(level)), level);
    }

    private static Trainer levelTo(Trainer t, int level) {
        return levelTo(RULES, t, level);
    }

    /** Levels up with the improvements spread over STR, DEX, INT, WIS, CHA, until the trainer is at this level. */
    private static Trainer levelTo(TrainerRulebook rules, Trainer t, int level) {
        Trainer current = rules.addXp(t, Math.max(0, rules.settings().xp().totalXp(level) - t.xp()));
        while (current.level() < level) {
            int next = current.level() + 1;
            Map<Ability, Integer> improvement = Map.of();
            if (TrainerRulebook.givesImprovement(next)) {
                improvement = Map.of(IMPROVE[Math.min(4, next / 4)], 2);
            }
            current = rules.levelUp(current, improvement);
        }
        return current;
    }
}
