/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.dice;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dice notation (chapter 6.2): {@code NdS} means roll N dice with S sides and add them up.
 *
 * @param count number of dice, at least 1
 * @param sides sides of each die, at least 2
 */
public record Dice(int count, int sides) {

    private static final Pattern NOTATION = Pattern.compile("^\\s*(\\d*)\\s*[dD]\\s*(\\d+)\\s*$");

    public Dice {
        if (count < 1) {
            throw new IllegalArgumentException("Roll at least one die, not " + count);
        }
        if (sides < 2) {
            throw new IllegalArgumentException("A die has at least 2 sides, not " + sides);
        }
    }

    /** One die with this many sides. */
    public static Dice d(int sides) {
        return new Dice(1, sides);
    }

    /**
     * Reads {@code d20}, {@code 1d20}, {@code 2d8} (spaces and capital D allowed). Anything else gives an empty result.
     */
    public static Optional<Dice> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = NOTATION.matcher(text);
        if (!m.matches()) {
            return Optional.empty();
        }
        try {
            int count = m.group(1).isEmpty() ? 1 : Integer.parseInt(m.group(1));
            int sides = Integer.parseInt(m.group(2));
            if (count < 1 || sides < 2) {
                return Optional.empty();
            }
            return Optional.of(new Dice(count, sides));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** The lowest total that can be rolled. */
    public int min() {
        return count;
    }

    /** The highest total that can be rolled. */
    public int max() {
        return count * sides;
    }

    /** {@code d20} for one die, {@code 2d8} for several. */
    @Override
    public String toString() {
        return (count == 1 ? "" : String.valueOf(count)) + "d" + sides;
    }
}
