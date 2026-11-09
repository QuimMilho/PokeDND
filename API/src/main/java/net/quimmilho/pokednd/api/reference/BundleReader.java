/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.reference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.EntryKind;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.LoadedBundle;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.NamedRecord;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.ReferenceBundle;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.SpeciesRecord;
import net.quimmilho.pokednd.api.error.ApiException;
import net.quimmilho.pokednd.core.importer.Bundle;

/** Reads a data bundle folder (chapter 19.2) into records. It touches no database, so tests can use it too. */
public final class BundleReader {

    private final ObjectMapper json;

    public BundleReader(ObjectMapper json) {
        this.json = json;
    }

    /** The hash that identifies a bundle: the {@code outputHash} of its manifest. */
    public String hash(Path dir) {
        return manifest(dir).path("outputHash").asText("");
    }

    private JsonNode manifest(Path dir) {
        JsonNode manifest = read(dir, "MANIFEST.json");
        if (manifest.path("outputHash").asText("").isEmpty()) {
            throw invalid("MANIFEST.json has no outputHash");
        }
        int format = manifest.path("bundleFormat").asInt(-1);
        if (format != Bundle.FORMAT) {
            throw invalid("the bundle has format " + format + " but this API reads format " + Bundle.FORMAT);
        }
        return manifest;
    }

    /** Reads everything.
     *
     * @throws ApiException 500 BUNDLE_INVALID if the folder is not a usable bundle
     */
    public ReferenceBundle read(Path dir) {
        JsonNode manifest = manifest(dir);
        String hash = manifest.path("outputHash").asText();
        int format = manifest.path("bundleFormat").asInt();
        Map<String, JsonNode> derived = new LinkedHashMap<>();
        read(dir, "derived.json").path("species").fields().forEachRemaining(e -> derived.put(e.getKey(), e.getValue()));
        JsonNode learnsets = read(dir, "learnsets.json").path("learnsets");
        Map<String, ArrayNode> evolutions = new LinkedHashMap<>();
        for (JsonNode rule : read(dir, "evolutions.json").path("rules")) {
            evolutions.computeIfAbsent(rule.path("from").asText(), k -> json.createArrayNode()).add(rule);
        }

        // forms.json (types, abilities and weaknesses of each form) is merged into the forms of each species
        JsonNode formDetails = Files.isRegularFile(dir.resolve("forms.json")) ? read(dir, "forms.json").path("forms") : json.createObjectNode();

        List<SpeciesRecord> species = new ArrayList<>();
        for (JsonNode s : read(dir, "species.json").path("species")) {
            String id = s.path("id").asText();
            mergeFormDetails(s, formDetails.path(id));
            int dex = s.path("dex").asInt();
            List<String> types = new ArrayList<>();
            s.path("types").forEach(t -> types.add(t.asText().toLowerCase(java.util.Locale.ROOT)));
            species.add(new SpeciesRecord(id, dex, s.path("name").asText(), Generations.of(dex), types, s.path("playable").asBoolean(false), s,
                    derived.get(id), evolutions.get(id), learnsets.get(id)));
        }
        if (species.isEmpty()) {
            throw invalid("species.json has no species");
        }

        List<NamedRecord> entries = new ArrayList<>();
        for (JsonNode m : read(dir, "moves.json").path("moves")) {
            entries.add(new NamedRecord(EntryKind.MOVE, m.path("id").asText(), m.path("name").asText(), lower(m.path("type").asText(null)), m));
        }
        for (JsonNode i : read(dir, "items.json").path("items")) {
            entries.add(new NamedRecord(EntryKind.ITEM, i.path("id").asText(), i.path("name").asText(), lower(i.path("category").asText(null)), i));
        }
        for (JsonNode a : read(dir, "abilities.json").path("abilities")) {
            entries.add(new NamedRecord(EntryKind.ABILITY, a.path("id").asText(), a.path("name").asText(), null, a));
        }

        Map<String, JsonNode> documents = new LinkedHashMap<>();
        documents.put("typechart", read(dir, "typechart.json"));
        documents.put("natures", read(dir, "natures.json"));
        documents.put("roster", read(dir, "roster.json"));
        documents.put("manifest", manifest);

        return new ReferenceBundle(new LoadedBundle(hash, format, manifest.path("cobblemon").asText(null), Instant.now()), species, entries, documents);
    }

    /** Adds the details of each form (types, abilities, weaknesses…) to the form objects of the species. */
    private static void mergeFormDetails(JsonNode species, JsonNode details) {
        for (JsonNode form : species.path("forms")) {
            JsonNode d = details.path(form.path("id").asText());
            if (d.isObject() && form instanceof com.fasterxml.jackson.databind.node.ObjectNode o) {
                d.fields().forEachRemaining(e -> o.set(e.getKey(), e.getValue()));
            }
        }
    }

    private JsonNode read(Path dir, String file) {
        Path p = dir.resolve(file);
        if (!Files.isRegularFile(p)) {
            throw invalid("the file " + p + " is missing");
        }
        try {
            return json.readTree(Files.readAllBytes(p));
        } catch (IOException e) {
            throw invalid("the file " + p + " cannot be read: " + e.getMessage());
        }
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(java.util.Locale.ROOT);
    }

    private static ApiException invalid(String why) {
        return new ApiException(500, "BUNDLE_INVALID", "The data bundle cannot be loaded: " + why + ". Rebuild it with the importer.");
    }
}
