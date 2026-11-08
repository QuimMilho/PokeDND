/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.rules;

import static net.quimmilho.pokednd.core.rules.PokemonType.BUG;
import static net.quimmilho.pokednd.core.rules.PokemonType.DRAGON;
import static net.quimmilho.pokednd.core.rules.PokemonType.FAIRY;
import static net.quimmilho.pokednd.core.rules.PokemonType.FIRE;
import static net.quimmilho.pokednd.core.rules.PokemonType.FLYING;
import static net.quimmilho.pokednd.core.rules.PokemonType.GHOST;
import static net.quimmilho.pokednd.core.rules.PokemonType.GRASS;
import static net.quimmilho.pokednd.core.rules.PokemonType.GROUND;
import static net.quimmilho.pokednd.core.rules.PokemonType.ICE;
import static net.quimmilho.pokednd.core.rules.PokemonType.NORMAL;
import static net.quimmilho.pokednd.core.rules.PokemonType.POISON;
import static net.quimmilho.pokednd.core.rules.PokemonType.ROCK;
import static net.quimmilho.pokednd.core.rules.PokemonType.STEEL;
import static net.quimmilho.pokednd.core.rules.PokemonType.WATER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The type chart against a table written separately: a grid typed in row by row from the games (attacker down the side,
 * defender across the top), not copied from the list in the specification or from the Showdown data.
 */
class TypeChartTest {

    /** Defender order across the top, which is also the order of the rows. */
    private static final PokemonType[] ORDER = {NORMAL, FIRE, WATER, PokemonType.ELECTRIC, GRASS, ICE, PokemonType.FIGHTING, POISON,
        GROUND, FLYING, PokemonType.PSYCHIC, BUG, ROCK, GHOST, DRAGON, PokemonType.DARK, STEEL, FAIRY};

    /**
     * One row per attacking type. {@code -} = normal damage, {@code 2} = super effective, {@code h} = not very effective,
     * {@code 0} = no effect. Columns: Nor Fir Wat Ele Gra Ice Fig Poi Gro Fly Psy Bug Roc Gho Dra Dar Ste Fai.
     */
    private static final String[] GRID = {
        /* Normal   */ "- - - - - - - - - - - - h 0 - - h -",
        /* Fire     */ "- h h - 2 2 - - - - - 2 h - h - 2 -",
        /* Water    */ "- 2 h - h - - - 2 - - - 2 - h - - -",
        /* Electric */ "- - 2 h h - - - 0 2 - - - - h - - -",
        /* Grass    */ "- h 2 - h - - h 2 h - h 2 - h - h -",
        /* Ice      */ "- h h - 2 h - - 2 2 - - - - 2 - h -",
        /* Fighting */ "2 - - - - 2 - h - h h h 2 0 - 2 2 h",
        /* Poison   */ "- - - - 2 - - h h - - - h h - - 0 2",
        /* Ground   */ "- 2 - 2 h - - 2 - 0 - h 2 - - - 2 -",
        /* Flying   */ "- - - h 2 - 2 - - - - 2 h - - - h -",
        /* Psychic  */ "- - - - - - 2 2 - - h - - - - 0 h -",
        /* Bug      */ "- h - - 2 - h h - h 2 - - h - 2 h h",
        /* Rock     */ "- 2 - - - 2 h - h 2 - 2 - - - - h -",
        /* Ghost    */ "0 - - - - - - - - - 2 - - 2 - h - -",
        /* Dragon   */ "- - - - - - - - - - - - - - 2 - h 0",
        /* Dark     */ "- - - - - - h - - - 2 - - 2 - h - h",
        /* Steel    */ "- h h h - 2 - - - - - - 2 - - - h 2",
        /* Fairy    */ "- h - - - - 2 h - - - - - - 2 2 h -",
    };

    private static double symbol(String s) {
        return switch (s) {
            case "-" -> 1.0;
            case "2" -> 2.0;
            case "h" -> 0.5;
            case "0" -> 0.0;
            default -> throw new AssertionError("bad symbol " + s);
        };
    }

    @Test
    void theReferenceGridIsComplete() {
        assertEquals(18, GRID.length);
        assertEquals(18, ORDER.length);
        for (String row : GRID) {
            assertEquals(18, row.split(" ").length, row);
        }
    }

    @Test
    void allEighteenTimesEighteenPairsEqualTheIndependentTable() {
        TypeChart chart = TypeChart.standard();
        for (int a = 0; a < 18; a++) {
            String[] row = GRID[a].split(" ");
            for (int d = 0; d < 18; d++) {
                assertEquals(symbol(row[d]), chart.multiplier(ORDER[a], ORDER[d]), ORDER[a] + " against " + ORDER[d]);
            }
        }
    }

