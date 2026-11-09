/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.domain.audit;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The audit trail (chapters 12.4 and 17.2, {@code AuditEntry}): who changed what in a game, and what it was before and after. Entries
 * are only ever added. Phase 33 writes the changes of settings; the rest of the trail and the screen that reads it come in phase 54.
 */
public interface AuditRepository {

    /**
     * One entry.
     *
     * @param action a dotted name such as {@code settings.update}
     * @param target what it was done to (a rule set id, or the id of the entry that an undo reverts)
     * @param before the state before, as JSON; null when there was none
     * @param after  the state after, as JSON; null when there is none
     */
    record AuditEntry(UUID id, UUID gameId, UUID actorUserId, String action, String target, JsonNode before, JsonNode after, Instant createdAt) {
    }

    void append(AuditEntry entry);

    /** The entries of a game whose action starts with {@code actionPrefix}, newest first. */
    List<AuditEntry> forGame(UUID gameId, String actionPrefix);
}
