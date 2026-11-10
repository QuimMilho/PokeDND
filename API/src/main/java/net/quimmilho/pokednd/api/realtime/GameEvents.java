/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.realtime;

import java.util.UUID;
import net.quimmilho.pokednd.api.domain.games.GameRepository.Game;
import net.quimmilho.pokednd.api.domain.games.GameState;

/**
 * What happened in a game, as the services tell it. The services fire these as CDI events; {@link RealtimeEvents} observes them
 * <b>after the transaction has been committed</b> and turns them into messages for the app and the mod. A service does not know about
 * sockets, and a change that is rolled back is never announced.
 */
public final class GameEvents {

    private GameEvents() {
    }

    /** A game moved to another state (opening the lobby, starting, pausing…). */
    public record StateChanged(Game game, GameState from) {
    }

    /** A person asked to join, or an admin answered, or removed them. {@code status}: requested, accepted, rejected, kicked. */
    public record MembershipChanged(UUID gameId, UUID membershipId, UUID userId, String role, String status) {
    }

    /** The settings of a game changed; {@code version} is the new settings version. */
    public record SettingsChanged(UUID gameId) {
    }

    /** What happened to a trainer. */
    public enum TrainerChange {
        CREATED, UPDATED, REMOVED, SKIN
    }

    public record TrainerChanged(UUID gameId, UUID trainerId, TrainerChange change) {
    }
}
