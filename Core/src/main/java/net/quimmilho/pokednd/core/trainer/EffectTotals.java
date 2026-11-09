/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Everything the class features of a trainer add up to at one level (chapter 4.4). Effects of the same key are added (or
 * multiplied, or switched on) as {@link EffectKeys} says.
 *
 * @param numbers    the total of every numeric key that appeared
 * @param expertise  skill ids with expertise
 * @param advantage  skill ids with advantage
 * @param reroll     skill ids that may be re-rolled
 * @param narrative  text-only effects, for the admin to adjudicate
 * @param startStages the {@code battle.startStage} effects, kept whole because each has its own types and stat
 */
public record EffectTotals(Map<String, Double> numbers, Set<String> expertise, Set<String> advantage, Set<String> reroll,
                           List<String> narrative, List<FeatureEffect> startStages) {

    public EffectTotals {
        numbers = Map.copyOf(numbers);
        expertise = Set.copyOf(expertise);
        advantage = Set.copyOf(advantage);
        reroll = Set.copyOf(reroll);
        narrative = List.copyOf(narrative);
        startStages = List.copyOf(startStages);
    }

    /** Adds up the effects of these features for a trainer with this proficiency bonus. */
    public static EffectTotals of(List<ClassFeature> features, int proficiencyBonus) {
        Map<String, Double> numbers = new TreeMap<>();
        Set<String> expertise = new LinkedHashSet<>();
        Set<String> advantage = new LinkedHashSet<>();
        Set<String> reroll = new LinkedHashSet<>();
        List<String> narrative = new ArrayList<>();
        List<FeatureEffect> stages = new ArrayList<>();
        for (ClassFeature f : features) {
            for (FeatureEffect e : f.effects()) {
                switch (e.key()) {
                    case "skill.expertise" -> addSkills(expertise, e);
                    case "skill.advantage" -> addSkills(advantage, e);
                    case "skill.reroll" -> addSkills(reroll, e);
                    case "narrative" -> narrative.add(e.params().getOrDefault("text", f.text()));
                    case "battle.startStage" -> stages.add(e);
                    default -> {
                        double amount = e.amount(proficiencyBonus);
                        switch (EffectKeys.combine(e.key())) {
                            case SUM -> numbers.merge(e.key(), amount, Double::sum);
                            case PRODUCT -> numbers.merge(e.key(), amount, (a, b) -> a * b);
                            case FLAG -> numbers.put(e.key(), 1.0);
                            case LIST -> { }
                        }
                    }
                }
            }
        }
        return new EffectTotals(numbers, expertise, advantage, reroll, narrative, stages);
    }

    private static void addSkills(Set<String> into, FeatureEffect e) {
        into.addAll(e.list("skills"));
        if (e.params().containsKey("skill")) {
            into.add(e.params().get("skill"));
        }
    }

    /**
     * The total of a key. A key no feature mentions is 0, or 1 for a multiplier such as {@code friendship.gainMultiplier}.
     * A flag such as {@code initiative.advantage} is 1 when on.
     */
    public double value(String key) {
        Double v = numbers.get(key);
        if (v != null) {
            return v;
        }
        return EffectKeys.combine(key) == EffectKeys.Combine.PRODUCT ? 1.0 : 0.0;
    }

    /** The total of a key as a whole number (rounded). */
    public int intValue(String key) {
        return (int) Math.round(value(key));
    }

    /** True if a flag such as {@code map.revealAll} is on. */
    public boolean has(String key) {
        return value(key) != 0.0;
    }
}
