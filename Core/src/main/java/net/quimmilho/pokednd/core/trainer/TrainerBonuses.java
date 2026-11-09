/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import net.quimmilho.pokednd.core.combat.CatchCalculator;
import net.quimmilho.pokednd.core.combat.FleeCalculator;

/**
 * How a trainer's sheet helps their Pokémon (chapter 4.2). The three conversion rates are small on purpose so that the sheet
 * helps without overpowering the official odds.
 *
 * <ul>
 *   <li><b>Command</b>: +1 percentage point of accuracy per point of the Pokémon Handling modifier, capped at +10.</li>
 *   <li><b>Catching</b>: the catch chance is multiplied by {@code 1 + 0.05 × Handling modifier} (see
 *       {@link CatchCalculator#trainerMultiplier}).</li>
 *   <li><b>Fleeing</b>: +2 percentage points per DEX modifier point (see {@link FleeCalculator}).</li>
 * </ul>
 */
public final class TrainerBonuses {

    /**
     * The command settings of chapter 4.7.
     *
     * @param accuracyPpPerPoint {@code combat.command.accuracyPpPerPoint}: percentage points per Handling point (default 1)
     * @param accuracyCapPp      {@code combat.command.accuracyCapPp}: the most the command bonus can be (default 10)
     */
    public record CommandSettings(double accuracyPpPerPoint, double accuracyCapPp) {

        public CommandSettings {
            if (accuracyPpPerPoint < 0 || accuracyCapPp < 0) {
                throw new IllegalArgumentException("The command bonus settings cannot be negative");
            }
        }

        /** The defaults: 1 percentage point per point, at most 10. */
        public static CommandSettings defaults() {
            return new CommandSettings(1.0, 10.0);
        }
    }

    private TrainerBonuses() {
    }

    /**
     * The accuracy bonus the trainer's active Pokémon get, in percentage points: one per Handling point, never below 0 and at
     * most the cap. (A negative Handling modifier gives no penalty: <em>Suggested</em>, see Appendix D of the specification.)
     */
    public static double commandAccuracyPp(int handlingModifier, CommandSettings settings) {
        double pp = Math.max(0, handlingModifier) * settings.accuracyPpPerPoint();
        return Math.min(pp, settings.accuracyCapPp());
    }

    /**
     * The catch multiplier: {@code 1 + bonusPerPoint × Handling modifier}, times {@code 1 + the class features' bonus}. A negative
     * Handling modifier counts as 0 unless {@code combat.catch.allowPenalty} is on.
     */
    public static double catchMultiplier(int handlingModifier, double featureBonus, CatchCalculator.Settings settings) {
        return CatchCalculator.trainerMultiplier(handlingModifier, 1.0 + featureBonus, settings);
    }

    /** The flee bonus in percentage points: DEX modifier times {@code combat.flee.bonusPpPerPoint}, plus the class features. */
    public static double fleeBonusPp(int dexModifier, double featureBonusPp, FleeCalculator.Settings settings) {
        return dexModifier * settings.bonusPpPerPoint() + featureBonusPp;
    }
}