    @Test
    void dualTypesMultiply() {
        TypeChart chart = TypeChart.standard();
        assertEquals(4.0, chart.effectiveness(FIRE, GRASS, BUG));
        assertEquals(0.0, chart.effectiveness(GROUND, FLYING));
        assertEquals(0.0, chart.effectiveness(GROUND, FLYING, GRASS), "an immunity wins over a weakness");
        assertEquals(0.25, chart.effectiveness(GRASS, FIRE, DRAGON));
        assertEquals(1.0, chart.effectiveness(WATER, FIRE, GRASS), "a weakness and a resistance cancel");
        assertEquals(1.0, chart.effectiveness(ROCK, FIRE, STEEL), "x2 against Fire and x0.5 against Steel");
        assertEquals(4.0, chart.effectiveness(ICE, GRASS, GROUND));
        assertEquals(1.0, chart.effectiveness(PokemonType.NORMAL, List.of(FAIRY)));
    }

    @Test
    void aPokemonHasOneOrTwoTypes() {
        TypeChart chart = TypeChart.standard();
        assertThrows(IllegalArgumentException.class, () -> chart.effectiveness(FIRE, List.of()));
        assertThrows(IllegalArgumentException.class, () -> chart.effectiveness(FIRE, List.of(GRASS, BUG, ROCK)));
    }

    @Test
    void everyMultiplierIsZeroHalfOneOrTwo() {
        TypeChart chart = TypeChart.standard();
        for (PokemonType a : PokemonType.values()) {
            for (PokemonType d : PokemonType.values()) {
                double m = chart.multiplier(a, d);
                assertTrue(m == 0.0 || m == 0.5 || m == 1.0 || m == 2.0, a + " against " + d + " = " + m);
            }
        }
    }

    @Test
    void weaknessesAndResistancesForCards() {
        TypeChart chart = TypeChart.standard();
        // Bulbasaur: Grass/Poison is weak to Fire, Ice, Flying, Psychic.
        assertEquals(List.of(FIRE, ICE, FLYING, PokemonType.PSYCHIC), chart.weaknesses(List.of(GRASS, POISON)));
        // Gengar: Ghost/Poison takes 0 from Normal and Fighting, and is weak to Ground, Psychic, Ghost, Dark.
        assertEquals(List.of(PokemonType.NORMAL, PokemonType.FIGHTING), chart.resistances(List.of(GHOST, POISON)).subList(0, 2));
        assertEquals(List.of(GROUND, PokemonType.PSYCHIC, GHOST, PokemonType.DARK), chart.weaknesses(List.of(GHOST, POISON)));
        // Scizor: Bug/Steel has a 4x weakness to Fire, listed first.
        assertEquals(FIRE, chart.weaknesses(List.of(BUG, STEEL)).get(0));
        assertEquals(4.0, chart.against(List.of(BUG, STEEL)).get(FIRE));
    }

    @Test
    void anAdminCanEditACopyAndTheOfficialChartStaysTheSame() {
        TypeChart chart = TypeChart.standard();
        TypeChart edited = chart.with(FIRE, WATER, 2.0);
        assertEquals(2.0, edited.multiplier(FIRE, WATER));
        assertEquals(0.5, chart.multiplier(FIRE, WATER));
        assertThrows(IllegalArgumentException.class, () -> chart.with(FIRE, WATER, -1.0));
    }

    @Test
    void typesParseFromTheNamesInTheData() {
        assertEquals(FIRE, PokemonType.parse("Fire").orElseThrow());
        assertEquals(FAIRY, PokemonType.parse("fairy").orElseThrow());
        assertTrue(PokemonType.parse("shadow").isEmpty());
        assertTrue(PokemonType.parse(null).isEmpty());
        assertEquals("steel", STEEL.id());
    }

    /** A cross-check against the Showdown snapshot as well (a second opinion, not the independent table). */
    @Test
    void agreesWithTheShowdownSnapshot() throws IOException {
        JsonNode sd;
        try (InputStream in = TypeChartTest.class.getResourceAsStream("/showdown/typechart.json")) {
            sd = new ObjectMapper().readTree(in);
        }
        // In Showdown: 0 normal, 1 weak (x2), 2 resists (x0.5), 3 immune.
        Map<Integer, Double> meaning = Map.of(0, 1.0, 1, 2.0, 2, 0.5, 3, 0.0);
        TypeChart chart = TypeChart.standard();
        for (PokemonType defender : PokemonType.values()) {
            JsonNode taken = sd.get(defender.id()).get("damageTaken");
            for (PokemonType attack : PokemonType.values()) {
                String key = attack.name().charAt(0) + attack.id().substring(1);
                assertEquals(meaning.get(taken.get(key).asInt()), chart.multiplier(attack, defender), attack + " against " + defender);
            }
        }
    }
}
