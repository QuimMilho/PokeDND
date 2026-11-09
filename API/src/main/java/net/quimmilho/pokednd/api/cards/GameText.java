/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Game texts (names, descriptions, Pokédex entries) from Cobblemon's language files in {@code resources/assets/cobblemon/lang}.
 * The lookup order for Portuguese is <b>pt_pt → pt_br → en_us</b>, for English <b>en_us</b> (chapter 9.2, decided): {@code pt_pt}
 * has no Pokédex descriptions, so a Portuguese card shows the Brazilian text.
 */
public final class GameText {

    private final Path langDir;
    private final ObjectMapper json;
    private final Map<String, Map<String, String>> loaded = new HashMap<>();

    public GameText(Path resourcesDir, ObjectMapper json) {
        this.langDir = resourcesDir.resolve("assets/cobblemon/lang");
        this.json = json;
    }

    /** True if the language files exist. */
    public boolean available() {
        return Files.isRegularFile(langDir.resolve("en_us.json"));
    }

    /** The text for a key in a card language ({@code en} or {@code pt}), or empty if no file in the order has it. */
    public Optional<String> get(String key, String language) {
        for (String file : order(language)) {
            String v = file(file).get(key);
            if (v != null && !v.isBlank()) {
                return Optional.of(v);
            }
        }
        return Optional.empty();
    }

    static List<String> order(String language) {
        return "pt".equals(language) ? List.of("pt_pt", "pt_br", "en_us") : List.of("en_us");
    }

    private synchronized Map<String, String> file(String name) {
        return loaded.computeIfAbsent(name, n -> {
            Path p = langDir.resolve(n + ".json");
            Map<String, String> out = new HashMap<>();
            if (Files.isRegularFile(p)) {
                try {
                    json.readTree(Files.readAllBytes(p)).fields().forEachRemaining(e -> {
                        if (e.getValue().isTextual()) {
                            out.put(e.getKey(), e.getValue().asText());
                        }
                    });
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot read the language file " + p + ": " + e.getMessage(), e);
                }
            }
            return out;
        });
    }
}
