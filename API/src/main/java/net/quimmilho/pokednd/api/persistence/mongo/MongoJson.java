/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.mongo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bson.Document;

/**
 * Turns the JSON that the API keeps (settings, rule set data, species, export requests…) into native MongoDB documents and back
 * (chapter 16.3: "native documents in MongoDB"). Two things need care:
 *
 * <ul>
 *   <li>A document must be an object. JSON that is a list or a single value is stored as {@code {"__value": …}} and unwrapped on read.</li>
 *   <li>A setting key such as {@code combat.ai.level} has dots, and MongoDB does not like dots (or a leading {@code $}) in field names.
 *       In the stored document a dot is the full-width dot {@code ．} (U+FF0E) and a leading {@code $} is {@code ＄} (U+FF04). The
 *       characters are put back on read, so nobody outside this class sees them.</li>
 * </ul>
 */
final class MongoJson {

    static final String WRAPPER = "__value";
    private static final char DOT = '．';
    private static final char DOLLAR = '＄';

    private MongoJson() {
    }

    /** The document for a JSON value; null stays null. */
    static Document toDocument(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            return objectToDocument(node);
        }
        return new Document(WRAPPER, toValue(node));
    }

    /** The JSON for a stored document; null stays null. */
    static JsonNode toJson(Document doc) {
        if (doc == null) {
            return null;
        }
        if (doc.size() == 1 && doc.containsKey(WRAPPER)) {
            return fromValue(doc.get(WRAPPER));
        }
        return fromValue(doc);
    }

    private static Document objectToDocument(JsonNode node) {
        Document d = new Document();
        node.fields().forEachRemaining(e -> d.put(escape(e.getKey()), toValue(e.getValue())));
        return d;
    }

    private static Object toValue(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) {
            return null;
        }
        if (n.isObject()) {
            return objectToDocument(n);
        }
        if (n.isArray()) {
            List<Object> list = new ArrayList<>(n.size());
            n.forEach(x -> list.add(toValue(x)));
            return list;
        }
        if (n.isBoolean()) {
            return n.booleanValue();
        }
        if (n.isInt()) {
            return n.intValue();
        }
        if (n.isIntegralNumber()) {
            return n.longValue();
        }
        if (n.isNumber()) {
            return n.doubleValue();
        }
        return n.asText();
    }

    @SuppressWarnings("unchecked")
    private static JsonNode fromValue(Object v) {
        JsonNodeFactory f = JsonNodeFactory.instance;
        if (v == null) {
            return f.nullNode();
        }
        if (v instanceof Map<?, ?> m) {
            ObjectNode o = f.objectNode();
            ((Map<String, Object>) m).forEach((k, x) -> o.set(unescape(k), fromValue(x)));
            return o;
        }
        if (v instanceof List<?> l) {
            ArrayNode a = f.arrayNode();
            l.forEach(x -> a.add(fromValue(x)));
            return a;
        }
        if (v instanceof Boolean b) {
            return f.booleanNode(b);
        }
        if (v instanceof Integer i) {
            return f.numberNode(i);
        }
        if (v instanceof Long l) {
            return f.numberNode(l);
        }
        if (v instanceof Double d) {
            return f.numberNode(d);
        }
        if (v instanceof Number n) {
            return f.numberNode(n.doubleValue());
        }
        return f.textNode(v.toString());
    }

    static String escape(String key) {
        String k = key.replace('.', DOT);
        return k.startsWith("$") ? DOLLAR + k.substring(1) : k;
    }

    static String unescape(String key) {
        String k = key.replace(DOT, '.');
        return !k.isEmpty() && k.charAt(0) == DOLLAR ? "$" + k.substring(1) : k;
    }
}
