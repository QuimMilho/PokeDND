/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The rule sets that come with the game: the plain defaults and the presets of chapter 12.1 (Casual, Hardcore, At the table, Remote).
 * They are read from {@code settings/builtin-rulesets.json} and refused at start if one names a setting that does not exist or a
 * value that setting does not allow.
 */
@ApplicationScoped
public class BuiltinRuleSets {

    public static final String PREFIX = "builtin:";
    public static final String DEFAULTS = "builtin:defaults";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** A built-in rule set. Its {@code settings} are already checked and in written form. */
    public record Builtin(String id, String name, String description, Map<String, JsonNode> settings) {
    }

    private final Map<String, Builtin> byId;

    @Inject
    public BuiltinRuleSets(SettingsRegistry registry) {
        Map<String, Builtin> map = new LinkedHashMap<>();
        try (InputStream in = BuiltinRuleSets.class.getResourceAsStream("/settings/builtin-rulesets.json")) {
            if (in == null) {
                throw new IllegalStateException("settings/builtin-rulesets.json is missing");
            }
            for (JsonNode rs : JSON.readTree(in).get("ruleSets")) {
                Map<String, JsonNode> raw = new LinkedHashMap<>();
                rs.get("settings").fields().forEachRemaining(e -> raw.put(e.getKey(), e.getValue()));
                List<Map<String, String>> errors = new ArrayList<>();
                Map<String, JsonNode> kept = registry.checkAll(raw, errors);
                if (!errors.isEmpty()) {
                    throw new IllegalStateException("The built-in rule set " + rs.get("id").asText() + " is wrong: " + errors);
                }
                Builtin b = new Builtin(rs.get("id").asText(), rs.get("name").asText(), rs.get("description").asText(), Collections.unmodifiableMap(kept));
                map.put(b.id(), b);
            }
        } catch (IOException e) {
            throw new IllegalStateException("settings/builtin-rulesets.json cannot be read", e);
        }
        this.byId = Collections.unmodifiableMap(map);
    }

    public static boolean isBuiltin(String id) {
        return id != null && id.startsWith(PREFIX);
    }

    public Optional<Builtin> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<Builtin> all() {
        return List.copyOf(byId.values());
    }
}
