/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import java.util.EnumMap;
import java.util.Map;

/**
 * The six ability scores (chapter 4.1). Immutable.
 *
 * @param scores one score per ability, 1 to 30
 */
public record AbilityScores(Map<Ability, Integer> scores) {

    /** The highest score the rules allow (also the cap of an Ability Score Improvement). */
    public static final int IMPROVEMENT_CAP = 20;

    public AbilityScores {
        EnumMap<Ability, Integer> copy = new EnumMap<>(Ability.class);
        for (Ability a : Ability.values()) {
            Integer v = scores.get(a);
            if (v == null) {
                throw new IllegalArgumentException("The ability score " + a.id() + " is missing");
            }
            if (v < 1 || v > 30) {
                throw new IllegalArgumentException("An ability score is 1 to 30, not " + v + " (" + a.id() + ")");
            }
            copy.put(a, v);
        }
        scores = Map.copyOf(copy);
    }

    /** All six scores in the order STR, DEX, CON, INT, WIS, CHA. */
    public static AbilityScores of(int str, int dex, int con, int intelligence, int wis, int cha) {
        return new AbilityScores(Map.of(Ability.STR, str, Ability.DEX, dex, Ability.CON, con, Ability.INT, intelligence,
                Ability.WIS, wis, Ability.CHA, cha));
    }

    /** The score of one ability. */
    public int get(Ability ability) {
        return scores.get(ability);
    }

    /** The modifier of one ability. */
    public int modifier(Ability ability) {
        return Ability.modifier(get(ability));
    }

    /** A copy with one score changed. */
    public AbilityScores with(Ability ability, int value) {
        EnumMap<Ability, Integer> copy = new EnumMap<>(scores);
        copy.put(ability, value);
        return new AbilityScores(copy);
    }

    /** A copy with points added to some scores (not capped here: see {@link TrainerRulebook}). */
    public AbilityScores plus(Map<Ability, Integer> points) {
        EnumMap<Ability, Integer> copy = new EnumMap<>(scores);
        points.forEach((a, p) -> copy.merge(a, p, Integer::sum));
        return new AbilityScores(copy);
    }

    /** The scores sorted from the highest to the lowest. */
    public java.util.List<Integer> sortedDescending() {
        return scores.values().stream().sorted(java.util.Comparator.reverseOrder()).toList();
    }
}
