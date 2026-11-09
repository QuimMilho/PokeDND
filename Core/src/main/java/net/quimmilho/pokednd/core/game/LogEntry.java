/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One row of the event log (chapter 18: {@code GameEvent}). The game is its log: the state is made from the entries that are not undone.
 *
 * @param seq      the place in the log, 1, 2, 3, … without gaps
 * @param actionId the {@code seq} of the first event of the action this event belongs to; the events of one action are undone together
 * @param type     the event type ({@link EventTypes})
 * @param actor    who did it: a user id, or {@code system}
 * @param atMillis when, in milliseconds since 1970 (given by the caller; the engine has no clock and ignores it)
 * @param payload  the event as JSON ({@link EventTypes#payload})
 * @param undone   true once the action was undone
 * @param discarded true for an undone action that can no longer be redone, because another action was added after the undo; such an
 *                 entry stays in the history for good
 */
public record LogEntry(long seq, long actionId, String type, String actor, long atMillis, JsonNode payload, boolean undone, boolean discarded) {

    /** The event of this entry. */
    public GameEvent event() {
        return EventTypes.read(type, payload);
    }

    public LogEntry withUndone(boolean value) {
        return value == undone ? this : new LogEntry(seq, actionId, type, actor, atMillis, payload, value, false);
    }

    /** True for an undone entry that a redo may bring back. */
    public boolean redoable() {
        return undone && !discarded;
    }

    /** This undone entry can no longer be redone. */
    public LogEntry discard() {
        return undone && !discarded ? new LogEntry(seq, actionId, type, actor, atMillis, payload, true, true) : this;
    }
}
