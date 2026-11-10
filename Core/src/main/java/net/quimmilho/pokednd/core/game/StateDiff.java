/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What changed between two states, for the audit trail (chapter 18: {@code AuditEntry.before} and {@code after}). Only the parts that
 * differ are kept, down to one trainer or one roll request: a change of one trainer's HP shows that trainer, not the whole game.
 */
public record StateDiff(ObjectNode before, ObjectNode after) {

    public static StateDiff between(GameState from, GameState to) {
        JsonNode a = GameStateJson.write(from);
        JsonNode b = GameStateJson.write(to);
        ObjectNode before = JsonNodeFactory.instance.objectNode();
        ObjectNode after = JsonNodeFactory.instance.objectNode();
        for (String field : union(a, b)) {
            JsonNode x = a.get(field);
            JsonNode y = b.get(field);
            if (x != null && x.equals(y)) {
                continue;
            }
            if (x != null && y != null && x.isObject() && y.isObject()) {
                ObjectNode xb = before.putObject(field);
                ObjectNode ya = after.putObject(field);
                for (String key : union(x, y)) {
                    JsonNode xv = x.get(key);
                    JsonNode yv = y.get(key);
                    if (xv != null && xv.equals(yv)) {
                        continue;
                    }
                    if (xv != null) {
                        xb.set(key, xv);
                    }
                    if (yv != null) {
                        ya.set(key, yv);
                    }
                }
            } else {
                if (x != null) {
                    before.set(field, x);
                }
                if (y != null) {
                    after.set(field, y);
                }
            }
        }
        return new StateDiff(before, after);
    }

    private static Set<String> union(JsonNode a, JsonNode b) {
        Set<String> names = new LinkedHashSet<>();
        a.fieldNames().forEachRemaining(names::add);
        b.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
