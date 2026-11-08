/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import net.quimmilho.pokednd.core.importer.Evolutions.Rule;
import net.quimmilho.pokednd.core.importer.SpeciesMerger.MergedSpecies;
import net.quimmilho.pokednd.core.rules.PokemonType;
import net.quimmilho.pokednd.core.rules.TypeChart;

/**
 * Values worked out from the other data, for cards and prompts (chapter 19.3, step 9): the stage badge, and what each type
 * does to the species, and the four signature moves of each card (phase 20, added with {@link #withSignature}).
 *
 * @param species by species id
 */
public record Derived(SortedMap<String, SpeciesDerived> species) {

    /** The badge on a card. */
    public enum Stage {
        BASIC, STAGE_1, STAGE_2
    }

    /**
     * Derived values of one species.
     *
     * @param stage        Basic, Stage 1 or Stage 2
     * @param evolvesFrom  species ids it evolves from (usually one or none)
     * @param evolvesInto  species ids it can evolve into
     * @param weaknesses   attacking types that do more than normal damage, with the multiplier (sorted by name in the file)
     * @param resistances  attacking types that do less than normal damage (not zero), with the multiplier
     * @param immunities   attacking types that do no damage
     * @param signature    the four moves printed on the card of each playable form (form id to move ids); empty for species that are
     *                     not in the roster
     */
    public record SpeciesDerived(Stage stage, List<String> evolvesFrom, List<String> evolvesInto,
                                 Map<String, Double> weaknesses, Map<String, Double> resistances, List<String> immunities,
                                 SortedMap<String, List<String>> signature) {
    }

    /** A copy with the signature moves of every card added. */
    public Derived withSignature(Signature signature) {
        SortedMap<String, List<String>> none = new TreeMap<>();
        Map<String, SortedMap<String, List<String>>> moves = signature.movesByForm();
        TreeMap<String, SpeciesDerived> out = new TreeMap<>();
        species.forEach((id, d) -> out.put(id, new SpeciesDerived(d.stage(), d.evolvesFrom(), d.evolvesInto(), d.weaknesses(),
                d.resistances(), d.immunities(), moves.getOrDefault(id, none))));
        return new Derived(java.util.Collections.unmodifiableSortedMap(out));
    }

    /** Works out the values for every merged species. */
    public static Derived build(List<MergedSpecies> species, List<Rule> rules, TypeChart chart) {
        Map<Integer, String> idByDex = new HashMap<>();
        species.forEach(s -> idByDex.put(s.dex(), s.id()));
        Map<Integer, List<Integer>> before = new HashMap<>();
        Map<Integer, List<Integer>> after = new HashMap<>();
        for (Rule r : rules) {
            if (r.toDex() == 0 || r.toDex() == r.fromDex()) {
                continue;
            }
            addOnce(before.computeIfAbsent(r.toDex(), k -> new ArrayList<>()), r.fromDex());
            addOnce(after.computeIfAbsent(r.fromDex(), k -> new ArrayList<>()), r.toDex());
        }
        Map<Integer, Integer> depth = new HashMap<>();
        TreeMap<String, SpeciesDerived> out = new TreeMap<>();
        for (MergedSpecies s : species) {
            int d = depth(s.dex(), before, depth, new java.util.HashSet<>());
            Stage stage = d <= 0 ? Stage.BASIC : d == 1 ? Stage.STAGE_1 : Stage.STAGE_2;

            List<PokemonType> types = new ArrayList<>();
            for (String t : s.types()) {
                types.add(PokemonType.parse(t).orElseThrow(() -> new ImportException(s.id() + ": unknown type " + t)));
            }
            Map<PokemonType, Double> against = chart.against(types);
            Map<String, Double> weak = new java.util.LinkedHashMap<>();
            chart.weaknesses(types).forEach(t -> weak.put(t.id(), against.get(t)));
            Map<String, Double> resist = new java.util.LinkedHashMap<>();
            List<String> immune = new ArrayList<>();
            chart.resistances(types).forEach(t -> {
                if (against.get(t) == 0.0) {
                    immune.add(t.id());
                } else {
                    resist.put(t.id(), against.get(t));
                }
            });
            out.put(s.id(), new SpeciesDerived(stage, ids(before.get(s.dex()), idByDex), ids(after.get(s.dex()), idByDex), weak, resist,
                    immune, new TreeMap<>()));
        }
        return new Derived(java.util.Collections.unmodifiableSortedMap(out));
    }

    private static void addOnce(List<Integer> list, int value) {
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    private static List<String> ids(List<Integer> dexes, Map<Integer, String> idByDex) {
        if (dexes == null) {
            return List.of();
        }
        return dexes.stream().map(d -> idByDex.getOrDefault(d, "dex" + d)).sorted().toList();
    }

    /** How many evolutions come before this species (the longest chain). A loop in the data counts as the end of the chain. */
    private static int depth(int dex, Map<Integer, List<Integer>> before, Map<Integer, Integer> memo, java.util.Set<Integer> path) {
        Integer known = memo.get(dex);
        if (known != null) {
            return known;
        }
        List<Integer> parents = before.get(dex);
        int result = 0;
        if (parents != null && path.add(dex)) {
            for (int p : parents) {
                result = Math.max(result, 1 + depth(p, before, memo, path));
            }
            path.remove(dex);
        }
        memo.put(dex, result);
        return result;
    }
}
