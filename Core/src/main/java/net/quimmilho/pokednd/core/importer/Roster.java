/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The playable species and forms: the ones whose assets are complete, as written by {@code tools/check-assets.mjs} to
 * {@code build/roster.json} (chapter 19.3, step 5). The importer never decides this itself; it only reads the file.
 *
 * @param cobblemon the Cobblemon version the roster was made from
 * @param species   playable species, by National Dex number
 */
public record Roster(String cobblemon, SortedMap<Integer, Entry> species) {

    /**
     * One playable species.
     *
     * @param dex   National Dex number
     * @param name  display name
     * @param id    Cobblemon species id (the species JSON file name, for example {@code mrmime})
     * @param forms the forms that have a resolver file; the first without aspects is the base form
     */
    public record Entry(int dex, String name, String id, List<Form> forms) {
        public Entry {
            forms = List.copyOf(forms);
        }

        /** The base form: the first one without aspects. */
        public Optional<Form> base() {
            return forms.stream().filter(f -> f.aspects().isEmpty()).findFirst();
        }
    }

    /**
     * One form with a resolver file.
     *
     * @param id      the resolver file's name without number and extension (for example {@code marowak_alolan})
     * @param aspects the aspects of the file's first entry (for example {@code alolan})
     */
    public record Form(String id, List<String> aspects) {
        public Form {
            aspects = List.copyOf(aspects);
        }
    }

    /** Reads {@code build/roster.json}. */
    public static Roster read(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new ImportException("Missing roster " + file + " (make it with: node tools/check-assets.mjs)");
        }
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(Files.readString(file));
        } catch (IOException e) {
            throw new ImportException("Cannot read " + file + ": " + e.getMessage(), e);
        }
        TreeMap<Integer, Entry> species = new TreeMap<>();
        for (JsonNode s : root.path("species")) {
            List<Form> forms = new ArrayList<>();
            for (JsonNode f : s.path("forms")) {
                List<String> aspects = new ArrayList<>();
                f.path("aspects").forEach(a -> aspects.add(a.asText()));
                forms.add(new Form(f.path("id").asText(), aspects));
            }
            Entry e = new Entry(s.path("dex").asInt(), s.path("name").asText(), s.path("species").asText(), forms);
            if (species.put(e.dex(), e) != null) {
                throw new ImportException(file + ": Dex " + e.dex() + " appears twice");
            }
        }
        if (root.has("speciesCount") && root.get("speciesCount").asInt() != species.size()) {
            throw new ImportException(file + ": says " + root.get("speciesCount").asInt() + " species but lists " + species.size());
        }
        return new Roster(root.path("cobblemon").asText(""), Collections.unmodifiableSortedMap(species));
    }

    /** Whether the species with this Dex number is playable. */
    public boolean contains(int dex) {
        return species.containsKey(dex);
    }
}
