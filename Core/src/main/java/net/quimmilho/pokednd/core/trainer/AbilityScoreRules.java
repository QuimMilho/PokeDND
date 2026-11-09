/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/** The three ways to make ability scores (chapter 4.3) and the checks for each. There is no race: nothing is added afterwards. */
public final class AbilityScoreRules {

    /** The standard array, from the highest to the lowest. */
    public static final List<Integer> STANDARD_ARRAY = List.of(15, 14, 13, 12, 10, 8);
    /** The lowest point-buy score. */
    public static final int POINT_BUY_MIN = 8;
    /** The highest point-buy score. */
    public static final int POINT_BUY_MAX = 15;

    private AbilityScoreRules() {
    }

    /** The point-buy cost of a score: 8 costs 0, 9 costs 1, ... 13 costs 5, 14 costs 7, 15 costs 9. */
    public static int pointBuyCost(int score) {
        if (score < POINT_BUY_MIN || score > POINT_BUY_MAX) {
            throw new IllegalArgumentException("With point buy a score is " + POINT_BUY_MIN + " to " + POINT_BUY_MAX + ", not " + score);
        }
        return switch (score) {
            case 14 -> 7;
            case 15 -> 9;
            default -> score - POINT_BUY_MIN;
        };
    }

    /** The total point-buy cost of six scores. */
    public static int pointBuySpent(AbilityScores scores) {
        int total = 0;
        for (Ability a : Ability.values()) {
            total += pointBuyCost(scores.get(a));
        }
        return total;
    }

    /** One roll of 4d6 with the lowest die dropped. {@code d6} gives a number from 1 to 6 each time it is called. */
    public static int fourD6DropLowest(IntSupplier d6) {
        int sum = 0;
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            int die = d6.getAsInt();
            if (die < 1 || die > 6) {
                throw new IllegalArgumentException("A d6 shows 1 to 6, not " + die);
            }
            sum += die;
            lowest = Math.min(lowest, die);
        }
        return sum - lowest;
    }

    /** Six scores rolled with 4d6 drop the lowest, in the order rolled (the player assigns them). */
    public static List<Integer> rollSix(IntSupplier d6) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            out.add(fourD6DropLowest(d6));
        }
        return out;
    }

    /** Checks six scores against a method. Returns every problem found, empty if the scores are fine. */
    public static List<TrainerProblem> check(ScoreMethod method, AbilityScores scores, TrainerSettings settings) {
        List<TrainerProblem> problems = new ArrayList<>();
        if (!settings.allowedMethods().contains(method)) {
            problems.add(new TrainerProblem("method_not_allowed", "scores",
                    "This game does not allow ability scores by " + method.id() + "."));
            return problems;
        }
        switch (method) {
            case POINT_BUY -> {
                boolean inRange = true;
                for (Ability a : Ability.values()) {
                    int s = scores.get(a);
                    if (s < POINT_BUY_MIN || s > POINT_BUY_MAX) {
                        inRange = false;
                        problems.add(new TrainerProblem("score_out_of_range", "scores", a.label() + " is " + s
                                + ", but with point buy a score is " + POINT_BUY_MIN + " to " + POINT_BUY_MAX + "."));
                    }
                }
                if (inRange) {
                    int spent = pointBuySpent(scores);
                    if (spent > settings.pointBuyBudget()) {
                        problems.add(new TrainerProblem("too_many_points", "scores", "These scores cost " + spent
                                + " points, but you only have " + settings.pointBuyBudget() + "."));
                    }
                }
            }
            case STANDARD_ARRAY -> {
                if (!scores.sortedDescending().equals(STANDARD_ARRAY)) {
                    problems.add(new TrainerProblem("not_standard_array", "scores",
                            "The standard array is 15, 14, 13, 12, 10 and 8, each used once."));
                }
            }
            case ROLL_4D6_DROP_LOWEST -> {
                for (Ability a : Ability.values()) {
                    int s = scores.get(a);
                    if (s < 3 || s > 18) {
                        problems.add(new TrainerProblem("roll_out_of_range", "scores", a.label() + " is " + s
                                + ", but 4d6 drop the lowest gives 3 to 18."));
                    }
                }
            }
        }
        return problems;
    }
}
