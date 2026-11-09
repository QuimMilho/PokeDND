/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.domain.games;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where games, their admins and their members are kept (chapter 17.2). The SQL adapter implements it. */
public interface GameRepository {

    /** Roles in {@code GameAdmin}. */
    String ADMIN_OWNER = "owner";
    String ADMIN_CO = "co";

    /** Roles in {@code Membership}. */
    String ROLE_PLAYER = "player";
    String ROLE_SPECTATOR = "spectator";

    /** States of a {@code Membership}. */
    String REQUESTED = "requested";
    String ACCEPTED = "accepted";
    String REJECTED = "rejected";
    String KICKED = "kicked";

    /**
     * A game.
     *
     * @param joinCode     the code players type to join; only while the lobby is open, otherwise null
     * @param ruleSet      the rule set the game uses: {@code builtin:<name>}, the id of a saved rule set, or null for the plain defaults
     * @param settings     the game's own setting overrides, a JSON object of key to value (chapter 12.2)
     * @param settingsVersion how many times the rule set or the overrides changed; a change is only kept if it saw the latest one
     */
    record Game(UUID id, UUID serverId, UUID ownerId, String name, GameState state, String joinCode, String ruleSet, JsonNode settings, long settingsVersion,
                Instant createdAt) {
    }

    /** An admin of a game: its owner, or a co-admin the owner added. */
    record GameAdmin(UUID gameId, UUID userId, String role) {
    }

    /** A person in a game, or asking to be. */
    record Membership(UUID id, UUID gameId, UUID userId, String role, String status, Instant createdAt) {
    }

    /**
     * Raised when the server already has another active game (the unique index of "one server, one active game"). The database cannot be
     * asked anything more after this, so the exception does not say which game it is.
     */
    class ActiveGameExistsException extends RuntimeException {
        public ActiveGameExistsException(UUID serverId) {
            super("The server already has an active game" + (serverId == null ? "" : ": " + serverId));
        }
    }

    /** Raised when the join code is taken by another open lobby. */
    class JoinCodeTakenException extends RuntimeException {
        public JoinCodeTakenException(String code) {
            super("The join code is in use: " + code);
        }
    }

    Game create(Game game);

    Optional<Game> find(UUID id);

    /** The game whose lobby is open with this code. */
    Optional<Game> findByJoinCode(String joinCode);

    /** The server's active game (lobby, running or paused), if any. */
    Optional<Game> activeOnServer(UUID serverId);

    /** How many games (whatever their state) the server has. */
    long countOnServer(UUID serverId);

    /**
     * Moves the game from one state to another, and sets or clears the join code, but only if the game still is in {@code from}. Returns
     * false if it was not (someone else changed it first).
     *
     * @throws ActiveGameExistsException if the move would make a second game of the server active
     * @throws JoinCodeTakenException    if the code is in use
     */
    boolean transition(UUID id, GameState from, GameState to, String joinCode, Instant at);

    boolean rename(UUID id, String name);

    /**
     * Sets the game's rule set and its own setting overrides, but only if the settings still are at {@code expectedVersion}; the version
     * then goes up by one. Returns false if someone else changed them first.
     */
    boolean replaceSettings(UUID id, long expectedVersion, String ruleSet, JsonNode overrides, Instant at);

    /** How many games (whatever their state) use this saved rule set. */
    long countUsingRuleSet(UUID ruleSetId);

    /** Every game that is not archived. */
    List<Game> allNotArchived();

    /** The not archived games this person administers. */
    List<Game> administeredBy(UUID userId);

    /** The not archived games this person asked to join or belongs to (kicked and rejected ones are left out). */
    List<Game> joinedBy(UUID userId);

    /** The not archived games on the servers this person administers. */
    List<Game> onServersAdministeredBy(UUID userId);

    List<GameAdmin> adminsOf(UUID gameId);

    Optional<GameAdmin> adminOf(UUID gameId, UUID userId);

    /** Returns false if the person was an admin already. */
    boolean addAdmin(UUID gameId, UUID userId, String role);

    /** Removes a co-admin (never the owner). Returns false if the person was not a co-admin. */
    boolean removeCoAdmin(UUID gameId, UUID userId);

    /** Removes the person as co-admin of every game on this server (they stopped being a server admin). Returns how many. */
    int removeCoAdminsOnServer(UUID serverId, UUID userId);

    Optional<Membership> membership(UUID gameId, UUID userId);

    Optional<Membership> membershipById(UUID gameId, UUID membershipId);

    /** The memberships of a game with this status, oldest first. */
    List<Membership> memberships(UUID gameId, String status);

    /** Adds a membership. Returns false if the person has one in this game already. */
    boolean addMembership(Membership membership);

    /** Changes the status if it still is {@code from}. Returns false if it was not. */
    boolean setMembershipStatus(UUID membershipId, String from, String to);

    /** Changes the role of a membership (player or spectator). */
    void setMembershipRole(UUID membershipId, String role);
}
