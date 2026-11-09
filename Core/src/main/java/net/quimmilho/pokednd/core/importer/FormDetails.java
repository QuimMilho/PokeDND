/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import net.quimmilho.pokednd.core.importer.SpeciesMerger.Merged;
import net.quimmilho.pokednd.core.importer.SpeciesMerger.MergedSpecies;
import net.quimmilho.pokednd.core.rules.PokemonType;
import net.quimmilho.pokednd.core.rules.TypeChart;

/**
 * What differs between the forms of one species: types, abilities and, from those types, weaknesses and resistances (written to
 * {@code forms.json}). {@code species.json} and {@code derived.json} describe the species as a whole, which is right for the base
 * form; a card for Alolan Marowak (Fire/Ghost) needs its own values.
 *
 * <p>Only forms that have a resolver file (the roster) are listed. A form that the species file does not describe takes the
 * species' own types and abilities.
 *
 * @param bySpecies species id, then form id
 */
public record FormDetails(SortedMap<String, SortedMap<String, Detail>> bySpecies) {

    /**
     * One form.
     *
     * @param types         one or two type ids, primary first
     * @param abilities     the normal abilities
     * @param hiddenAbility the hidden ability, or null
     * @param weaknesses    attacking types that do more than normal damage, with the multiplier
     * @param resistances   attacking types that do less (but not zero) damage
     * @param immunities    attacking types that do no damage
     */
    public record Detail(List<String> types, List<String> abilities, String hiddenAbility, Map<String, Double> weaknesses,
                         Map<String, Double> resistances, List<String> immunities) {
    }

    public static FormDetails build(Raw raw, Roster roster, Merged merged, TypeChart chart) {
        Map<String, MergedSpecies> byId = new TreeMap<>();
        merged.species().forEach(s -> byId.put(s.id(), s));
        SortedMap<String, SortedMap<String, Detail>> out = new TreeMap<>();
        for (Roster.Entry entry : roster.species().values()) {
            MergedSpecies species = byId.get(entry.id());
            if (species == null) {
                continue;
            }
            SortedMap<String, Detail> forms = new TreeMap<>();
            for (Roster.Form form : entry.forms()) {
                forms.put(form.id(), detail(raw, species, form, chart));
            }
            out.put(entry.id(), forms);
        }
        return new FormDetails(java.util.Collections.unmodifiableSortedMap(out));
    }

    private static Detail detail(Raw raw, MergedSpecies species, Roster.Form form, TypeChart chart) {
        List<PokemonType> types = new ArrayList<>(Signature.formTypes(raw, species, form));
        List<String> abilities = new ArrayList<>(species.abilities());
        String hidden = species.hiddenAbility().orElse(null);

        JsonNode file = formFile(raw, species, form);
        if (file != null && file.path("abilities").isArray() && !file.path("abilities").isEmpty()) {
            abilities.clear();
            hidden = null;
            for (JsonNode a : file.path("abilities")) {
                String text = a.asText();
                if (text.startsWith("h:")) {
                    hidden = Forms.squash(text.substring(2));
                } else {
                    abilities.add(Forms.squash(text));
                }
            }
        }

        Map<PokemonType, Double> against = chart.against(types);
        Map<String, Double> weak = new LinkedHashMap<>();
        chart.weaknesses(types).forEach(t -> weak.put(t.id(), against.get(t)));
        Map<String, Double> resist = new LinkedHashMap<>();
        List<String> immune = new ArrayList<>();
        chart.resistances(types).forEach(t -> {
            if (against.get(t) == 0.0) {
                immune.add(t.id());
            } else {
                resist.put(t.id(), against.get(t));
            }
        });
        return new Detail(types.stream().map(PokemonType::id).toList(), abilities, hidden, weak, resist, immune);
    }

    /** The entry of the species file whose aspects are the form's, if there is one. */
    private static JsonNode formFile(Raw raw, MergedSpecies species, Roster.Form form) {
        Raw.SpeciesFile file = raw.species().get(species.dex());
        if (file == null || form.aspects().isEmpty()) {
            return null;
        }
        for (JsonNode f : file.json().path("forms")) {
            List<String> aspects = new ArrayList<>();
            f.path("aspects").forEach(a -> aspects.add(a.asText()));
            if (new java.util.TreeSet<>(aspects).equals(new java.util.TreeSet<>(form.aspects()))) {
                return f;
            }
        }
        return null;
    }

    /** The details of one form, if the roster has it. */
    public Optional<Detail> get(String species, String form) {
        return Optional.ofNullable(bySpecies.get(species)).map(m -> m.get(form));
    }
}
