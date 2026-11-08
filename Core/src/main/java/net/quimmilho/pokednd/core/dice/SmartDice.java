/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.dice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.quimmilho.pokednd.core.dice.DiceChoice.Band;
import net.quimmilho.pokednd.core.dice.DiceSettings.Direction;

/**
 * The smart-dice rule (chapter 6.4, Appendix B): given the real chances of the possible outcomes, pick the simplest die that
 * expresses them, so players roll a d4 for 25 % and a d100 only for awkward odds.
 *
 * <p>This is a port of the reference implementation in Appendix B.3 and gives the same results for every example in
 * chapter 6.5 and Appendix B. One deliberate addition: when the d100 is used, an outcome that is possible always keeps at least
 * one face (the reference would round a chance below 0.5 % down to zero faces and make it impossible).
 *
 * <p>All methods are pure: the same input always gives the same result.
 */
public final class SmartDice {

    /** The chances may be off by this much from adding up to 1 (rounding in the caller). */
    private static final double SUM_TOLERANCE = 1e-6;

    private SmartDice() {
    }

    /**
     * An outcome and its chance.
     *
     * @param label       what it is called
     * @param probability its chance, 0 to 1
     */
    public record Outcome(String label, double probability) {
    }

    /** The result for one success chance. */
    public enum Kind {
        /** A die decides. */
        ROLL,
        /** The chance is 100 %: it always succeeds, nobody rolls. */
        ALWAYS,
        /** The chance is 0 %: it never succeeds, nobody rolls. */
        NEVER
    }

    /**
     * What to roll for a yes/no chance.
     *
     * @param kind        roll, or always, or never
     * @param choice      the die and the bands (bands: fail, then success)
     * @param trueChance  the official chance, 0 to 1
     * @param successFrom lowest face that succeeds (0 when nothing is rolled)
     * @param successTo   highest face that succeeds
     */
    public record Success(Kind kind, DiceChoice choice, double trueChance, int successFrom, int successTo) {

        /** The die, or 0 when nothing is rolled. */
        public int die() {
            return choice.die();
        }

        /** The real chance of success. */
        public double realChance() {
            return choice.bands().get(1).realChance();
        }

        /** The smallest roll that succeeds when rolling high, e.g. 5 for "need 5 or more". Equals {@code successFrom}. */
        public int need() {
            return successFrom;
        }
    }

    // ---------------------------------------------------------------- several outcomes on one roll (B.2)

    /** Picks the die for outcomes with the default settings. Probabilities are 0 to 1 and add up to 1; list the worst outcome first. */
    public static DiceChoice choose(double... probabilities) {
        return choose(DiceSettings.defaults(), probabilities);
    }

    /** Picks the die for outcomes. The outcomes are labelled {@code 1}, {@code 2}… */
    public static DiceChoice choose(DiceSettings settings, double... probabilities) {
        List<Outcome> outcomes = new ArrayList<>();
        for (int i = 0; i < probabilities.length; i++) {
            outcomes.add(new Outcome(String.valueOf(i + 1), probabilities[i]));
        }
        return choose(settings, outcomes);
    }

    /**
     * Picks the die for a list of outcomes, worst first.
     *
     * @throws IllegalArgumentException if there are no outcomes, a chance is outside 0 to 1, or they do not add up to 1
     */
    public static DiceChoice choose(DiceSettings settings, List<Outcome> outcomes) {
        if (outcomes.isEmpty()) {
            throw new IllegalArgumentException("A roll needs at least one outcome");
        }
        double[] p = new double[outcomes.size()];
        double sum = 0;
        for (int i = 0; i < p.length; i++) {
            p[i] = outcomes.get(i).probability();
            if (!(p[i] >= 0.0 && p[i] <= 1.0)) {
                throw new IllegalArgumentException("A chance must be between 0 and 1, not " + p[i] + " (" + outcomes.get(i).label() + ")");
            }
            sum += p[i];
        }
        if (Math.abs(sum - 1.0) > SUM_TOLERANCE) {
            throw new IllegalArgumentException("The chances must add up to 1, not " + sum);
        }

        int possible = 0;
        for (double v : p) {
            possible += v > 0 ? 1 : 0;
        }
        if (possible == 1) {
            return noRoll(outcomes, p);
        }

        double rarest = Double.MAX_VALUE;
        for (double v : p) {
            if (v > 0) {
                rarest = Math.min(rarest, v);
            }
        }
        double tolAbs = settings.tolerancePp() / 100.0;
        double tolRel = settings.toleranceRelativePct() / 100.0;
        int die = 0;
        int[] counts = null;
        if (rarest >= settings.d100BelowPct() / 100.0 - 1e-9) {
            for (int sides : settings.available()) {
                if (sides == 100) {
                    continue;
                }
                int[] c = allocate(p, sides);
                if (accepted(p, c, sides, tolAbs, tolRel)) {
                    die = sides;
                    counts = c;
                    break;
                }
            }
        }
        if (counts == null) {
            die = 100;
            counts = percentile(p);
        }
        return build(settings, outcomes, p, die, counts);
    }

