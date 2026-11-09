/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.quimmilho.pokednd.api.settings.SettingDef.Type;

/**
 * Every setting of Appendix A (chapter 12): its default and what values it accepts. The list comes from {@code settings/registry.json},
 * which {@code tools/gen-settings.mjs} generates from the document, so the code and the document cannot drift apart.
 *
 * <p>{@link #check} is the one place that decides whether a value is allowed. It also puts the value in its one written form, so the
 * same setting is stored the same way whoever sent it.
 */
@ApplicationScoped
public class SettingsRegistry {

    public static final String RESOURCE = "/settings/registry.json";
    /** The largest a data setting may be once written as JSON. */
    public static final int DATA_MAX_BYTES = 256 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();

    /** The answer of {@link #check}: the value to keep, or why it is refused. Exactly one of them is set. */
    public record Checked(JsonNode value, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    private final Map<String, SettingDef> byKey;

    public SettingsRegistry() {
        this(RESOURCE);
    }

    SettingsRegistry(String resource) {
        Map<String, SettingDef> map = new LinkedHashMap<>();
        try (InputStream in = SettingsRegistry.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("The settings registry is missing: " + resource + " (run node tools/gen-settings.mjs)");
            }
            JsonNode root = JSON.readTree(in);
            for (JsonNode s : root.get("settings")) {
                SettingDef def = parse(s);
                map.put(def.key(), def);
            }
        } catch (IOException e) {
            throw new IllegalStateException("The settings registry cannot be read: " + resource, e);
        }
        this.byKey = Collections.unmodifiableMap(map);
        // A default that its own rules refuse is a mistake in the document or in tools/settings-schema.json: stop at start, not in a game.
        for (SettingDef def : byKey.values()) {
            if (def.defaultValue() != null && !def.defaultValue().isNull()) {
                Checked c = check(def, def.defaultValue());
                if (!c.ok()) {
                    throw new IllegalStateException("The default of " + def.key() + " is not allowed by its own rules: " + c.error());
                }
            }
        }
    }

    private static SettingDef parse(JsonNode s) {
        List<String> allowed = null;
        if (s.has("allowed")) {
            allowed = new ArrayList<>();
            for (JsonNode a : s.get("allowed")) {
                allowed.add(a.asText());
            }
            allowed = List.copyOf(allowed);
        }
        return new SettingDef(s.get("key").asText(), s.get("group").asText(), Type.ofId(s.get("type").asText()), s.get("default"), s.get("defaultText").asText(),
                s.get("chapter").asInt(), s.get("description").asText(), allowed, num(s, "min"), num(s, "max"), text(s, "pattern"), text(s, "patternHint"),
                text(s, "tokenPattern"), text(s, "tokenHint"), s.has("minItems") ? s.get("minItems").asInt() : null,
                s.has("maxItems") ? s.get("maxItems").asInt() : null, num(s, "itemMin"), num(s, "itemMax"), s.path("unique").asBoolean(false),
                s.has("minLength") ? s.get("minLength").asInt() : null, s.has("maxLength") ? s.get("maxLength").asInt() : null);
    }

    private static Double num(JsonNode s, String f) {
        return s.has(f) ? s.get(f).asDouble() : null;
    }

    private static String text(JsonNode s, String f) {
        return s.has(f) ? s.get(f).asText() : null;
    }

    // ------------------------------------------------------------------ lookup

    public boolean has(String key) {
        return byKey.containsKey(key);
    }

    public SettingDef get(String key) {
        return byKey.get(key);
    }

    /** All settings, in the order of Appendix A. */
    public java.util.Collection<SettingDef> all() {
        return byKey.values();
    }

    public Set<String> keys() {
        return byKey.keySet();
    }

    // ------------------------------------------------------------------ validation

    /** Checks a value for a key. A key that does not exist is refused with a clear message. */
    public Checked check(String key, JsonNode raw) {
        SettingDef def = byKey.get(key);
        if (def == null) {
            return new Checked(null, key + ": there is no such setting.");
        }
        Checked c = check(def, raw);
        return c.ok() ? c : new Checked(null, key + ": " + c.error());
    }

