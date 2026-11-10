/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.websockets.next.CloseReason;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Who is connected, and the way to send to them (chapter 14.3). It knows the live connections of the mods (one per server) and of the
 * apps (many per person), and nothing else: the rules of what to send, and to whom, are in {@link ModProtocol}, {@link AppProtocol} and
 * {@link RealtimeEvents}.
 *
 * <p>The connections live in memory of this process. The state that must survive is in the database; after a restart every peer
 * reconnects and is brought up to date (chapter 14.7).
 */
@ApplicationScoped
public class RealtimeHub {

    private static final Logger LOG = Logger.getLogger(RealtimeHub.class);

    /** A mod connected to the API. */
    public static final class ModSession {
        public final UUID serverId;
        public final String serverName;
        public final WebSocketConnection connection;
        public final Instant connectedAt = Instant.now();
        public final MessageBudget budget;
        /** True once a valid hello was received. */
        public volatile boolean ready;
        public volatile Instant lastSeen = Instant.now();
        public volatile Instant lastStatusWrite = Instant.EPOCH;
        public volatile String modVersion;
        public volatile String minecraftVersion;
        public volatile String stateHash;
        public volatile Set<String> features = Set.of();
        /** The trainers the mod says it has a fake player for (from its hello). */
        public volatile Set<String> players = Set.of();
        /** The last world-file manifest the mod sent (chapter 15.7), or null. */
        public volatile JsonNode manifest;
        /** Trainers whose fake player the mod confirmed, trainer id to the Minecraft uuid. */
        public final Map<String, String> spawned = new ConcurrentHashMap<>();
        /** How many messages were handled (duplicates are not counted). */
        public final AtomicLong handled = new AtomicLong();

        ModSession(UUID serverId, String serverName, WebSocketConnection connection, int perSecond) {
            this.serverId = serverId;
            this.serverName = serverName;
            this.connection = connection;
            this.budget = new MessageBudget(perSecond);
        }
    }

    /** An app (a browser) connected to the API. */
    public static final class AppSession {
        public final WebSocketConnection connection;
        public final Instant connectedAt = Instant.now();
        public final MessageBudget budget;
        public final Dedupe dedupe;
        public volatile UUID userId;
        public volatile boolean siteAdmin;
        /** When the access token it logged in with stops being valid; the connection ends then unless it logs in again. */
        public volatile Instant authExpiresAt;
        public volatile Instant lastSeen = Instant.now();
        /** The games it listens to, game id to the id of the game's server. */
        public final Map<UUID, UUID> games = new ConcurrentHashMap<>();

        AppSession(WebSocketConnection connection, int perSecond, int dedupeSize) {
            this.connection = connection;
            this.budget = new MessageBudget(perSecond);
            this.dedupe = new Dedupe(dedupeSize);
        }
    }

    @Inject
    ObjectMapper json;

    @ConfigProperty(name = "pokednd.realtime.mod-messages-per-second", defaultValue = "200")
    int modPerSecond;

    @ConfigProperty(name = "pokednd.realtime.app-messages-per-second", defaultValue = "20")
    int appPerSecond;

    @ConfigProperty(name = "pokednd.realtime.dedupe-size", defaultValue = "1000")
    int dedupeSize;

    @ConfigProperty(name = "pokednd.realtime.hello-timeout-seconds", defaultValue = "10")
    int helloTimeoutSeconds;

    @ConfigProperty(name = "pokednd.realtime.auth-timeout-seconds", defaultValue = "10")
    int authTimeoutSeconds;

    /** A peer that sent nothing for this long (three heartbeats and a little more) is gone. */
    @ConfigProperty(name = "pokednd.realtime.idle-timeout-seconds", defaultValue = "70")
    int idleTimeoutSeconds;

    private final Map<UUID, ModSession> mods = new ConcurrentHashMap<>();
    private final Map<String, ModSession> modsByConnection = new ConcurrentHashMap<>();
    /** The ids a server's mod sent, kept across reconnects so that a command repeated after a drop is not run twice. */
    private final Map<UUID, Dedupe> modDedupe = new ConcurrentHashMap<>();
    private final Map<String, AppSession> apps = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ mods

    /** Registers a new mod connection. Returns the connection of the same server that it replaces, if any (the caller closes it). */
    ModSession addMod(UUID serverId, String name, WebSocketConnection connection) {
        ModSession session = new ModSession(serverId, name, connection, modPerSecond);
        modsByConnection.put(connection.id(), session);
        return mods.put(serverId, session);
    }

    ModSession mod(String connectionId) {
        return modsByConnection.get(connectionId);
    }

    /** Forgets the connection. True if it was the current connection of its server (and not one that a newer connection replaced). */
    boolean removeMod(ModSession session) {
        modsByConnection.remove(session.connection.id());
        return mods.remove(session.serverId, session);
    }

    Dedupe dedupeOfServer(UUID serverId) {
        return modDedupe.computeIfAbsent(serverId, id -> new Dedupe(dedupeSize));
    }

    /** The mod that is connected for this server and has said hello. */
    public ModSession readyMod(UUID serverId) {
        ModSession s = mods.get(serverId);
        return s != null && s.ready ? s : null;
    }

    /** Is this server's mod connected and ready? */
    public boolean isOnline(UUID serverId) {
        return readyMod(serverId) != null;
    }