    /** Shares out the faces in proportion (largest remainder; a tie gives the extra face to the lower band). */
    static int[] allocate(double[] bands, int sides) {
        double[] raw = new double[bands.length];
        int[] counts = new int[bands.length];
        int left = sides;
        for (int i = 0; i < bands.length; i++) {
            raw[i] = bands[i] * sides;
            counts[i] = (int) Math.floor(raw[i]);
            left -= counts[i];
        }
        Integer[] order = new Integer[bands.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        // Stable sort: equal remainders keep their band order, so the lower band wins a tie.
        Arrays.sort(order, Comparator.comparingDouble((Integer i) -> raw[i] - Math.floor(raw[i])).reversed());
        for (int i : order) {
            if (left <= 0) {
                break;
            }
            counts[i]++;
            left--;
        }
        return counts;
    }

    /** Every possible band keeps a face and is within both tolerances. */
    private static boolean accepted(double[] bands, int[] counts, int sides, double tolAbs, double tolRel) {
        for (int i = 0; i < bands.length; i++) {
            double p = bands[i];
            if (p == 0.0) {
                if (counts[i] != 0) {
                    return false;
                }
                continue;
            }
            if (counts[i] == 0) {
                return false; // a possible outcome must stay possible
            }
            double q = (double) counts[i] / sides;
            double abs = Math.abs(q - p);
            if (!(abs <= tolAbs + 1e-9 && abs / p <= tolRel + 1e-9)) {
                return false;
            }
        }
        return true;
    }

    /** The d100 fallback: round each chance to whole percent; the largest band takes the leftover. */
    private static int[] percentile(double[] bands) {
        int[] c = new int[bands.length];
        int sum = 0;
        for (int i = 0; i < bands.length; i++) {
            c[i] = (int) Math.round(bands[i] * 100.0);
            sum += c[i];
        }
        int largest = indexOfMax(c);
        c[largest] += 100 - sum;
        // A possible outcome keeps at least one face, taken from the largest band.
        for (int i = 0; i < c.length; i++) {
            if (bands[i] > 0 && c[i] == 0) {
                c[i] = 1;
                c[indexOfMax(c)]--;
            }
        }
        return c;
    }

    private static int indexOfMax(int[] c) {
        int best = 0;
        for (int i = 1; i < c.length; i++) {
            if (c[i] > c[best]) {
                best = i;
            }
        }
        return best;
    }

    private static DiceChoice build(DiceSettings settings, List<Outcome> outcomes, double[] p, int die, int[] counts) {
        List<Band> bands = new ArrayList<>();
        int next = 1;
        double maxError = 0;
        for (int i = 0; i < counts.length; i++) {
            int faces = counts[i];
            int from = faces == 0 ? 0 : next;
            int to = faces == 0 ? 0 : next + faces - 1;
            next += faces;
            if (settings.direction() == Direction.LOW && faces > 0) {
                int mirroredFrom = die + 1 - to;
                int mirroredTo = die + 1 - from;
                from = mirroredFrom;
                to = mirroredTo;
            }
            Band band = new Band(outcomes.get(i).label(), p[i], faces, from, to, (double) faces / die);
            maxError = Math.max(maxError, band.errorPp());
            bands.add(band);
        }
        boolean twoD10 = die == 100 && !settings.hasD100();
        return new DiceChoice(die, true, twoD10, bands, maxError, explain(die, twoD10, bands, settings.direction()));
    }

    private static DiceChoice noRoll(List<Outcome> outcomes, double[] p) {
        List<Band> bands = new ArrayList<>();
        StringBuilder only = new StringBuilder();
        for (int i = 0; i < p.length; i++) {
            double real = p[i] > 0 ? 1.0 : 0.0;
            bands.add(new Band(outcomes.get(i).label(), p[i], 0, 0, 0, real));
            if (p[i] > 0) {
                only.append(outcomes.get(i).label());
            }
        }
        return new DiceChoice(0, false, false, bands, 0.0, "No roll needed: it is always " + only + ".");
    }

    private static String explain(int die, boolean twoD10, List<Band> bands, Direction direction) {
        StringBuilder sb = new StringBuilder("Roll a d").append(die);
        if (twoD10) {
            sb.append(" (two d10: tens, then units; 00 is 100)");
        }
        sb.append(direction == Direction.HIGH ? " (high is good): " : " (low is good): ");
        List<Band> shown = new ArrayList<>(bands);
        shown.removeIf(b -> b.faces() == 0);
        if (direction == Direction.LOW) {
            shown.sort(Comparator.comparingInt(Band::from));
        }
        for (int i = 0; i < shown.size(); i++) {
            Band b = shown.get(i);
            sb.append(i == 0 ? "" : " · ").append(b.range()).append(' ').append(b.label());
        }
        sb.append(". Real chances: ");
        boolean first = true;
        for (Band b : bands) {
            if (b.faces() == 0) {
                continue;
            }
            sb.append(first ? "" : " / ").append(DiceChoice.percent(b.realChance()));
            first = false;
        }
        sb.append(" (true: ");
        first = true;
        for (Band b : bands) {
            if (b.faces() == 0) {
                continue;
            }
            sb.append(first ? "" : " / ").append(DiceChoice.percent(b.trueChance()));
            first = false;
        }
        return sb.append(").").toString();
    }

    // ---------------------------------------------------------------- one success chance (B.1)

    /** The die for a yes/no chance (0 to 1) with the default settings. */
    public static Success success(double chance) {
        return success(DiceSettings.defaults(), chance);
    }

    /**
     * The die for a yes/no chance. A chance of 0 never succeeds and 1 always succeeds, and in both cases nobody rolls.
     *
     * @throws IllegalArgumentException if the chance is not between 0 and 1
     */
    public static Success success(DiceSettings settings, double chance) {
        if (!(chance >= 0.0 && chance <= 1.0)) {
            throw new IllegalArgumentException("A chance must be between 0 and 1, not " + chance);
        }
        DiceChoice choice = choose(settings, List.of(new Outcome("fail", 1.0 - chance), new Outcome("success", chance)));
        if (!choice.rolled()) {
            return new Success(chance >= 1.0 ? Kind.ALWAYS : Kind.NEVER, choice, chance, 0, 0);
        }
        Band success = choice.bands().get(1);
        return new Success(Kind.ROLL, choice, chance, success.from(), success.to());
    }
}
