/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The state after the log entry {@code seq} (chapter 12.5, 18: {@code Snapshot}). A restart or a deep undo starts from the newest snapshot
 * and replays only the entries after it.
 *
 * @param seq   the last log entry that is in this state
 * @param round the round the game was in
 * @param state the state as JSON ({@link GameStateJson})
 */
public record Snapshot(long seq, int round, JsonNode state) {

    public GameState read() {
        return GameStateJson.read(state);
    }
}
