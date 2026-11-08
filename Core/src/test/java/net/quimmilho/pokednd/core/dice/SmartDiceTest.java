/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.dice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.quimmilho.pokednd.core.dice.DiceChoice.Band;
import net.quimmilho.pokednd.core.dice.DiceSettings.Direction;
import net.quimmilho.pokednd.core.dice.SmartDice.Kind;
import net.quimmilho.pokednd.core.dice.SmartDice.Success;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The smart-dice selector against the examples of chapter 6.5 and Appendix B, the reference implementation's own output
 * ({@code dice/reference.json}, made by running the JavaScript of Appendix B.3), and some properties.
 */
class SmartDiceTest {

    private static JsonNode reference;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = SmartDiceTest.class.getResourceAsStream("/dice/reference.json")) {
            reference = new ObjectMapper().readTree(in);
        }
    }

    // ---------------------------------------------------------------- the reference implementation

    @Test
    void everyChanceFrom1To99PercentGivesTheReferenceDieAndNeed() {
        assertEquals(99, reference.get("b1").size());
        for (JsonNode row : reference.get("b1")) {
            int pct = row.get("pct").asInt();
            Success s = SmartDice.success(pct / 100.0);
            assertEquals(Kind.ROLL, s.kind(), pct + " %");
            assertEquals(row.get("die").asInt(), s.die(), pct + " %");
            assertEquals(row.get("need").asInt(), s.need(), pct + " %: need");
            assertEquals(s.die(), s.successTo(), pct + " %: success runs to the top of the die");
            assertEquals(row.get("actual").asDouble(), s.realChance(), 1e-12, pct + " %: real chance");
        }
    }

    @Test
    void severalOutcomesOnOneRollGiveTheReferenceFaces() {
        var it = reference.get("b2").fields();
        int n = 0;
        while (it.hasNext()) {
            var e = it.next();
            double[] bands = toArray(e.getValue().get("bands"));
            DiceChoice c = SmartDice.choose(bands);
            assertEquals(e.getValue().get("die").asInt(), c.die(), e.getKey());
            assertEquals(toInts(e.getValue().get("counts")), faces(c), e.getKey());
            n++;
        }
        assertEquals(15, n);
    }

    @Test
    void aGridOfThreeBandRollsEqualsTheReference() {
        assertEquals(231, reference.get("grid").size());
        for (JsonNode row : reference.get("grid")) {
            double[] bands = toArray(row.get("bands"));
            DiceChoice c = SmartDice.choose(bands);
            String where = java.util.Arrays.toString(bands);
            if (!c.rolled()) {
                // One possible outcome: the reference would roll a d4 for nothing, the rule of chapter 6.4 step 5 rolls nothing.
                assertEquals(1, java.util.Arrays.stream(bands).filter(p -> p > 0).count(), where);
                continue;
            }
            assertEquals(row.get("die").asInt(), c.die(), where);
            assertEquals(toInts(row.get("counts")), faces(c), where);
        }
    }

    private static double[] toArray(JsonNode a) {
        double[] out = new double[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).asDouble();
        }
        return out;
    }

    private static List<Integer> toInts(JsonNode a) {
        List<Integer> out = new ArrayList<>();
        a.forEach(x -> out.add(x.asInt()));
        return out;
    }

    private static List<Integer> faces(DiceChoice c) {
        return c.bands().stream().map(Band::faces).toList();
    }

    // ---------------------------------------------------------------- the table printed in the specification

    /** Appendix B.1 of project_v2.md, read straight from the file: Core must reproduce it exactly. Skipped when the file is not there. */
    @Test
    void appendixB1AsPrintedInTheSpecificationIsReproducedExactly() throws IOException {
        java.nio.file.Path spec = java.nio.file.Path.of(System.getProperty("pokednd.spec", "../project_v2.md"));
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isRegularFile(spec), "project_v2.md not found");
        List<String> lines = java.nio.file.Files.readAllLines(spec);
        int start = lines.indexOf("#### B.1 One success chance → die");
        int end = lines.indexOf("#### B.2 Several outcomes on one roll");
        org.junit.jupiter.api.Assertions.assertTrue(start > 0 && end > start, "B.1 not found in the specification");
        java.util.regex.Pattern cell = java.util.regex.Pattern.compile("^(\\d+) %\\s*\\|\\s*d(\\d+)\\s*\\|\\s*(\\d+)(?:–(\\d+))?\\s*\\|\\s*([0-9.]+) %");
        int checked = 0;
        for (String line : lines.subList(start, end)) {
            if (!line.startsWith("| ") || !Character.isDigit(line.charAt(2))) {
                continue;
            }
            // Two groups of four cells sit side by side, separated by an empty cell.
            String[] halves = line.substring(2).split("\\|\\s*\\|", 2);
            for (String half : halves) {
                String text = half.strip();
                if (text.isEmpty() || !Character.isDigit(text.charAt(0))) {
                    continue;
                }
                java.util.regex.Matcher m = cell.matcher(text);
                org.junit.jupiter.api.Assertions.assertTrue(m.find(), "cannot read: " + text);
                int pct = Integer.parseInt(m.group(1));
                Success s = SmartDice.success(pct / 100.0);
                assertEquals(Integer.parseInt(m.group(2)), s.die(), pct + " %");
                assertEquals(Integer.parseInt(m.group(3)), s.successFrom(), pct + " % from");
                assertEquals(m.group(4) == null ? s.die() : Integer.parseInt(m.group(4)), s.successTo(), pct + " % to");
                assertEquals(m.group(5) + " %", DiceChoice.percent(s.realChance()), pct + " % real chance");
                checked++;
            }
        }
        assertEquals(99, checked, "all 99 rows of B.1");
    }

    // ---------------------------------------------------------------- chapter 6.5, one chance

    private static void check(double chance, int die, int from, double realChance) {
        Success s = SmartDice.success(chance);
        assertEquals(die, s.die(), chance + " die");
        assertEquals(from, s.successFrom(), chance + " from");
        assertEquals(die, s.successTo(), chance + " to");
        assertEquals(realChance, s.realChance(), 0.0005, chance + " real chance");
    }

    @Test
    void theWorkedExamplesOfChapter65ForASingleChance() {
        check(0.25, 4, 4, 0.25);        // paralysis: d4, roll a 4
        check(0.20, 10, 9, 0.20);       // frozen thaws: d10, 9-10
        check(0.10, 10, 10, 0.10);      // flamethrower burn: d10, 10
        check(0.30, 10, 8, 0.30);       // body slam paralysis: d10, 8-10
        check(0.33, 6, 5, 1.0 / 3);     // confused hits itself: d6, 5-6
        check(0.49, 4, 3, 0.50);        // flee, first try: d4, 3-4
        check(0.61, 8, 4, 0.625);       // flee, second try: d8, 4-8
        check(0.33, 6, 5, 1.0 / 3);     // catch Pidgey at full HP
        check(0.16, 6, 6, 1.0 / 6);     // catch Bulbasaur at low HP: d6, 6
        check(0.40, 8, 6, 0.375);       // catch Bulbasaur asleep at low HP: d8, 6-8 (Appendix B.1; 6.5 said d10 by mistake, fixed)
        check(0.88, 8, 2, 0.875);       // catch Pidgey nearly dead: d8, 2-8
        check(0.023, 100, 99, 0.02);    // legendary at 1 HP, Ultra Ball: d100, 99-100
        check(0.008, 100, 100, 0.01);   // legendary at full HP, Ultra Ball: d100, 100
    }

    @Test
    void theSuccessSentenceNamesTheDieTheFacesAndBothChances() {
        String text = SmartDice.success(0.30).choice().explanation();
        assertTrue(text.startsWith("Roll a d10"), text);
        assertTrue(text.contains("8–10 success"), text);
        assertTrue(text.contains("30 %"), text);
    }

    // ---------------------------------------------------------------- chapter 6.5 and B.2, several outcomes

    private static void bands(double[] probabilities, int die, String ranges) {
        DiceChoice c = SmartDice.choose(probabilities);
        assertEquals(die, c.die(), java.util.Arrays.toString(probabilities));
        assertEquals(ranges, c.bands().stream().map(Band::range).reduce((a, b) -> a + " · " + b).orElseThrow(),
                java.util.Arrays.toString(probabilities));
    }

    @Test
    void hitAndCritBands() {
        bands(new double[] {0.20, 0.75, 0.05}, 20, "1–4 · 5–19 · 20");         // 80 % accuracy, 5 % crit
        bands(new double[] {0.10, 0.80, 0.10}, 10, "1 · 2–9 · 10");            // 90 % accuracy, 10 % crit
        bands(new double[] {0.0, 0.95, 0.05}, 20, "- · 1–19 · 20");            // 100 % accuracy, 5 % crit
        bands(new double[] {0.95, 0.05}, 20, "1–19 · 20");
        bands(new double[] {0.05, 0.90, 0.05}, 20, "1 · 2–19 · 20");
        bands(new double[] {0.15, 0.80, 0.05}, 20, "1–3 · 4–19 · 20");
        bands(new double[] {0.25, 0.65, 0.10}, 20, "1–5 · 6–18 · 19–20");
        bands(new double[] {0.30, 0.65, 0.05}, 20, "1–6 · 7–19 · 20");
        bands(new double[] {0.40, 0.55, 0.05}, 20, "1–8 · 9–19 · 20");
        bands(new double[] {0.85, 0.15}, 6, "1–5 · 6");
        bands(new double[] {0.0, 0.75, 0.25}, 4, "- · 1–3 · 4");               // 100 % accuracy, crit stage +3
        bands(new double[] {0.25, 0.70, 0.05}, 20, "1–5 · 6–19 · 20");         // one accuracy stage down
    }

    @Test
    void sleepConfusionAndMultiHitLengths() {
        bands(new double[] {1.0 / 3, 1.0 / 3, 1.0 / 3}, 6, "1–2 · 3–4 · 5–6");
        bands(new double[] {0.25, 0.25, 0.25, 0.25}, 4, "1 · 2 · 3 · 4");
        DiceChoice multi = SmartDice.choose(0.35, 0.35, 0.15, 0.15);
        assertEquals(6, multi.die());
        assertEquals("1–2 · 3–4 · 5 · 6", multi.bands().stream().map(Band::range).reduce((a, b) -> a + " · " + b).orElseThrow());
        assertEquals(List.of("33.3 %", "33.3 %", "16.7 %", "16.7 %"),
                multi.bands().stream().map(b -> DiceChoice.percent(b.realChance())).toList(), "33/33/17/17 % as in 6.5");
    }

    @Test
    void theExplanationOfABandedRollIsReadable() {
        DiceChoice c = SmartDice.choose(DiceSettings.defaults(),
                List.of(new SmartDice.Outcome("miss", 0.20), new SmartDice.Outcome("hit", 0.75), new SmartDice.Outcome("crit", 0.05)));
        assertEquals("Roll a d20 (high is good): 1–4 miss · 5–19 hit · 20 crit. Real chances: 20 % / 75 % / 5 % (true: 20 % / 75 % / 5 %).",
                c.explanation());
        assertEquals(0.0, c.maxErrorPp(), 1e-9);
    }

    // ---------------------------------------------------------------- edge odds

    @Test
    void zeroAndHundredPercentNeedNoRoll() {
        Success never = SmartDice.success(0.0);
        assertEquals(Kind.NEVER, never.kind());
        assertFalse(never.choice().rolled());
        assertEquals(0, never.die());
        assertEquals(0.0, never.realChance());
        Success always = SmartDice.success(1.0);
        assertEquals(Kind.ALWAYS, always.kind());
        assertEquals(1.0, always.realChance());
        assertTrue(always.choice().explanation().contains("always"), always.choice().explanation());
        assertFalse(SmartDice.choose(0.0, 1.0, 0.0).rolled(), "a roll with a single possible outcome is not rolled");
    }

    @Test
    void onePercentAndNinetyNinePercentUseTheD100() {
        check(0.01, 100, 100, 0.01);
        check(0.99, 100, 2, 0.99);
        check(0.333, 6, 5, 1.0 / 3);
    }

    @Test
    void aChanceBelowHalfAPercentKeepsOneFaceSoItCanStillHappen() {
        Success s = SmartDice.success(0.002);
        assertEquals(100, s.die());
        assertEquals(100, s.successFrom(), "one face of the d100, so 1 % instead of impossible");
        Success high = SmartDice.success(0.998);
        assertEquals(2, high.successFrom());
        DiceChoice c = SmartDice.choose(0.997, 0.001, 0.001, 0.001);
        assertEquals(100, c.die());
        assertTrue(c.bands().stream().allMatch(b -> b.faces() >= 1));
        assertEquals(100, c.bands().stream().mapToInt(Band::faces).sum());
    }

    // ---------------------------------------------------------------- settings

    @Test
    void theTableMayOwnFewerDice() {
        DiceSettings onlyD20 = DiceSettings.defaults().withAvailable(List.of(20, 100));
        Success s = SmartDice.success(onlyD20, 0.25);
        assertEquals(20, s.die());
        assertEquals(16, s.successFrom());
        assertEquals(0.25, s.realChance(), 1e-12);
        // Without any small die and without a d100, the d100 is rolled as two d10.
        DiceSettings noD100 = DiceSettings.defaults().withAvailable(List.of(6, 10));
        DiceChoice rare = SmartDice.success(noD100, 0.03).choice();
        assertEquals(100, rare.die());
        assertTrue(rare.percentileAsTwoD10());
        assertTrue(rare.explanation().contains("two d10"), rare.explanation());
        assertFalse(SmartDice.success(0.03).choice().percentileAsTwoD10());
    }

    @Test
    void tighterToleranceMovesMoreRollsToTheD100AndLooserOnesToSmallDice() {
        // 45 % is a d20 (9 faces) by default; with a 0.1 point tolerance only a d20 or d100 fits, with 10 points a d4 does.
        assertEquals(20, SmartDice.success(0.45).die());
        assertEquals(20, SmartDice.success(DiceSettings.defaults().withTolerance(0.1, 0.1), 0.45).die());
        assertEquals(4, SmartDice.success(DiceSettings.defaults().withTolerance(10, 100), 0.45).die());
        // 33 % is a d6 by default (off by 0.33 points); a tolerance of 0.1 points cannot use it and falls to the d100.
        assertEquals(6, SmartDice.success(0.33).die());
        assertEquals(100, SmartDice.success(DiceSettings.defaults().withTolerance(0.1, 100), 0.33).die());
        // The d100 threshold: 4.5 % is a d100 by default, but a d20 (one face, 5 %) if the threshold is lowered to 1 %.
        assertEquals(100, SmartDice.success(0.045).die());
        DiceSettings lowThreshold = new DiceSettings(DiceSettings.ALL_DICE, 2.5, 15, 1, Direction.HIGH);
        assertEquals(20, SmartDice.success(lowThreshold, 0.045).die());
    }

    @Test
    void rollLowToSucceedMirrorsTheFaces() {
        DiceSettings low = DiceSettings.defaults().withDirection(Direction.LOW);
        Success s = SmartDice.success(low, 0.25);
        assertEquals(4, s.die());
        assertEquals(1, s.successFrom());
        assertEquals(1, s.successTo(), "the best outcome has the lowest face");
        DiceChoice c = SmartDice.choose(low, 0.20, 0.75, 0.05);
        assertEquals(List.of("17–20", "2–16", "1"), c.bands().stream().map(Band::range).toList());
        assertTrue(c.explanation().contains("low is good"), c.explanation());
    }

    @Test
    void badSettingsAndBadInputAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> DiceSettings.defaults().withAvailable(List.of()));
        assertThrows(IllegalArgumentException.class, () -> DiceSettings.defaults().withAvailable(List.of(7)));
        assertThrows(IllegalArgumentException.class, () -> DiceSettings.defaults().withTolerance(-1, 15));
        assertThrows(IllegalArgumentException.class, () -> SmartDice.success(-0.1));
        assertThrows(IllegalArgumentException.class, () -> SmartDice.success(1.1));
        assertThrows(IllegalArgumentException.class, () -> SmartDice.choose(0.5, 0.4), "does not add up to 1");
        assertThrows(IllegalArgumentException.class, () -> SmartDice.choose(1.2, -0.2));
        assertThrows(IllegalArgumentException.class, () -> SmartDice.choose());
    }

    // ---------------------------------------------------------------- properties

    /** Chances in steps of 0.5 %, 2 to 4 outcomes, adding up to exactly 100 %. */
    private static double[] randomBands(Random random) {
        int n = 2 + random.nextInt(3);
        int[] halfPercent = new int[n];
        int left = 200;
        for (int i = 0; i < n - 1; i++) {
            halfPercent[i] = random.nextInt(Math.max(1, left - (n - 1 - i)) + 1);
            left -= halfPercent[i];
        }
        halfPercent[n - 1] = left;
        double[] p = new double[n];
        for (int i = 0; i < n; i++) {
            p[i] = halfPercent[i] / 200.0;
        }
        return p;
    }

    @Test
    void propertyBandsAddUpToTheDieAndNoPossibleOutcomeHasZeroFaces() {
        Random random = new Random(20261108L);
        for (int i = 0; i < 20_000; i++) {
            double[] p = randomBands(random);
            DiceChoice c = SmartDice.choose(p);
            String where = java.util.Arrays.toString(p) + " -> " + c.explanation();
            if (!c.rolled()) {
                continue;
            }
            assertEquals(c.die(), c.bands().stream().mapToInt(Band::faces).sum(), where);
            for (int b = 0; b < p.length; b++) {
                if (p[b] > 0) {
                    assertTrue(c.bands().get(b).faces() >= 1, "a possible outcome has no face: " + where);
                } else {
                    assertEquals(0, c.bands().get(b).faces(), "an impossible outcome has a face: " + where);
                }
            }
            // faces are consecutive from 1 and do not overlap
            int next = 1;
            for (Band b : c.bands()) {
                if (b.faces() > 0) {
                    assertEquals(next, b.from(), where);
                    assertEquals(next + b.faces() - 1, b.to(), where);
                    next += b.faces();
                }
            }
            assertEquals(c.die() + 1, next, where);
        }
    }

    @Test
    void propertyTheErrorNeverExceedsTheTolerances() {
        Random random = new Random(7L);
        DiceSettings settings = DiceSettings.defaults();
        for (int i = 0; i < 20_000; i++) {
            double[] p = randomBands(random);
            DiceChoice c = SmartDice.choose(settings, p);
            if (!c.rolled()) {
                continue;
            }
            String where = java.util.Arrays.toString(p) + " -> " + c.explanation();
            for (int b = 0; b < p.length; b++) {
                Band band = c.bands().get(b);
                if (c.die() != 100) {
                    assertTrue(band.errorPp() <= settings.tolerancePp() + 1e-6, "absolute error too big: " + where);
                    assertTrue(p[b] == 0 || band.errorPp() / 100.0 / p[b] <= settings.toleranceRelativePct() / 100.0 + 1e-6,
                            "relative error too big: " + where);
                } else {
                    assertTrue(band.errorPp() <= settings.tolerancePp() + 1e-6, "d100 error too big: " + where);
                }
            }
            assertEquals(c.bands().stream().mapToDouble(Band::errorPp).max().orElse(0), c.maxErrorPp(), 1e-9);
        }
    }

    @Test
    void propertyEverySingleChanceIsWithinToleranceAndSumsToTheDie() {
        for (int thousandth = 1; thousandth <= 999; thousandth++) {
            double chance = thousandth / 1000.0;
            Success s = SmartDice.success(chance);
            assertEquals(Kind.ROLL, s.kind());
            Band success = s.choice().bands().get(1);
            Band fail = s.choice().bands().get(0);
            assertEquals(s.die(), success.faces() + fail.faces(), chance + "");
            assertTrue(success.faces() >= 1 && fail.faces() >= 1, chance + "");
            assertTrue(s.choice().maxErrorPp() <= 2.5 + 1e-6, chance + ": " + s.choice().explanation());
        }
    }

    @Test
    void theSameInputAlwaysGivesTheSameResult() {
        assertEquals(SmartDice.choose(0.1, 0.8, 0.1), SmartDice.choose(0.1, 0.8, 0.1));
        assertEquals(SmartDice.success(0.37), SmartDice.success(0.37));
    }
}
