/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.domain.servers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where registered Minecraft servers and their admins are kept (chapter 17.2). The SQL adapter implements it. */
public interface ServerRepository {

    String ONLINE = "online";
    String OFFLINE = "offline";

    /**
     * A registered server.
     *
     * @param tokenHash   SHA-256 of the server token (64 hex characters); the token itself is never stored
     * @param tokenPrefix the first characters of the token, to tell tokens apart
     */
    record Server(UUID id, String name, String tokenHash, String tokenPrefix, String bluemapUrl, String status, Instant lastSeenAt,
                  String modVersion, String mcVersion, Instant createdAt) {
    }

    /** Raised when another server has this name (capitals do not matter). */
    class ServerNameTakenException extends RuntimeException {
        public ServerNameTakenException(String name) {
            super("A server with this name exists already: " + name);
        }
    }

    /** @throws ServerNameTakenException if the name is taken */
    Server create(Server server);

    Optional<Server> find(UUID id);

    Optional<Server> findByTokenHash(String tokenHash);

    /** Every server, by name. */
    List<Server> all();

    /** The servers this person administers, by name. */
    List<Server> administeredBy(UUID userId);

    /**
     * Changes the name and the BlueMap address. Returns false if there is no such server.
     *
     * @throws ServerNameTakenException if another server has the name
     */
    boolean update(UUID id, String name, String bluemapUrl);

    /** Gives the server a new token (its hash and prefix). The old token stops working at once. Returns false if there is no such server. */
    boolean replaceToken(UUID id, String tokenHash, String tokenPrefix);

    /** Deletes the server and its admin list. Returns false if there is no such server. */
    boolean delete(UUID id);

    /** The ids of the server's admins, oldest first. */
    List<UUID> adminsOf(UUID serverId);

    /** Returns false if the person was an admin already. */
    boolean addAdmin(UUID serverId, UUID userId);

    /** Returns false if the person was not an admin. */
    boolean removeAdmin(UUID serverId, UUID userId);

    boolean isAdmin(UUID serverId, UUID userId);

    /** Stores the online state, when the server was last heard from and the versions it reported (null keeps the old value). */
    void recordStatus(UUID id, String status, Instant seenAt, String modVersion, String mcVersion);

    /** Marks every server offline (when the API starts, nobody is connected). Returns how many changed. */
    int markAllOffline();
}