    /**
     * Sends a command to the server's mod. Nothing is kept for later: a mod that is not connected gets the current state when it says hello
     * again (chapter 14.7).
     *
     * @return false if no mod is connected for this server
     */
    public boolean sendToMod(UUID serverId, String type, UUID gameId, JsonNode data) {
        ModSession s = readyMod(serverId);
        if (s == null) {
            return false;
        }
        return send(s.connection, Protocol.envelope(json, type, null, gameId == null ? null : gameId.toString(), data));
    }

    // ------------------------------------------------------------------ apps

    AppSession addApp(WebSocketConnection connection) {
        AppSession s = new AppSession(connection, appPerSecond, dedupeSize);
        apps.put(connection.id(), s);
        return s;
    }

    AppSession app(String connectionId) {
        return apps.get(connectionId);
    }

    void removeApp(String connectionId) {
        apps.remove(connectionId);
    }

    /** Sends to the connections of one person. */
    public int sendToUser(UUID userId, String type, UUID gameId, JsonNode data) {
        return sendToApps(s -> userId.equals(s.userId), type, gameId, data);
    }

    /** Sends to the apps that listen to this game and pass the filter. */
    public int sendToGame(UUID gameId, Predicate<AppSession> filter, String type, JsonNode data) {
        return sendToApps(s -> s.userId != null && s.games.containsKey(gameId) && filter.test(s), type, gameId, data);
    }

    /** Sends to the apps that listen to any game of this server. */
    public int sendToServerGames(UUID serverId, String type, JsonNode data) {
        return sendToApps(s -> s.userId != null && s.games.containsValue(serverId), type, null, data);
    }

    private int sendToApps(Predicate<AppSession> filter, String type, UUID gameId, JsonNode data) {
        int sent = 0;
        for (AppSession s : List.copyOf(apps.values())) {
            if (filter.test(s) && send(s.connection, Protocol.envelope(json, type, null, gameId == null ? null : gameId.toString(), data))) {
                sent++;
            }
        }
        return sent;
    }

    /** Stops listening to a game on every connection of this person (they were removed from it). */
    public void unsubscribeUser(UUID userId, UUID gameId) {
        apps.values().stream().filter(s -> userId.equals(s.userId)).forEach(s -> s.games.remove(gameId));
    }

    /** The connections of one person. */
    public List<AppSession> sessionsOf(UUID userId) {
        return apps.values().stream().filter(s -> userId.equals(s.userId)).toList();
    }

    /** The apps that listen to a game (for tests and for the status). */
    public List<AppSession> listeners(UUID gameId) {
        return apps.values().stream().filter(s -> s.games.containsKey(gameId)).toList();
    }

    // ------------------------------------------------------------------ sending and closing

    /** Writes one message. False when the connection is closed or the write failed; never throws. */
    boolean send(WebSocketConnection connection, JsonNode message) {
        try {
            if (connection.isClosed()) {
                return false;
            }
            connection.sendTextAndAwait(json.writeValueAsString(message));
            return true;
        } catch (Exception e) {
            LOG.debugf("Could not write to connection %s: %s", connection.id(), e.toString());
            return false;
        }
    }

    boolean sendText(WebSocketConnection connection, String text) {
        try {
            if (connection.isClosed()) {
                return false;
            }
            connection.sendTextAndAwait(text);
            return true;
        } catch (Exception e) {
            LOG.debugf("Could not write to connection %s: %s", connection.id(), e.toString());
            return false;
        }
    }

    ObjectNode object() {
        return json.createObjectNode();
    }

    /** Closes a connection with a reason of up to 123 bytes; never throws. */
    void close(WebSocketConnection connection, int code, String reason) {
        try {
            if (!connection.isClosed()) {
                connection.closeAndAwait(new CloseReason(code, reason));
            }
        } catch (Exception e) {
            LOG.debugf("Could not close connection %s: %s", connection.id(), e.toString());
        }
    }

    // ------------------------------------------------------------------ housekeeping

    /** Ends the connections that never said hello or logged in, whose token ran out, or that went silent. */
    @Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void sweep() {
        sweep(Instant.now());
    }

    /** The same at a chosen moment (the tests move the clock forward). Returns how many connections were closed. */
    public int sweep(Instant now) {
        List<Runnable> closing = new ArrayList<>();
        for (ModSession s : mods.values()) {
            if (!s.ready && now.isAfter(s.connectedAt.plusSeconds(helloTimeoutSeconds))) {
                closing.add(() -> close(s.connection, Protocol.CLOSE_UNAUTHENTICATED, "No hello within " + helloTimeoutSeconds + " seconds."));
            } else if (now.isAfter(s.lastSeen.plusSeconds(idleTimeoutSeconds))) {
                closing.add(() -> close(s.connection, Protocol.CLOSE_IDLE, "No message for " + idleTimeoutSeconds + " seconds."));
            }
        }
        for (AppSession s : apps.values()) {
            if (s.userId == null && now.isAfter(s.connectedAt.plus(Duration.ofSeconds(authTimeoutSeconds)))) {
                closing.add(() -> close(s.connection, Protocol.CLOSE_UNAUTHENTICATED, "No auth within " + authTimeoutSeconds + " seconds."));
            } else if (s.userId != null && s.authExpiresAt != null && now.isAfter(s.authExpiresAt)) {
                closing.add(() -> close(s.connection, Protocol.CLOSE_UNAUTHENTICATED, "The access token has expired. Log in again."));
            } else if (now.isAfter(s.lastSeen.plusSeconds(idleTimeoutSeconds))) {
                closing.add(() -> close(s.connection, Protocol.CLOSE_IDLE, "No message for " + idleTimeoutSeconds + " seconds."));
            }
        }
        closing.forEach(Runnable::run);
        return closing.size();
    }
}
