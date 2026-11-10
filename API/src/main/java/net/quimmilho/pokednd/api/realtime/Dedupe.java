/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.realtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers the ids of the last messages a peer sent, and the answer that was given to each (chapter 14.7: "commands carry ids and are
 * idempotent"). A message with an id seen before is not handled a second time; the old answer is sent again, so a peer that lost our
 * answer and sent the command again still gets it.
 */
public final class Dedupe {

    private final Map<String, String> seen;

    public Dedupe(int capacity) {
        this.seen = new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > capacity;
            }
        };
    }

    /** What to do with a message id. */
    public record Verdict(boolean duplicate, String earlierAnswer) {
    }

    /** Notes the id. A new id returns {@code duplicate=false}; an old one returns the answer given before (null if there was none). */
    public synchronized Verdict check(String id) {
        if (seen.containsKey(id)) {
            return new Verdict(true, seen.get(id));
        }
        seen.put(id, null);
        return new Verdict(false, null);
    }

    /** Keeps the answer that was sent for this id. */
    public synchronized void answered(String id, String answer) {
        if (seen.containsKey(id)) {
            seen.put(id, answer);
        }
    }

    public synchronized int size() {
        return seen.size();
    }
}
