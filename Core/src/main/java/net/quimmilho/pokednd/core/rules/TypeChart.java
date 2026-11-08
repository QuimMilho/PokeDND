/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The type effectiveness chart (chapter 7.7): what an attacking type does to a defending type, as a multiplier.
 *
 * <p>The multipliers are 0 (no effect), 0.5, 1 and 2. A defender with two types takes the product, so 4, 2, 1, 0.5, 0.25
 * and 0 can happen. The chart is data: {@link #standard()} is the official one, and {@link #with} makes an edited copy
 * for an admin who wants to change a pair. A chart never changes after it is made.
 */
public final class TypeChart {

    private static final int N = PokemonType.values().length;
    private static volatile TypeChart standard;

    private final double[][] grid;

    private TypeChart(double[][] grid) {
        this.grid = grid;
    }

    /** The official chart (Generation 6 and later), loaded from {@code rules/typechart.json}. */
    public static TypeChart standard() {
        TypeChart chart = standard;
        if (chart == null) {
            synchronized (TypeChart.class) {
                if (standard == null) {
                    standard = load();
                }
                chart = standard;
            }
        }
        return chart;
    }

    private static TypeChart load() {
        JsonNode root;
        try (InputStream in = TypeChart.class.getResourceAsStream("/rules/typechart.json")) {
            if (in == null) {
                throw new IllegalStateException("rules/typechart.json is missing from Core");
            }
            root = new ObjectMapper().readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read rules/typechart.json: " + e.getMessage(), e);
        }
        double[][] grid = new double[N][N];
        for (double[] row : grid) {
            Arrays.fill(row, 1.0);
        }
        for (PokemonType attack : PokemonType.values()) {
            JsonNode row = root.get(attack.id());
            if (row == null) {
                throw new IllegalStateException("rules/typechart.json has no row for " + attack.id());
            }
            fill(grid, attack, row.path("double"), 2.0);
            fill(grid, attack, row.path("half"), 0.5);
            fill(grid, attack, row.path("zero"), 0.0);
        }
        return new TypeChart(grid);
    }

    private static void fill(double[][] grid, PokemonType attack, JsonNode defenders, double multiplier) {
        for (JsonNode d : defenders) {
            PokemonType defender = PokemonType.parse(d.asText())
                    .orElseThrow(() -> new IllegalStateException("rules/typechart.json: unknown type " + d.asText()));
            grid[attack.ordinal()][defender.ordinal()] = multiplier;
        }
    }

    /** What {@code attack} does to a single defending type. */
    public double multiplier(PokemonType attack, PokemonType defender) {
        return grid[attack.ordinal()][defender.ordinal()];
    }

    /**
     * What {@code attack} does to a defender with one or two types: the product of the single multipliers.
     * Examples: Fire against Grass/Bug is 4; Ground against Flying (alone or in a pair) is 0.
     *
     * @param defenders one or two types; a type listed twice counts twice, so pass distinct types
     */
    public double effectiveness(PokemonType attack, List<PokemonType> defenders) {
        if (defenders.isEmpty() || defenders.size() > 2) {
            throw new IllegalArgumentException("A Pokémon has one or two types, not " + defenders.size());
        }
        double product = 1.0;
        for (PokemonType d : defenders) {
            product *= multiplier(attack, d);
        }
        return product;
    }

    /** Same as {@link #effectiveness(PokemonType, List)} for a single-type defender. */
    public double effectiveness(PokemonType attack, PokemonType defender) {
        return effectiveness(attack, List.of(defender));
    }

    /** Same, for a defender with two types. */
    public double effectiveness(PokemonType attack, PokemonType first, PokemonType second) {
        return effectiveness(attack, List.of(first, second));
    }

    /** The multiplier of every attacking type against this defender, for cards and prompts (weaknesses and resistances). */
    public Map<PokemonType, Double> against(List<PokemonType> defenders) {
        Map<PokemonType, Double> out = new EnumMap<>(PokemonType.class);
        for (PokemonType attack : PokemonType.values()) {
            out.put(attack, effectiveness(attack, defenders));
        }
        return out;
    }

    /** The attacking types that hit this defender for more than normal damage, strongest first, then in type order. */
    public List<PokemonType> weaknesses(List<PokemonType> defenders) {
        return pick(defenders, true);
    }

    /** The attacking types that hit this defender for less than normal damage (including none), weakest damage first. */
    public List<PokemonType> resistances(List<PokemonType> defenders) {
        return pick(defenders, false);
    }

    private List<PokemonType> pick(List<PokemonType> defenders, boolean weak) {
        Map<PokemonType, Double> all = against(defenders);
        List<PokemonType> out = new ArrayList<>();
        for (PokemonType t : PokemonType.values()) {
            double m = all.get(t);
            if (weak ? m > 1.0 : m < 1.0) {
                out.add(t);
            }
        }
        out.sort((a, b) -> weak ? Double.compare(all.get(b), all.get(a)) : Double.compare(all.get(a), all.get(b)));
        return out;
    }

    /** A copy of this chart where one pair has a different multiplier (an admin edit). */
    public TypeChart with(PokemonType attack, PokemonType defender, double multiplier) {
        if (!(multiplier >= 0.0) || Double.isInfinite(multiplier)) {
            throw new IllegalArgumentException("A type multiplier must be 0 or more, not " + multiplier);
        }
        double[][] copy = new double[N][];
        for (int i = 0; i < N; i++) {
            copy[i] = grid[i].clone();
        }
        copy[attack.ordinal()][defender.ordinal()] = multiplier;
        return new TypeChart(copy);
    }
}
