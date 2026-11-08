/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.cards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import net.quimmilho.pokednd.core.importer.ImportException;

/**
 * The hand-written overrides of the signature moves ({@code cards/signature-overrides.json}, setting
 * {@code cards.signature.overridesFile}, chapter 9.3). An entry fixes the moves of one card and always wins over the rule.
 *
 * <pre>
 * { "0006_charizard": ["flamethrower", "air-slash", "dragon-claw", "roost"] }
 * </pre>
 *
 * <p>The key is the four-digit National Dex number, an underscore, and the species id for the base form
 * ({@code 0006_charizard}) or the form id for any other form ({@code 0105_marowak_alolan}). Move ids may be written as
 * {@code air-slash}, {@code airslash} or {@code Air Slash}: spaces, hyphens and capitals are ignored. A key that starts
 * with an underscore (for example {@code _note}) is a comment.
 *
 * @param moves the overrides by card key, with the move ids in the importer's form ({@code airslash})
 */
public record SignatureOverrides(SortedMap<String, List<String>> moves) {

    /** An empty set of overrides. */
    public static SignatureOverrides none() {
        return new SignatureOverrides(new TreeMap<>());
    }

    /** The key of a card: {@code 0006_charizard} for a base form, {@code 0105_marowak_alolan} for another form. */
    public static String cardKey(int dex, String speciesId, String formId, boolean baseForm) {
        return String.format(Locale.ROOT, "%04d_%s", dex, baseForm ? speciesId : formId);
    }

    /** The move id in the form used in the data: lower case, letters and digits only. */
    public static String normalise(String move) {
        return move.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** The override of a card, if there is one. */
    public Optional<List<String>> get(String cardKey) {
        return Optional.ofNullable(moves.get(cardKey));
    }

    /** Reads the file. A missing file is an error: pass no file at all to use no overrides. */
    public static SignatureOverrides read(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new ImportException("Missing signature overrides file " + file);
        }
        try {
            return parse(Files.readString(file), file.toString());
        } catch (IOException e) {
            throw new ImportException("Cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    /** Reads overrides from JSON text; {@code source} is only used in error messages. */
    public static SignatureOverrides parse(String json, String source) {
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(json);
        } catch (IOException e) {
            throw new ImportException(source + " is not valid JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new ImportException(source + ": expected a JSON object like {\"0006_charizard\": [\"flamethrower\", ...]}");
        }
        TreeMap<String, List<String>> out = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (e.getKey().startsWith("_")) {
                continue;
            }
            if (!e.getKey().matches("\\d{4}_[a-z0-9_]+")) {
                throw new ImportException(source + ": \"" + e.getKey() + "\" is not a card key like 0006_charizard");
            }
            if (!e.getValue().isArray()) {
                throw new ImportException(source + ": " + e.getKey() + " must be a list of moves");
            }
            List<String> list = new ArrayList<>();
            for (JsonNode m : e.getValue()) {
                if (!m.isTextual() || normalise(m.asText()).isEmpty()) {
                    throw new ImportException(source + ": " + e.getKey() + " has a move that is not a name");
                }
                String id = normalise(m.asText());
                if (list.contains(id)) {
                    throw new ImportException(source + ": " + e.getKey() + " lists " + id + " twice");
                }
                list.add(id);
            }
            if (list.isEmpty() || list.size() > 4) {
                throw new ImportException(source + ": " + e.getKey() + " needs one to four moves, not " + list.size());
            }
            out.put(e.getKey(), List.copyOf(list));
        }
        return new SignatureOverrides(out);
    }

    /** A copy with one more override (for tests and tools). */
    public SignatureOverrides with(String cardKey, List<String> moveIds) {
        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>(moves);
        copy.put(cardKey, moveIds.stream().map(SignatureOverrides::normalise).toList());
        return new SignatureOverrides(new TreeMap<>(copy));
    }
}
