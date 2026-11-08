/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The Pokémon Showdown data (MIT licence, chapter 19.5): moves, abilities, items, natures and the type chart, as exported by
 * {@code tools/export-showdown.mjs} and committed in {@code src/main/resources/showdown/}. Event handlers (code) are not in
 * the files; each entry only lists their names under {@code handlers}.
 *
 * @param moves     by Showdown move id (for example {@code flamethrower})
 * @param abilities by Showdown ability id
 * @param items     by Showdown item id (letters and digits only, for example {@code thunderstone})
 * @param natures   by nature id
 * @param typeChart by lower-case type id
 */
public record Showdown(SortedMap<String, JsonNode> moves, SortedMap<String, JsonNode> abilities, SortedMap<String, JsonNode> items,
                       SortedMap<String, JsonNode> natures, SortedMap<String, JsonNode> typeChart) {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Loads the copy that is packaged with Core. */
    public static Showdown load() {
        return new Showdown(table("moves"), table("abilities"), table("items"), table("natures"), table("typechart"));
    }

    /** Loads the files from a folder (used by tests and by a fresh export). */
    public static Showdown load(Path dir) {
        return new Showdown(file(dir, "moves"), file(dir, "abilities"), file(dir, "items"), file(dir, "natures"),
                file(dir, "typechart"));
    }

    private static SortedMap<String, JsonNode> table(String name) {
        String resource = "/showdown/" + name + ".json";
        try (InputStream in = Showdown.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new ImportException("Missing packaged resource " + resource + " (make it with: node tools/export-showdown.mjs)");
            }
            return index(JSON.readTree(in), resource);
        } catch (IOException e) {
            throw new ImportException("Cannot read " + resource + ": " + e.getMessage(), e);
        }
    }

    private static SortedMap<String, JsonNode> file(Path dir, String name) {
        Path p = dir.resolve(name + ".json");
        try {
            return index(JSON.readTree(Files.readString(p)), p.toString());
        } catch (IOException e) {
            throw new ImportException("Cannot read " + p + ": " + e.getMessage(), e);
        }
    }

    private static SortedMap<String, JsonNode> index(JsonNode root, String where) {
        if (!root.isObject()) {
            throw new ImportException(where + " is not a JSON object");
        }
        TreeMap<String, JsonNode> out = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            out.put(e.getKey(), e.getValue());
        }
        return Collections.unmodifiableSortedMap(out);
    }
}
