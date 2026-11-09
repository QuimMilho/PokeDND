/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Who did what (chapter 12.4: every override is logged; chapter 18: {@code AuditEntry}). There is one entry for every event that is
 * added, and one for every undo and redo.
 *
 * @param seq      the log entry it is about (for an undo or redo, the last entry it touched)
 * @param actionId the action it belongs to (0 for an undo or redo)
 * @param actor    who did it
 * @param action   the event type, or {@code undo} or {@code redo}
 * @param target   what it was about: a trainer id, a request id, {@code game}, or {@code actions:5,9} for an undo
 * @param atMillis when
 * @param before   the parts of the state that changed, as they were
 * @param after    the same parts after the change
 */
public record AuditEntry(long seq, long actionId, String actor, String action, String target, long atMillis, JsonNode before, JsonNode after) {
}
