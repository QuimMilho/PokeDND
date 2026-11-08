/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.dice;

import java.util.List;
import java.util.Locale;

/**
 * What the smart-dice rule decided for one roll (chapter 6.4): the die, which faces mean which outcome, and how far the real
 * chances are from the true ones.
 *
 * <p>The bands are listed from the worst outcome to the best, as given. With the default direction the worst outcome has the
 * lowest faces and the best outcome the highest ({@code 1–4 miss · 5–19 hit · 20 crit}).
 *
 * @param die                the number of sides: 4, 6, 8, 10, 12, 20 or 100; 0 when nothing needs to be rolled
 * @param rolled             false when there is only one possible outcome (a 100 % or 0 % chance): nobody rolls
 * @param percentileAsTwoD10 the die is a d100 but the table owns none, so it is rolled as two d10 (tens, then units; 00 is 100)
 * @param bands              one entry per outcome, in the order given
 * @param maxErrorPp         the largest difference between a real chance and the true chance, in percentage points
 * @param explanation        a plain-language sentence for the screen
 */
public record DiceChoice(int die, boolean rolled, boolean percentileAsTwoD10, List<Band> bands, double maxErrorPp,
                         String explanation) {

    /**
     * One outcome of the roll.
     *
     * @param label      what it is called ({@code miss}, {@code hit}, {@code crit}…)
     * @param trueChance the official chance, 0 to 1
     * @param faces      how many faces of the die mean this outcome (0 if it cannot happen)
     * @param from       the lowest face that means it (0 if it cannot happen or nothing is rolled)
     * @param to         the highest face that means it
     * @param realChance what the die really gives: faces divided by sides (1 when nothing is rolled and this is the only outcome)
     */
    public record Band(String label, double trueChance, int faces, int from, int to, double realChance) {

        /** The difference between the real and the true chance, in percentage points (never negative). */
        public double errorPp() {
            return Math.abs(realChance - trueChance) * 100.0;
        }

        /** True if this outcome can happen with this die. */
        public boolean possible() {
            return faces > 0 || realChance > 0;
        }

        /** {@code 5–19}, {@code 20}, or {@code -} for an outcome with no faces. */
        public String range() {
            if (faces == 0) {
                return "-";
            }
            return from == to ? String.valueOf(from) : from + "–" + to;
        }
    }

    public DiceChoice {
        bands = List.copyOf(bands);
    }

    /** The die in notation, {@code d20}; empty text when nothing is rolled. */
    public String dieName() {
        return die == 0 ? "" : "d" + die;
    }

    /** A chance as text: {@code 50 %}, {@code 33.3 %}, {@code 0.8 %}. */
    public static String percent(double chance) {
        double pct = Math.round(chance * 1000.0) / 10.0;
        String s = pct == Math.rint(pct) ? String.valueOf((long) pct) : String.format(Locale.ROOT, "%.1f", pct);
        return s + " %";
    }
}
