/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.quimmilho.pokednd.api.domain.games.GameRepository.Game;
import net.quimmilho.pokednd.api.domain.servers.ServerRepository.Server;
import net.quimmilho.pokednd.api.games.GameService;
import net.quimmilho.pokednd.api.realtime.Protocol.Message;
import net.quimmilho.pokednd.api.realtime.Protocol.ProtocolException;
import net.quimmilho.pokednd.api.realtime.RealtimeHub.ModSession;
import net.quimmilho.pokednd.api.servers.ServerService;
import org.jboss.logging.Logger;

/**
 * The conversation with a Minecraft mod on {@code /ws/mod} (chapters 14.3, 14.7, 18.2, 18.3 and 18.5).
 *
 * <p>The mod proves who it is with its server token (in the {@code Authorization} header of the upgrade request), says {@code hello},
 * and from then on the server shows as online. Every message has an id; one seen before is answered with the old answer and not handled
 * again. A mod that is not connected misses what happens meanwhile, and gets the state of the game again when it says hello.
 */
@ApplicationScoped
public class ModProtocol {

    private static final Logger LOG = Logger.getLogger(ModProtocol.class);
    /** The status in the database is touched at most this often by heartbeats. */
    private static final Duration STATUS_WRITE_EVERY = Duration.ofSeconds(60);

    /** Messages that tell the API a fact; the API notes them and answers with an ack. The rules that use them come with later phases. */
    private static final Set<String> FACTS = Set.of("trainer.spawned", "skin.applied", "walk.progress", "walk.arrived", "walk.blocked");
    /** Messages of 18.5 whose handling belongs to later phases; they are answered with an error, never silently dropped. */
    private static final Set<String> LATER = Set.of("player.joined", "player.left", "link.request", "admin.command", "battle.ready", "animation.done");
    private static final Set<String> CONFIG = Set.of("config.manifest", "config.changed", "config.applied");

    /** What handling a message produced: the reply, and what to do once the reply is on the wire (so it arrives after it). */
    private record Result(JsonNode reply, Runnable afterwards, boolean close, int closeCode, String closeReason) {
        static Result reply(JsonNode reply) {
            return new Result(reply, null, false, 0, null);
        }

        static Result replyThen(JsonNode reply, Runnable afterwards) {
            return new Result(reply, afterwards, false, 0, null);
        }

        static Result closing(JsonNode reply, int code, String reason) {
            return new Result(reply, null, true, code, reason);
        }
    }

    @Inject
    ObjectMapper json;

    @Inject
    RealtimeHub hub;

    @Inject
    ServerService servers;

    @Inject
    GameService games;

    @Inject
    ModCommands commands;

    // ------------------------------------------------------------------ the connection

