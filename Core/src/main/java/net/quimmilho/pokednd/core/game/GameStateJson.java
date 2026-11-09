/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A game state as JSON, for snapshots (chapter 18: {@code Snapshot.state}) and for the audit trail. Writing a state and reading it back
 * gives an equal state: nothing is lost.
 */
public final class GameStateJson {

    private GameStateJson() {
    }

    public static JsonNode write(GameState state) {
        return EventTypes.Json.MAPPER.valueToTree(state);
    }

    /** @throws InvalidEventException if the JSON is not a game state */
    public static GameState read(JsonNode json) {
        try {
            return EventTypes.Json.MAPPER.treeToValue(json, GameState.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new InvalidEventException("A stored game state is not valid: " + e.getMessage());
        }
    }
}
