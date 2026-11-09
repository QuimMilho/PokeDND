/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.servers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.Ids;
import net.quimmilho.pokednd.api.auth.TokenService;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository;
import net.quimmilho.pokednd.api.domain.games.GameRepository;
import net.quimmilho.pokednd.api.domain.servers.ServerRepository;
import net.quimmilho.pokednd.api.domain.servers.ServerRepository.Server;
import net.quimmilho.pokednd.api.domain.servers.ServerRepository.ServerNameTakenException;
import net.quimmilho.pokednd.api.error.ApiException;

/**
 * Registering Minecraft servers, their tokens and their admins (chapters 3.1, 14.6 and 16.2).
 *
 * <p>The server token is 256 bits of randomness, written as {@code pds_} and 43 URL-safe characters. It is shown once, when the server is
 * registered or the token is rotated; only its SHA-256 hash is stored. It belongs to one server, and a new token makes the old one fail.
 */
@ApplicationScoped
public class ServerService {

    /** Every server token starts with this, so a pasted token is easy to recognise (and a person's access token is easy to refuse). */
    public static final String TOKEN_MARK = "pds_";
    /** How many characters of the token are kept in the clear, to tell tokens apart in a list. */
    public static final int PREFIX_LENGTH = 8;
    public static final int NAME_MAX = 64;
    public static final int URL_MAX = 512;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** A server and its new token, which is shown only this once. */
    public record Created(Server server, String token) {
    }

    @Inject
    ServerRepository servers;

    @Inject
    AccountRepository accounts;

    @Inject
    GameRepository games;

    // ------------------------------------------------------------------ registering

    /**
     * Registers a server and makes its first token.
     *
     * @throws ApiException 400 INVALID_NAME or INVALID_URL; 409 SERVER_NAME_TAKEN
     */
    @Transactional
    public Created register(String name, String bluemapUrl) {
        String cleanName = cleanName(name);
        String cleanUrl = cleanUrl(bluemapUrl);
        String token = newToken();
        Server server = new Server(Ids.newId(), cleanName, TokenService.hash(token), prefixOf(token), cleanUrl, ServerRepository.OFFLINE, null, null, null,
                Instant.now());
        try {
            servers.create(server);
        } catch (ServerNameTakenException e) {
            throw nameTaken();
        }
        return new Created(server, token);
    }

    /** @throws ApiException 404 */
    public Server get(UUID id) {
        return servers.find(id).orElseThrow(() -> ApiException.notFound("This server"));
    }

    public List<Server> all() {
        return servers.all();
    }

    public List<Server> administeredBy(UUID userId) {
        return servers.administeredBy(userId);
    }

    /**
     * Changes the name and/or the BlueMap address. A null field stays as it is; an empty {@code bluemapUrl} removes the address.
     *
     * @throws ApiException 404; 400 INVALID_NAME or INVALID_URL; 409 SERVER_NAME_TAKEN
     */
    @Transactional
    public Server update(UUID id, String name, String bluemapUrl) {
        Server old = get(id);
        String newName = name == null ? old.name() : cleanName(name);
        String newUrl = bluemapUrl == null ? old.bluemapUrl() : cleanUrl(bluemapUrl);
        try {
            servers.update(id, newName, newUrl);
        } catch (ServerNameTakenException e) {
            throw nameTaken();
        }
        return get(id);
    }

    /**
     * Deletes a server that has no games. (A server with games keeps them: archive the games, or keep the server.)
     *
     * @throws ApiException 404; 409 SERVER_HAS_GAMES
     */
    @Transactional
    public void delete(UUID id) {
        get(id);
        if (games.countOnServer(id) > 0) {
            throw new ApiException(409, "SERVER_HAS_GAMES", "This server has games, so it cannot be deleted.");
        }
        servers.delete(id);
    }

    // ------------------------------------------------------------------ tokens

    /**
     * Gives the server a new token. The old one stops working at once.
     *
     * @throws ApiException 404
     */
    @Transactional
    public Created rotateToken(UUID id) {
        get(id);
        String token = newToken();
        servers.replaceToken(id, TokenService.hash(token), prefixOf(token));
        return new Created(get(id), token);
    }

    /** The server this token belongs to; empty for anything that is not the current token of a server. */
    public Optional<Server> authenticate(String token) {
        if (token == null || !token.startsWith(TOKEN_MARK) || token.length() > 200) {
            return Optional.empty();
        }
        return servers.findByTokenHash(TokenService.hash(token));
    }

    /** A new random token: 256 bits, so it cannot be guessed. */
    public static String newToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return TOKEN_MARK + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String prefixOf(String token) {
        return token.substring(0, PREFIX_LENGTH);
    }

    // ------------------------------------------------------------------ admins

    /** The accounts that administer the server. */
    public List<AccountRepository.User> adminsOf(UUID serverId) {
        return servers.adminsOf(serverId).stream().map(accounts::findUser).flatMap(Optional::stream).toList();
    }

    public boolean isAdmin(UUID serverId, UUID userId) {
        return servers.isAdmin(serverId, userId);
    }

    /**
     * Makes the person an admin of the server (nothing changes if they are one already).
     *
     * @throws ApiException 404 for the server or the person
     */
    @Transactional
    public void addAdmin(UUID serverId, UUID userId) {
        get(serverId);
        AccountRepository.User user = accounts.findUser(userId).filter(u -> u.deletedAt() == null).orElseThrow(() -> ApiException.notFound("This account"));
        servers.addAdmin(serverId, user.id());
    }

    /**
     * Takes the admin role away. The person also stops being a co-admin of games on this server, since only server admins may be.
     *
     * @throws ApiException 404 if the server does not exist or the person is not one of its admins
     */
    @Transactional
    public void removeAdmin(UUID serverId, UUID userId) {
        get(serverId);
        if (!servers.removeAdmin(serverId, userId)) {
            throw ApiException.notFound("This server admin");
        }
        games.removeCoAdminsOnServer(serverId, userId);
    }

    // ------------------------------------------------------------------ status

    /** The realtime channel (phase 40) calls this when the mod connects, says hello and sends heartbeats. */
    public void markOnline(UUID serverId, String modVersion, String mcVersion) {
        servers.recordStatus(serverId, ServerRepository.ONLINE, Instant.now(), modVersion, mcVersion);
    }

    /** The realtime channel calls this when the mod's connection ends. */
    public void markOffline(UUID serverId) {
        servers.recordStatus(serverId, ServerRepository.OFFLINE, Instant.now(), null, null);
    }

    /** At start nobody is connected, whatever the table said when the API stopped. */
    public int markAllOffline() {
        return servers.markAllOffline();
    }

    // ------------------------------------------------------------------ checking what people typed

    private static String cleanName(String name) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty() || n.length() > NAME_MAX || n.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("INVALID_NAME", "A server name has 1 to " + NAME_MAX + " characters.");
        }
        return n;
    }

    /** Null or empty means "none"; otherwise an http or https address. */
    private static String cleanUrl(String url) {
        String u = url == null ? "" : url.strip();
        if (u.isEmpty()) {
            return null;
        }
        try {
            URI uri = new URI(u);
            boolean web = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            if (!web || uri.getHost() == null || u.length() > URL_MAX) {
                throw new URISyntaxException(u, "not a web address");
            }
        } catch (URISyntaxException e) {
            throw ApiException.badRequest("INVALID_URL", "The BlueMap address must be an http or https address of up to " + URL_MAX + " characters.");
        }
        return u;
    }

    private static ApiException nameTaken() {
        return new ApiException(409, "SERVER_NAME_TAKEN", "Another server has this name.");
    }
}
