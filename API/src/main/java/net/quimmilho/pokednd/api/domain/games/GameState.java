/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.domain.games;

import java.util.Locale;
import java.util.Optional;

/**
 * The states of a game (chapter 3.4) and the commands that move it between them. The diagram of 3.4 is the {@link #next} table:
 *
 * <pre>
 * Draft   --open lobby--> Lobby          Running --pause--> Paused        Ended --archive--> Archived
 * Lobby   --start------> Running         Paused  --resume-> Running       Draft --archive--> Archived (discard)
 * Lobby   --close lobby-> Running        Running --end----> Ended
 * Running --open lobby--> Lobby (late join)   Paused --end--> Ended
 * </pre>
 *
 * "Start game" and "close lobby" are two names for the same step from Lobby to Running; the app and the mod use the one that fits
 * the moment (the first start, or the end of a late-join lobby).
 */
public enum GameState {
    /** Created, settings editable, nobody can join yet. */
    DRAFT,
    /** The join code is valid; players ask to join and the admin accepts. */
    LOBBY,
    /** Rounds are being played. */
    RUNNING,
    /** Everything is frozen and saved. */
    PAUSED,
    /** The campaign is over; read-only. */
    ENDED,
    /** Hidden from lists, kept in the database. */
    ARCHIVED;

    /** What a game admin can ask for. */
    public enum Command {
        OPEN_LOBBY, CLOSE_LOBBY, START, PAUSE, RESUME, END, ARCHIVE
    }

    /** The name stored in the database and sent to the app ({@code draft}, {@code lobby}…). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static GameState ofId(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }

    /**
     * Counts toward "one server, one active game" (3.8). A draft is not active (nobody can join it), and an ended or archived game is over.
     */
    public boolean active() {
        return this == LOBBY || this == RUNNING || this == PAUSED;
    }

    /** The join code works only in this state. */
    public boolean lobbyOpen() {
        return this == LOBBY;
    }

    /** Where this command leads from this state; empty if the command is not allowed here. */
    public Optional<GameState> next(Command command) {
        GameState to = switch (command) {
            case OPEN_LOBBY -> this == DRAFT || this == RUNNING ? LOBBY : null;
            case CLOSE_LOBBY, START -> this == LOBBY ? RUNNING : null;
            case PAUSE -> this == RUNNING ? PAUSED : null;
            case RESUME -> this == PAUSED ? RUNNING : null;
            case END -> this == RUNNING || this == PAUSED ? ENDED : null;
            case ARCHIVE -> this == ENDED || this == DRAFT ? ARCHIVED : null;
        };
        return Optional.ofNullable(to);
    }
}
