/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import com.fasterxml.jackson.annotation.JsonValue;

/** Where a game is in its life (chapter 12): draft, lobby, running, paused, ended. */
public enum GameStatus {
    DRAFT("draft"),
    LOBBY("lobby"),
    RUNNING("running"),
    PAUSED("paused"),
    ENDED("ended");

    private final String id;

    GameStatus(String id) {
        this.id = id;
    }

    /** The word the API and the mod use ({@code "running"}); it is also what is written in the log and in snapshots. */
    @JsonValue
    public String id() {
        return id;
    }
}
