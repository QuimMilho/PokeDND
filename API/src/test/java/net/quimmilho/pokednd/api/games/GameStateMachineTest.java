/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.games;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.quimmilho.pokednd.api.domain.games.GameState;
import net.quimmilho.pokednd.api.domain.games.GameState.Command;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static net.quimmilho.pokednd.api.domain.games.GameState.ARCHIVED;
import static net.quimmilho.pokednd.api.domain.games.GameState.DRAFT;
import static net.quimmilho.pokednd.api.domain.games.GameState.ENDED;
import static net.quimmilho.pokednd.api.domain.games.GameState.LOBBY;
import static net.quimmilho.pokednd.api.domain.games.GameState.PAUSED;
import static net.quimmilho.pokednd.api.domain.games.GameState.RUNNING;

/**
 * The state diagram of chapter 3.4 written out here a second time, arrow by arrow. Every state is tried with every command, so each
 * allowed transition and each forbidden one is a test case.
 */
class GameStateMachineTest {

    /** One arrow of the diagram: from, command, to. */
    private record Arrow(GameState from, Command command, GameState to) {
    }

    /** The arrows of the diagram of 3.4, and nothing else. */
    private static final Arrow[] DIAGRAM = {
            new Arrow(DRAFT, Command.OPEN_LOBBY, LOBBY),      // Draft --> Lobby: open lobby
            new Arrow(LOBBY, Command.START, RUNNING),         // Lobby --> Running: start game
            new Arrow(RUNNING, Command.PAUSE, PAUSED),        // Running --> Paused: pause
            new Arrow(PAUSED, Command.RESUME, RUNNING),       // Paused --> Running: resume
            new Arrow(RUNNING, Command.OPEN_LOBBY, LOBBY),    // Running --> Lobby: reopen lobby (late join)
            new Arrow(LOBBY, Command.CLOSE_LOBBY, RUNNING),   // Lobby --> Running: close lobby
            new Arrow(RUNNING, Command.END, ENDED),           // Running --> Ended: end game
            new Arrow(PAUSED, Command.END, ENDED),            // Paused --> Ended: end game
            new Arrow(ENDED, Command.ARCHIVE, ARCHIVED),      // Ended --> Archived: archive
            new Arrow(DRAFT, Command.ARCHIVE, ARCHIVED),      // Draft --> Archived: discard
    };

    record Case(GameState from, Command command) {
        @Override
        public String toString() {
            return from + " + " + command;
        }
    }

    static Stream<Case> everyStateWithEveryCommand() {
        Stream.Builder<Case> all = Stream.builder();
        for (GameState s : GameState.values()) {
            for (Command c : Command.values()) {
                all.add(new Case(s, c));
            }
        }
        return all.build();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyStateWithEveryCommand")
    void aTransitionIsAllowedExactlyWhenTheDiagramHasThatArrow(Case c) {
        Map<String, GameState> arrows = new HashMap<>();
        for (Arrow a : DIAGRAM) {
            arrows.put(a.from() + "/" + a.command(), a.to());
        }
        Optional<GameState> expected = Optional.ofNullable(arrows.get(c.from() + "/" + c.command()));
        assertEquals(expected, c.from().next(c.command()), c.toString());
    }

    @Test
    void theDiagramHasTenArrowsAndTheTableAllowsExactlyThose() {
        assertEquals(10, DIAGRAM.length);
        int allowed = 0;
        for (GameState s : GameState.values()) {
            for (Command c : Command.values()) {
                if (s.next(c).isPresent()) {
                    allowed++;
                }
            }
        }
        assertEquals(10, allowed);
        // 6 states x 7 commands = 42 pairs, so 32 are forbidden
        assertEquals(42, GameState.values().length * Command.values().length);
    }

    @Test
    void nothingLeavesArchivedAndEndedGamesOnlyGetArchived() {
        for (Command c : Command.values()) {
            assertTrue(ARCHIVED.next(c).isEmpty(), "archived + " + c);
            if (c != Command.ARCHIVE) {
                assertTrue(ENDED.next(c).isEmpty(), "ended + " + c);
            }
        }
    }

    @Test
    void onlyLobbyRunningAndPausedCountAsActive() {
        assertFalse(DRAFT.active());
        assertTrue(LOBBY.active());
        assertTrue(RUNNING.active());
        assertTrue(PAUSED.active());
        assertFalse(ENDED.active());
        assertFalse(ARCHIVED.active());
    }

    @Test
    void theJoinCodeWorksOnlyInTheLobby() {
        for (GameState s : GameState.values()) {
            assertEquals(s == LOBBY, s.lobbyOpen(), s.name());
        }
    }

    @Test
    void statesHaveTheNamesTheDatabaseUses() {
        for (GameState s : GameState.values()) {
            assertEquals(s, GameState.ofId(s.id()));
        }
        assertEquals("draft", DRAFT.id());
        assertEquals("archived", ARCHIVED.id());
    }
}
