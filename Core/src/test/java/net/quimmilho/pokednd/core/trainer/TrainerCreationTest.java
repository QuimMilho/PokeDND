/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.quimmilho.pokednd.core.rules.TrainerXp;
import net.quimmilho.pokednd.core.trainer.TrainerRulebook.CreateRequest;
import org.junit.jupiter.api.Test;

/** Creating a trainer (chapter 4.3) and refusing the ones that break the rules. */
class TrainerCreationTest {

    private static final TrainerRulebook RULES = TrainerRulebook.defaults();
    /** Costs 9 + 7 + 5 + 3 + 2 + 1 = 27 points. */
    private static final AbilityScores POINT_BUY_27 = AbilityScores.of(15, 14, 13, 11, 10, 9);

    private static CreateRequest ace(String name, AbilityScores scores) {
        return new CreateRequest(name, "ace_trainer", ScoreMethod.POINT_BUY, scores, List.of("pokemon_handling", "insight"));
    }

    private static Set<String> codes(List<TrainerProblem> problems) {
        return problems.stream().map(TrainerProblem::code).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void theModifierOfAScoreIsFloorOfScoreMinus10OverTwo() {
        int[][] cases = {{1, -5}, {3, -4}, {8, -1}, {9, -1}, {10, 0}, {11, 0}, {12, 1}, {13, 1}, {15, 2}, {18, 4}, {20, 5}};
        for (int[] c : cases) {
            assertEquals(c[1], Ability.modifier(c[0]), "score " + c[0]);
        }
    }

    @Test
    void theProficiencyBonusFollowsTheTable() {
        int[] expected = {2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 6, 6, 6, 6};
        for (int level = 1; level <= 20; level++) {
            assertEquals(expected[level - 1], TrainerRulebook.proficiencyBonus(level), "level " + level);
        }
    }

    @Test
    void thePointBuyCostsMatchTheTable() {
        int[][] costs = {{8, 0}, {9, 1}, {10, 2}, {11, 3}, {12, 4}, {13, 5}, {14, 7}, {15, 9}};
        for (int[] c : costs) {
            assertEquals(c[1], AbilityScoreRules.pointBuyCost(c[0]), "score " + c[0]);
        }
        assertThrows(IllegalArgumentException.class, () -> AbilityScoreRules.pointBuyCost(7));
        assertThrows(IllegalArgumentException.class, () -> AbilityScoreRules.pointBuyCost(16));
        assertEquals(27, AbilityScoreRules.pointBuySpent(POINT_BUY_27));
    }

    @Test
    void aValidTrainerGetsTheStartingKitAndFullHp() {
        Trainer t = RULES.create(ace("Ana", POINT_BUY_27));
        assertEquals("Ana", t.name());
        assertEquals("ace_trainer", t.classId());
        assertEquals(1, t.level());
        assertEquals(0, t.xp());
        assertEquals(3000, t.money());
        assertEquals(Map.of("poke_ball", 5, "potion", 3), t.items());
        // CON 13 gives +1: HP is the d8 at its maximum plus 1
        assertEquals(9, t.maxHp());
        assertEquals(t.maxHp(), t.hp());
    }

    @Test
    void classExtrasAreAddedToTheCommonKit() {
        AbilityScores s = POINT_BUY_27;
        Trainer ranger = RULES.create(new CreateRequest("Rex", "ranger", ScoreMethod.POINT_BUY, s, List.of("survival", "stealth")));
        assertEquals(Map.of("poke_ball", 8, "potion", 3), ranger.items());
        Trainer medic = RULES.create(new CreateRequest("Mia", "medic", ScoreMethod.POINT_BUY, s, List.of("medicine", "insight")));
        assertEquals(Map.of("poke_ball", 5, "potion", 3, "antidote", 2, "revive", 1), medic.items());
        Trainer collector = RULES.create(new CreateRequest("Cal", "collector", ScoreMethod.POINT_BUY, s, List.of("appraisal", "insight")));
        assertEquals(4000, collector.money());
        assertEquals(3000, RULES.create(ace("Ana", s)).money());
    }

    @Test
    void levelOneHpIsTheMaximumOfTheHitDiePlusTheConModifier() {
        // CON 14 is +2; 8 + 8 + 14 + 8 + 8 + 15 costs 0+0+7+0+0+9 = 16 points
        AbilityScores con14 = AbilityScores.of(8, 8, 14, 8, 8, 15);
        assertEquals(10, create("ace_trainer", con14, "insight", "athletics").maxHp(), "d8 + 2");
        assertEquals(8, create("researcher", con14, "insight", "nature").maxHp(), "d6 + 2");
        // The Hiker's Tough as Rock adds 1 HP per level from level 1: d10 + 2 + 1
        assertEquals(13, create("hiker", con14, "athletics", "survival").maxHp());
        // CON 8 is -1: d6 - 1 = 5
        assertEquals(5, create("researcher", AbilityScores.of(15, 15, 8, 8, 8, 8), "insight", "nature").maxHp());
        // A tiny die with a terrible CON still gives 1 HP
        TrainerClass tiny = new TrainerClass("tiny", "Tiny", "", 4, Ability.STR, List.of(Ability.STR, Ability.CON),
                new TrainerClass.SkillChoices(0, List.of()), 0, 0, Map.of(), List.of());
        TrainerRulebook rules = RULES.withClasses(new TrainerClasses(List.of(tiny)))
                .withSettings(TrainerSettings.defaults().withAllowedMethods(ScoreMethod.parseAllowed("any")));
        Trainer t = rules.create(new CreateRequest("Tim", "tiny", ScoreMethod.ROLL_4D6_DROP_LOWEST, AbilityScores.of(10, 10, 3, 10, 10, 10), List.of()));
        assertEquals(1, t.maxHp());
    }

    private static Trainer create(String classId, AbilityScores scores, String... skills) {
        return RULES.create(new CreateRequest("Ana", classId, ScoreMethod.POINT_BUY, scores, List.of(skills)));
    }

    @Test
    void settingsChangeTheKit() {
        TrainerRulebook rules = RULES.withSettings(TrainerSettings.defaults().withStartMoney(500).withStartItems(Map.of("great_ball", 2)));
        Trainer t = rules.create(ace("Ana", POINT_BUY_27));
        assertEquals(500, t.money());
        assertEquals(Map.of("great_ball", 2), t.items());
    }

    // ---------------------------------------------------------------- refusals

    @Test
    void tooManyPointsIsRefusedWithTheCost() {
        // 15 x 6 = 54 points
        AbilityScores s = AbilityScores.of(15, 15, 15, 15, 15, 15);
        InvalidTrainerException e = assertThrows(InvalidTrainerException.class, () -> RULES.create(ace("Ana", s)));
        assertEquals(Set.of("too_many_points"), codes(e.problems()));
        assertEquals("These scores cost 54 points, but you only have 27.", e.getMessage());
    }

    @Test
    void aPointBuyScoreOutsideEightToFifteenIsRefusedByName() {
        AbilityScores s = AbilityScores.of(16, 8, 8, 8, 8, 7);
        List<TrainerProblem> problems = RULES.check(ace("Ana", s));
        assertEquals(Set.of("score_out_of_range"), codes(problems));
        assertTrue(problems.get(0).message().contains("Strength is 16"), problems.get(0).message());
        assertTrue(problems.get(1).message().contains("Charisma is 7"), problems.get(1).message());
    }

    @Test
    void anUnknownClassIsRefusedAndTheMessageListsTheClasses() {
        InvalidTrainerException e = assertThrows(InvalidTrainerException.class, () -> RULES.create(
                new CreateRequest("Ana", "wizard", ScoreMethod.POINT_BUY, POINT_BUY_27, List.of("insight", "athletics"))));
        assertEquals(Set.of("unknown_class"), codes(e.problems()));
        assertTrue(e.getMessage().contains("wizard") && e.getMessage().contains("ace_trainer") && e.getMessage().contains("medic"),
                e.getMessage());
        assertEquals(Set.of("unknown_class"), codes(RULES.check(new CreateRequest("Ana", null, ScoreMethod.POINT_BUY, POINT_BUY_27, List.of()))));
    }

    @Test
    void skillsMustBeTheRightNumberFromTheClassListAndDistinct() {
        CreateRequest one = new CreateRequest("Ana", "hiker", ScoreMethod.POINT_BUY, POINT_BUY_27, List.of("athletics"));
        assertEquals(Set.of("skill_count"), codes(RULES.check(one)));
        assertTrue(RULES.check(one).get(0).message().contains("picks 2 skills, but 1"), RULES.check(one).get(0).message());

        CreateRequest foreign = new CreateRequest("Ana", "hiker", ScoreMethod.POINT_BUY, POINT_BUY_27, List.of("athletics", "persuasion"));
        List<TrainerProblem> p = RULES.check(foreign);
        assertEquals(Set.of("skill_not_allowed"), codes(p));
        assertTrue(p.get(0).message().startsWith("Persuasion is not one of the skills the Hiker can pick"), p.get(0).message());

        CreateRequest twice = new CreateRequest("Ana", "hiker", ScoreMethod.POINT_BUY, POINT_BUY_27, List.of("athletics", "athletics"));
        assertEquals(Set.of("skill_duplicate"), codes(RULES.check(twice)));
    }

    @Test
    void nameRulesAreThreeToSixteenLettersDigitsAndUnderscores() {
        for (String ok : List.of("Ana", "Ash_Ketchum", "a1b", "ABCDEFGHIJKLMNOP", "___")) {
            assertEquals(List.of(), TrainerNames.check(ok), ok);
        }
        assertEquals(Set.of("name_length"), codes(TrainerNames.check("ab")));
        assertEquals(Set.of("name_length"), codes(TrainerNames.check("ABCDEFGHIJKLMNOPQ")));
        assertEquals(Set.of("name_characters"), codes(TrainerNames.check("Ash Ketchum")));
        assertEquals(Set.of("name_characters"), codes(TrainerNames.check("Zoë")));
        assertEquals(Set.of("name_missing"), codes(TrainerNames.check("")));
        assertEquals(Set.of("name_missing"), codes(TrainerNames.check(null)));
        assertEquals(Set.of("name_length", "name_characters"), codes(TrainerNames.check("a-")));
    }

    @Test
    void aNameAlreadyInTheGameIsRefusedIgnoringCase() {
        assertEquals(Set.of("name_taken"), codes(TrainerNames.check("ana", List.of("Ana", "Bob"))));
        assertEquals(List.of(), TrainerNames.check("Cat", List.of("Ana", "Bob")));
        assertThrows(InvalidTrainerException.class, () -> RULES.create(ace("ANA", POINT_BUY_27), List.of("Ana")));
    }

    @Test
    void everyProblemIsReportedAtOnce() {
        CreateRequest bad = new CreateRequest("x", "wizard", ScoreMethod.POINT_BUY, AbilityScores.of(15, 15, 15, 15, 15, 15), List.of());
        assertEquals(Set.of("name_length", "unknown_class", "too_many_points"), codes(RULES.check(bad)));
    }

    // ---------------------------------------------------------------- the three methods

    @Test
    void standardArrayAcceptsOnlyTheSixNumbersInAnyOrder() {
        TrainerRulebook rules = RULES.withSettings(TrainerSettings.defaults().withAllowedMethods(EnumSet.of(ScoreMethod.STANDARD_ARRAY)));
        CreateRequest ok = new CreateRequest("Ana", "ace_trainer", ScoreMethod.STANDARD_ARRAY, AbilityScores.of(8, 10, 12, 13, 14, 15),
                List.of("insight", "athletics"));
        assertEquals(List.of(), rules.check(ok));
        CreateRequest twice = new CreateRequest("Ana", "ace_trainer", ScoreMethod.STANDARD_ARRAY, AbilityScores.of(15, 15, 13, 12, 10, 8),
                List.of("insight", "athletics"));
        assertEquals(Set.of("not_standard_array"), codes(rules.check(twice)));
    }

    @Test
    void aMethodTheGameDoesNotAllowIsRefused() {
        // The default allows only point buy
        CreateRequest array = new CreateRequest("Ana", "ace_trainer", ScoreMethod.STANDARD_ARRAY, AbilityScores.of(8, 10, 12, 13, 14, 15),
                List.of("insight", "athletics"));
        assertEquals(Set.of("method_not_allowed"), codes(RULES.check(array)));
        TrainerRulebook any = RULES.withSettings(TrainerSettings.defaults().withAllowedMethods(ScoreMethod.parseAllowed("any")));
        assertEquals(List.of(), any.check(array));
    }

    @Test
    void rolledScoresMustBeThreeToEighteen() {
        TrainerRulebook rules = RULES.withSettings(TrainerSettings.defaults().withAllowedMethods(ScoreMethod.parseAllowed("roll4d6DropLowest")));
        CreateRequest ok = new CreateRequest("Ana", "ace_trainer", ScoreMethod.ROLL_4D6_DROP_LOWEST,
                AbilityScores.of(18, 3, 12, 14, 9, 16), List.of("insight", "athletics"));
        assertEquals(List.of(), rules.check(ok));
        CreateRequest high = new CreateRequest("Ana", "ace_trainer", ScoreMethod.ROLL_4D6_DROP_LOWEST,
                AbilityScores.of(19, 3, 12, 14, 9, 16), List.of("insight", "athletics"));
        assertEquals(Set.of("roll_out_of_range"), codes(rules.check(high)));
    }

    @Test
    void fourD6DropLowestDropsTheLowestDie() {
        int[] dice = {6, 1, 5, 4};
        AtomicInteger i = new AtomicInteger();
        assertEquals(15, AbilityScoreRules.fourD6DropLowest(() -> dice[i.getAndIncrement()]));
        assertEquals(3, AbilityScoreRules.fourD6DropLowest(() -> 1));
        assertEquals(18, AbilityScoreRules.fourD6DropLowest(() -> 6));
        assertThrows(IllegalArgumentException.class, () -> AbilityScoreRules.fourD6DropLowest(() -> 7));
        AtomicInteger n = new AtomicInteger();
        List<Integer> six = AbilityScoreRules.rollSix(() -> (n.getAndIncrement() % 6) + 1);
        assertEquals(6, six.size());
        assertTrue(six.stream().allMatch(v -> v >= 3 && v <= 18));
    }

    @Test
    void theSettingTextForTheAllowedMethodsRoundTrips() {
        assertEquals("any", ScoreMethod.allowedText(ScoreMethod.parseAllowed("any")));
        assertEquals("pointBuy", ScoreMethod.allowedText(ScoreMethod.parseAllowed("POINTBUY")));
        assertThrows(IllegalArgumentException.class, () -> ScoreMethod.parseAllowed("dice"));
    }

    @Test
    void aBiggerBudgetAllowsMoreExpensiveScores() {
        AbilityScores s = AbilityScores.of(15, 15, 15, 15, 15, 15);
        TrainerRulebook rich = RULES.withSettings(TrainerSettings.defaults().withPointBuyBudget(54));
        assertFalse(rich.check(ace("Ana", s)).stream().anyMatch(p -> p.code().equals("too_many_points")));
    }

    @Test
    void startingAboveLevelThreeNeedsTheImprovementsAlreadyEarned() {
        TrainerXp xp = new TrainerXp(25, 1.0, 5, 20, TrainerXp.defaultTable());
        TrainerRulebook rules = RULES.withSettings(TrainerSettings.defaults().withXp(xp));
        // Level 5 includes the improvement of level 4: two points to spend
        assertEquals(Set.of("improvement_points"), codes(rules.check(ace("Ana", POINT_BUY_27))));
        CreateRequest ok = new CreateRequest("Ana", "ace_trainer", ScoreMethod.POINT_BUY, POINT_BUY_27, List.of("pokemon_handling", "insight"),
                Map.of(Ability.CHA, 2));
        Trainer t = rules.create(ok);
        assertEquals(5, t.level());
        assertEquals(xp.totalXp(5), t.xp());
        assertEquals(500, t.xp());
        assertEquals(11, t.scores().get(Ability.CHA), "9 + the 2 improvement points");
        // CON 13 is +1: level 1 gives 8 + 1, each of levels 2 to 5 gives 5 + 1
        assertEquals(33, t.maxHp());
    }
}
