/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.dice;

import java.util.List;
import java.util.TreeSet;

/**
 * The settings that decide which die the smart-dice rule picks (chapter 6.4). The defaults are the ones of the specification.
 *
 * @param available            the dice the table owns, by number of sides: {@code dice.available}, default 4, 6, 8, 10, 12, 20, 100.
 *                             Only these sizes exist: 4, 6, 8, 10, 12, 20 and 100.
 * @param tolerancePp          {@code dice.tolerance.absolutePp}: a band may be off by at most this many percentage points (default 2.5)
 * @param toleranceRelativePct {@code dice.tolerance.relativePct}: and by at most this share of its own size (default 15)
 * @param d100BelowPct         {@code dice.d100Below}: if the rarest band is rarer than this percentage, use a d100 (default 5)
 * @param direction            {@code dice.direction}: roll high to succeed (default) or low
 */
public record DiceSettings(List<Integer> available, double tolerancePp, double toleranceRelativePct, double d100BelowPct,
                           Direction direction) {

    /** Which end of the die is good (chapter 6.2). */
    public enum Direction {
        /** The D&amp;D way: the best outcome has the highest numbers (default). */
        HIGH,
        /** The best outcome has the lowest numbers. */
        LOW
    }

    /** The sizes of dice that exist. */
    public static final List<Integer> ALL_DICE = List.of(4, 6, 8, 10, 12, 20, 100);

    public DiceSettings {
        available = List.copyOf(new TreeSet<>(available));
        if (available.isEmpty()) {
            throw new IllegalArgumentException("dice.available must list at least one die");
        }
        for (int d : available) {
            if (!ALL_DICE.contains(d)) {
                throw new IllegalArgumentException("dice.available: there is no d" + d + " (the dice are " + ALL_DICE + ")");
            }
        }
        if (tolerancePp < 0 || toleranceRelativePct < 0 || d100BelowPct < 0) {
            throw new IllegalArgumentException("the dice tolerances cannot be negative");
        }
    }

    /** The defaults of chapter 6.4. */
    public static DiceSettings defaults() {
        return new DiceSettings(ALL_DICE, 2.5, 15, 5, Direction.HIGH);
    }

    /** A copy that owns only these dice. */
    public DiceSettings withAvailable(List<Integer> dice) {
        return new DiceSettings(dice, tolerancePp, toleranceRelativePct, d100BelowPct, direction);
    }

    /** A copy with other tolerances. */
    public DiceSettings withTolerance(double absolutePp, double relativePct) {
        return new DiceSettings(available, absolutePp, relativePct, d100BelowPct, direction);
    }

    /** A copy with another direction. */
    public DiceSettings withDirection(Direction other) {
        return new DiceSettings(available, tolerancePp, toleranceRelativePct, d100BelowPct, other);
    }

    /** True if the table owns a d100. If not, the d100 is rolled as two d10 (tens and units; 00 is 100). */
    public boolean hasD100() {
        return available.contains(100);
    }
}