    /**
     * Checks a whole map of settings (a rule set, or a change to a game). Returns the values to keep, in written form; the errors, one
     * for each setting that is refused, are collected in {@code errors}.
     */
    public Map<String, JsonNode> checkAll(Map<String, JsonNode> raw, List<Map<String, String>> errors) {
        Map<String, JsonNode> kept = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> e : raw.entrySet()) {
            Checked c = check(e.getKey(), e.getValue());
            if (c.ok()) {
                kept.put(e.getKey(), c.value());
            } else {
                errors.add(Map.of("key", e.getKey(), "message", c.error()));
            }
        }
        return kept;
    }

    private Checked check(SettingDef def, JsonNode raw) {
        if (raw == null || raw.isNull() || raw.isMissingNode()) {
            return refuse("a value is needed.");
        }
        return switch (def.type()) {
            case BOOL -> raw.isBoolean() ? ok(raw) : refuse("must be true or false.");
            case INT -> checkInt(def, raw);
            case DECIMAL -> checkDecimal(def, raw);
            case ENUM -> checkEnum(def, raw);
            case STRING -> checkString(def, raw);
            case CSV -> checkCsv(def, raw);
            case INT_LIST -> checkIntList(def, raw);
            case JSON -> checkJsonObject(raw);
            case DATA -> checkData(raw);
        };
    }

    private static Checked ok(JsonNode v) {
        return new Checked(v, null);
    }

    private static Checked refuse(String why) {
        return new Checked(null, why);
    }

    private static String shown(JsonNode v) {
        String t = v.toString();
        return t.length() > 60 ? t.substring(0, 57) + "..." : t;
    }

    private static String number(double d) {
        return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d);
    }

    private static double minOf(SettingDef d) {
        return d.min() != null ? d.min() : 0;
    }

    private Checked checkInt(SettingDef def, JsonNode raw) {
        if (!raw.isNumber() || !(raw.isIntegralNumber() || (Double.isFinite(raw.asDouble()) && raw.asDouble() == Math.rint(raw.asDouble())))) {
            return refuse("must be a whole number (got " + shown(raw) + ").");
        }
        long v = raw.asLong();
        if (Math.abs(raw.asDouble()) > 1e15) {
            return refuse("is too large.");
        }
        return inRange(def, v, JsonNodeFactory.instance.numberNode(v));
    }

    private Checked checkDecimal(SettingDef def, JsonNode raw) {
        if (!raw.isNumber() || !Double.isFinite(raw.asDouble())) {
            return refuse("must be a number (got " + shown(raw) + ").");
        }
        return inRange(def, raw.asDouble(), JsonNodeFactory.instance.numberNode(raw.asDouble()));
    }

    private static Checked inRange(SettingDef def, double v, JsonNode value) {
        double min = minOf(def);
        if (v < min) {
            return refuse("must be at least " + number(min) + " (got " + number(v) + ").");
        }
        if (def.max() != null && v > def.max()) {
            return refuse("must be at most " + number(def.max()) + " (got " + number(v) + ").");
        }
        return ok(value);
    }

    private Checked checkEnum(SettingDef def, JsonNode raw) {
        if (!raw.isTextual() || !def.allowed().contains(raw.asText())) {
            return refuse("must be one of " + String.join(", ", def.allowed()) + " (got " + shown(raw) + ").");
        }
        return ok(raw);
    }

    private Checked checkString(SettingDef def, JsonNode raw) {
        if (!raw.isTextual()) {
            return refuse("must be text (got " + shown(raw) + ").");
        }
        String s = raw.asText();
        int minLength = def.minLength() != null ? def.minLength() : 1;
        int maxLength = def.maxLength() != null ? def.maxLength() : 200;
        if (s.length() < minLength || s.length() > maxLength) {
            return refuse("must have " + minLength + " to " + maxLength + " characters.");
        }
        if (s.chars().anyMatch(Character::isISOControl)) {
            return refuse("must not contain control characters.");
        }
        if (def.pattern() != null && !Pattern.compile(def.pattern()).matcher(s).matches()) {
            return refuse("must be " + (def.patternHint() != null ? def.patternHint() : "in the right form") + " (got " + shown(raw) + ").");
        }
        return ok(raw);
    }

    /** The tokens of a text list (or of a JSON array of text), trimmed; null if it is not one. */
    private static List<String> tokens(JsonNode raw) {
        List<String> out = new ArrayList<>();
        if (raw.isTextual()) {
            if (!raw.asText().isBlank()) {
                for (String t : raw.asText().split(",", -1)) {
                    out.add(t.strip());
                }
            }
        } else if (raw.isArray()) {
            for (JsonNode n : raw) {
                if (!n.isTextual()) {
                    return null;
                }
                out.add(n.asText().strip());
            }
        } else {
            return null;
        }
        return out;
    }

    private Checked checkCsv(SettingDef def, JsonNode raw) {
        List<String> tokens = tokens(raw);
        if (tokens == null) {
            return refuse("must be a list written as text, such as \"a,b,c\" (got " + shown(raw) + ").");
        }
        int minItems = def.minItems() != null ? def.minItems() : 0;
        if (tokens.size() < minItems) {
            return refuse("needs at least " + minItems + " entr" + (minItems == 1 ? "y" : "ies") + ".");
        }
        if (def.maxItems() != null && tokens.size() > def.maxItems()) {
            return refuse("has at most " + def.maxItems() + " entries.");
        }
        Pattern tokenPattern = def.tokenPattern() == null ? null : Pattern.compile(def.tokenPattern());
        Set<String> seen = new LinkedHashSet<>();
        for (String t : tokens) {
            if (t.isEmpty()) {
                return refuse("has an empty entry.");
            }
            if (def.allowed() != null && !def.allowed().contains(t)) {
                return refuse("has \"" + t + "\", which is not one of " + String.join(", ", def.allowed()) + ".");
            }
            if (tokenPattern != null && !tokenPattern.matcher(t).matches()) {
                return refuse("has \"" + t + "\", which is not " + (def.tokenHint() != null ? def.tokenHint() : "in the right form") + ".");
            }
            if (!seen.add(t) && def.unique()) {
                return refuse("lists \"" + t + "\" twice.");
            }
        }
        String joined = String.join(",", tokens);
        if (joined.length() > 500) {
            return refuse("is too long.");
        }
        return ok(JsonNodeFactory.instance.textNode(joined));
    }

    private Checked checkIntList(SettingDef def, JsonNode raw) {
        List<Long> numbers = new ArrayList<>();
        if (raw.isArray()) {
            for (JsonNode n : raw) {
                if (!n.isNumber() || n.asDouble() != Math.rint(n.asDouble())) {
                    return refuse("must be a list of whole numbers (got " + shown(raw) + ").");
                }
                numbers.add(n.asLong());
            }
        } else if (raw.isTextual()) {
            for (String t : raw.asText().split(",", -1)) {
                try {
                    numbers.add(Long.parseLong(t.strip()));
                } catch (NumberFormatException e) {
                    return refuse("must be a list of whole numbers such as 5,10,15 (got " + shown(raw) + ").");
                }
            }
        } else {
            return refuse("must be a list of whole numbers (got " + shown(raw) + ").");
        }
        int minItems = def.minItems() != null ? def.minItems() : 1;
        int maxItems = def.maxItems() != null ? def.maxItems() : 50;
        if (numbers.size() < minItems || numbers.size() > maxItems) {
            return refuse(minItems == maxItems ? "needs exactly " + minItems + " numbers." : "needs " + minItems + " to " + maxItems + " numbers.");
        }
        double lo = def.itemMin() != null ? def.itemMin() : 0;
        Set<Long> seen = new LinkedHashSet<>();
        ArrayNode out = JsonNodeFactory.instance.arrayNode();
        for (long n : numbers) {
            if (n < lo || (def.itemMax() != null && n > def.itemMax())) {
                return refuse("has " + n + ", which is not between " + number(lo) + " and " + (def.itemMax() != null ? number(def.itemMax()) : "a large number") + ".");
            }
            if (!seen.add(n) && def.unique()) {
                return refuse("lists " + n + " twice.");
            }
            out.add(n);
        }
        return ok(out);
    }

    private Checked checkJsonObject(JsonNode raw) {
        if (!raw.isObject()) {
            return refuse("must be an object such as {\"catch\": 20} (got " + shown(raw) + ").");
        }
        if (raw.size() > 50) {
            return refuse("has too many entries.");
        }
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        for (Map.Entry<String, JsonNode> e : (Iterable<Map.Entry<String, JsonNode>>) raw::fields) {
            JsonNode v = e.getValue();
            if (e.getKey().isBlank() || e.getKey().length() > 40) {
                return refuse("has an entry with a bad name.");
            }
            if (!v.isNumber() || v.asDouble() != Math.rint(v.asDouble()) || v.asLong() < 2 || v.asLong() > 1000) {
                return refuse("\"" + e.getKey() + "\" must be a die size from 2 to 1000 (got " + shown(v) + ").");
            }
            out.put(e.getKey(), v.asLong());
        }
        return ok(out);
    }

    private Checked checkData(JsonNode raw) {
        if (!(raw.isObject() || raw.isArray() || raw.isTextual())) {
            return refuse("must be a table, a list or a name (got " + shown(raw) + ").");
        }
        try {
            if (JSON.writeValueAsBytes(raw).length > DATA_MAX_BYTES) {
                return refuse("is too large (the limit is " + DATA_MAX_BYTES / 1024 + " KB).");
            }
        } catch (IOException e) {
            return refuse("cannot be read.");
        }
        return ok(raw);
    }

    /** The settings of a map that differ from their defaults, in registry order; used to save a game's rules as a rule set. */
    public Map<String, JsonNode> withoutDefaults(Map<String, JsonNode> values) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        for (SettingDef def : byKey.values()) {
            JsonNode v = values.get(def.key());
            if (v != null && !sameValue(def.defaultValue(), v)) {
                out.put(def.key(), v);
            }
        }
        return out;
    }

    /** Are two values the same setting value? Numbers compare by value ({@code 1} and {@code 1.0} are the same). */
    public static boolean sameValue(JsonNode a, JsonNode b) {
        if (a == null || a.isNull()) {
            return b == null || b.isNull();
        }
        if (b == null || b.isNull()) {
            return false;
        }
        if (a.isNumber() && b.isNumber()) {
            return Double.compare(a.asDouble(), b.asDouble()) == 0;
        }
        if (a.isArray() && b.isArray()) {
            if (a.size() != b.size()) {
                return false;
            }
            for (int i = 0; i < a.size(); i++) {
                if (!sameValue(a.get(i), b.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a.isObject() && b.isObject()) {
            if (a.size() != b.size()) {
                return false;
            }
            for (Map.Entry<String, JsonNode> e : (Iterable<Map.Entry<String, JsonNode>>) a::fields) {
                if (!b.has(e.getKey()) || !sameValue(e.getValue(), b.get(e.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        return a.equals(b);
    }
}
