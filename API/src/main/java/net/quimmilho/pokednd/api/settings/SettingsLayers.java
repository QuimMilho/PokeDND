/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.settings;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the layers of chapter 12.2 combine: the value of a setting is the one from the highest layer that sets it, and the answer says
 * which layer that was. This class knows nothing about games or databases; it only lays one map over another.
 *
 * <p>Phase 33 has three layers: the built-in default, the game's rule set and the game's own changes. The site default, the server
 * default and the live override of 12.2 are further layers for the same method; they need no change here.
 */
public final class SettingsLayers {

    /** The layer names a setting can come from. */
    public static final String DEFAULT = "default";
    public static final String RULE_SET = "ruleSet";
    public static final String GAME = "game";

    private SettingsLayers() {
    }

    /** One layer: its name and the settings it sets. */
    public record Layer(String source, Map<String, JsonNode> values) {
    }

    /** The value a setting has, and where it came from. */
    public record Resolved(SettingDef def, JsonNode value, String source) {
        public boolean overridden() {
            return !DEFAULT.equals(source);
        }
    }

    /**
     * The value of every setting, in the order of Appendix A. {@code layers} go from the lowest to the highest; the registry's defaults are
     * the layer below all of them. Keys the registry does not know are ignored.
     */
    public static Map<String, Resolved> resolve(SettingsRegistry registry, List<Layer> layers) {
        Map<String, Resolved> out = new LinkedHashMap<>();
        for (SettingDef def : registry.all()) {
            out.put(def.key(), resolveOne(def, layers));
        }
        return out;
    }

    public static Resolved resolveOne(SettingDef def, List<Layer> layers) {
        for (int i = layers.size() - 1; i >= 0; i--) {
            JsonNode v = layers.get(i).values().get(def.key());
            if (v != null && !v.isNull()) {
                return new Resolved(def, v, layers.get(i).source());
            }
        }
        return new Resolved(def, def.defaultValue(), DEFAULT);
    }
}