    /** The server this {@code Authorization} header belongs to; empty for a missing, wrong or replaced token. */
    public Optional<Server> authenticate(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Optional.empty();
        }
        return servers.authenticate(header.substring(7).strip());
    }

    /** A mod opened the connection. The upgrade check let it in; the token is checked again here so that no path skips it. */
    public void opened(WebSocketConnection connection) {
        Optional<Server> server = authenticate(connection.handshakeRequest().header("Authorization"));
        if (server.isEmpty()) {
            hub.close(connection, Protocol.CLOSE_UNAUTHENTICATED, "The server token is missing, wrong or no longer valid.");
            return;
        }
        ModSession old = hub.addMod(server.get().id(), server.get().name(), connection);
        if (old != null) {
            // two mods for one server would give two sets of fake players: the newest connection wins
            hub.close(old.connection, Protocol.CLOSE_REPLACED, "Another connection for this server replaced this one.");
        }
        LOG.debugf("Mod of server %s connected", server.get().id());
    }

    /** The connection ended (by either side). */
    public void closed(WebSocketConnection connection) {
        ModSession session = hub.mod(connection.id());
        if (session == null) {
            return;
        }
        boolean current = hub.removeMod(session);
        if (current && session.ready) {
            servers.markOffline(session.serverId);
            ObjectNode d = json.createObjectNode();
            d.put("serverId", session.serverId.toString());
            d.put("online", false);
            hub.sendToServerGames(session.serverId, "server.status", d);
            LOG.debugf("Mod of server %s disconnected", session.serverId);
        }
    }

    // ------------------------------------------------------------------ messages

    /** One text message from the mod. Never throws: a problem becomes an {@code error} message. */
    public void received(WebSocketConnection connection, String text) {
        ModSession session = hub.mod(connection.id());
        if (session == null) {
            return;
        }
        session.lastSeen = Instant.now();
        switch (session.budget.take(System.currentTimeMillis())) {
            case REFUSE -> {
                hub.send(connection, Protocol.error(json, null, null, "RATE_LIMITED", "Too many messages. Slow down."));
                return;
            }
            case CLOSE -> {
                hub.send(connection, Protocol.error(json, null, null, "RATE_LIMITED", "Far too many messages. The connection is closed."));
                hub.close(connection, Protocol.CLOSE_RATE_LIMITED, "Too many messages.");
                return;
            }
            default -> {
            }
        }
        Message m;
        try {
            m = Protocol.parse(json, text);
        } catch (ProtocolException e) {
            hub.send(connection, Protocol.error(json, e.ref(), null, e.code(), e.getMessage()));
            if (e.fatal()) {
                hub.close(connection, Protocol.CLOSE_UNSUPPORTED_VERSION, "Unsupported protocol version.");
            }
            return;
        }
        // Replies to our commands are never answered (that would be an endless conversation), and are not worth remembering.
        boolean reply = "ack".equals(m.type()) || "error".equals(m.type()) || "pong".equals(m.type());
        Dedupe dedupe = hub.dedupeOfServer(session.serverId);
        if (!reply) {
            Dedupe.Verdict verdict = dedupe.check(m.id());
            if (verdict.duplicate()) {
                if (verdict.earlierAnswer() != null) {
                    hub.sendText(connection, verdict.earlierAnswer());
                }
                return;
            }
        }
        Result result;
        try {
            session.handled.incrementAndGet();
            result = handle(session, m);
        } catch (RuntimeException e) {
            LOG.error("Could not handle a message of the mod of server " + session.serverId, e);
            result = Result.reply(Protocol.error(json, m.id(), m.gameId(), "INTERNAL_ERROR", "The API could not handle this message."));
        }
        if (result.reply() != null) {
            try {
                String answer = json.writeValueAsString(result.reply());
                dedupe.answered(m.id(), answer);
                hub.sendText(connection, answer);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
        if (result.afterwards() != null) {
            result.afterwards().run();
        }
        if (result.close()) {
            hub.close(connection, result.closeCode(), result.closeReason());
        }
    }

    private Result handle(ModSession session, Message m) {
        switch (m.type()) {
            case "ack", "pong" -> {
                return Result.reply(null);
            }
            case "error" -> {
                return onError(session, m);
            }
            case "ping" -> {
                return onPing(session, m);
            }
            case "hello" -> {
                return onHello(session, m);
            }
            default -> {
            }
        }
        if (!session.ready) {
            return Result.reply(Protocol.error(json, m.id(), m.gameId(), "HELLO_REQUIRED", "Send \"hello\" first."));
        }
        if (CONFIG.contains(m.type())) {
            return onConfig(session, m);
        }
        if (FACTS.contains(m.type())) {
            return onFact(session, m);
        }
        if (LATER.contains(m.type())) {
            return Result.reply(Protocol.error(json, m.id(), m.gameId(), "NOT_SUPPORTED_YET", "The API does not handle \"" + m.type() + "\" yet."));
        }
        return Result.reply(Protocol.error(json, m.id(), m.gameId(), "UNKNOWN_TYPE", "The API does not know the message type \"" + m.type() + "\"."));
    }

    // ------------------------------------------------------------------ hello

    private Result onHello(ModSession session, Message m) {
        JsonNode d = m.data();
        int theirs = d.path("protocolVersion").isInt() ? d.get("protocolVersion").asInt() : m.v();
        if (!Protocol.SUPPORTED.contains(theirs)) {
            String why = "This API speaks protocol version " + Protocol.VERSION + "; the mod speaks version " + theirs
                    + ". Update the mod (or the API) so that both use the same version.";
            return Result.closing(Protocol.error(json, m.id(), null, "UNSUPPORTED_PROTOCOL", why), Protocol.CLOSE_UNSUPPORTED_VERSION,
                    "Unsupported protocol version " + theirs + ".");
        }
        session.modVersion = text(d, "modVersion");
        session.minecraftVersion = text(d, "minecraftVersion");
        session.stateHash = text(d, "stateHash");
        Set<String> features = new HashSet<>();
        d.path("features").forEach(f -> features.add(f.asText()));
        session.features = Set.copyOf(features);
        Set<String> players = new HashSet<>();
        d.path("players").forEach(p -> players.add(p.asText()));
        session.players = Set.copyOf(players);
        boolean first = !session.ready;
        session.ready = true;
        session.lastStatusWrite = Instant.now();
        servers.markOnline(session.serverId, session.modVersion, session.minecraftVersion);

        Optional<Game> game = games.activeOnServer(session.serverId);
        ObjectNode ack = json.createObjectNode();
        ack.put("serverId", session.serverId.toString());
        ack.put("serverName", session.serverName);
        ack.put("protocolVersion", Protocol.VERSION);
        ack.put("heartbeatSeconds", Protocol.HEARTBEAT_SECONDS);
        ArrayNode versions = ack.putArray("supportedVersions");
        Protocol.SUPPORTED.stream().sorted().forEach(versions::add);
        game.ifPresent(g -> {
            ObjectNode gd = ack.putObject("game");
            gd.put("id", g.id().toString());
            gd.put("name", g.name());
            gd.put("state", g.state().id());
            if (g.state().lobbyOpen() && g.joinCode() != null) {
                gd.put("lobbyCode", g.joinCode());
            }
        });
        ObjectNode helloAck = Protocol.envelope(json, "hello.ack", m.id(), game.map(g -> g.id().toString()).orElse(null), ack);
        return Result.replyThen(helloAck, () -> {
            game.ifPresent(g -> commands.catchUp(g, session.players));
            if (first) {
                ObjectNode status = json.createObjectNode();
                status.put("serverId", session.serverId.toString());
                status.put("online", true);
                hub.sendToServerGames(session.serverId, "server.status", status);
            }
        });
    }

    // ------------------------------------------------------------------ the rest

    private Result onPing(ModSession session, Message m) {
        Instant now = Instant.now();
        if (session.ready && Duration.between(session.lastStatusWrite, now).compareTo(STATUS_WRITE_EVERY) >= 0) {
            session.lastStatusWrite = now;
            servers.markOnline(session.serverId, session.modVersion, session.minecraftVersion);
        }
        return Result.reply(Protocol.envelope(json, "pong", m.id(), m.gameId(), json.createObjectNode()));
    }

    /** The mod reports a problem the admin should see: it goes to the admins who watch the game, as a notification. */
    private Result onError(ModSession session, Message m) {
        LOG.warnf("The mod of server %s reports an error: %s %s", session.serverId, m.data().path("code").asText(), m.data().path("message").asText());
        games.activeOnServer(session.serverId).ifPresent(g -> {
            ObjectNode d = json.createObjectNode();
            d.put("level", "error");
            d.put("source", "mod");
            d.put("code", m.data().path("code").asText("MOD_ERROR"));
            d.put("message", m.data().path("message").asText(""));
            hub.sendToGame(g.id(), s -> games.runs(g, s.userId, s.siteAdmin), "notification", d);
        });
        return Result.reply(null);
    }

    /** World-file news (15.7). The API keeps the last manifest; the comparison with the stored copy comes with the world configuration (phase 65). */
    private Result onConfig(ModSession session, Message m) {
        if ("config.manifest".equals(m.type())) {
            session.manifest = m.data().deepCopy();
        }
        return Result.reply(Protocol.ack(json, m, json.createObjectNode()));
    }

    private Result onFact(ModSession session, Message m) {
        if ("trainer.spawned".equals(m.type()) && m.data().path("trainerId").isTextual()) {
            session.spawned.put(m.data().get("trainerId").asText(), m.data().path("uuid").asText(""));
        }
        return Result.reply(Protocol.ack(json, m, json.createObjectNode()));
    }

    private static String text(JsonNode d, String field) {
        return d.path(field).isTextual() ? d.get(field).asText() : null;
    }
}
