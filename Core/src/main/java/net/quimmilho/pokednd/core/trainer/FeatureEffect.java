/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * One small machine-readable part of a class feature (chapter 4.4).
 *
 * @param key    a key of {@link EffectKeys}, for example {@code accuracy.bonusPp}
 * @param value  the number: percentage points, a stage, uses per battle, and so on; 0 when only the parameters matter
 * @param params extra detail as text: {@code skill} / {@code skills} for skill effects, {@code types} and {@code stat} for
 *               {@code battle.startStage}, {@code die} for {@code battle.styleDie}, {@code hpPct} for session powers,
 *               {@code text} for {@code narrative}, {@code scalesWith=proficiency} to multiply the value by the proficiency bonus
 */
public record FeatureEffect(String key, double value, Map<String, String> params) {

    /** The parameter that makes {@link #amount(int)} multiply the value by the proficiency bonus. */
    public static final String SCALES_WITH = "scalesWith";

    public FeatureEffect {
        EffectKeys.combine(key); // refuses an unknown key
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    /** An effect with only a number. */
    public static FeatureEffect of(String key, double value) {
        return new FeatureEffect(key, value, Map.of());
    }

    /** An effect with a number and parameters. */
    public static FeatureEffect of(String key, double value, Map<String, String> params) {
        return new FeatureEffect(key, value, params);
    }

    /** A text-only effect. */
    public static FeatureEffect narrative(String text) {
        return new FeatureEffect("narrative", 0, Map.of("text", text));
    }

    /** The number this effect adds, with {@code scalesWith=proficiency} applied. */
    public double amount(int proficiencyBonus) {
        return "proficiency".equals(params.get(SCALES_WITH)) ? value * proficiencyBonus : value;
    }

    /** A parameter that holds a comma-separated list, as a list. */
    public List<String> list(String name) {
        String text = params.get(name);
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
