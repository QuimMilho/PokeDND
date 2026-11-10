/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.TokenService;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository;
import net.quimmilho.pokednd.api.domain.games.GameRepository.Game;
import net.quimmilho.pokednd.api.games.GameService;
import net.quimmilho.pokednd.api.realtime.Protocol.Message;
import net.quimmilho.pokednd.api.realtime.Protocol.ProtocolException;
import net.quimmilho.pokednd.api.realtime.RealtimeHub.AppSession;
import org.jboss.logging.Logger;

/**
 * The conversation with the app on {@code /ws/app} (chapters 14.3, 14.6, 18.2, 18.3 and 18.4).
 *
 * <p>The first message must be {@code auth} with the access token. Then the app {@code subscribe}s to the games it wants to hear about; it
 * is told about a game only if it may see it. The connection lasts as long as the token does: the app sends {@code auth} again with its
 * refreshed token to keep it, or reconnects.
 */
@ApplicationScoped
public class AppProtocol {

    private static final Logger LOG = Logger.getLogger(AppProtocol.class);
    /** After the token has run out, the connection is kept this much longer, so that an app can send the refreshed token. */
    private static final int GRACE_SECONDS = 30;

    @Inject
    ObjectMapper json;

    @Inject
    RealtimeHub hub;

    @Inject
    TokenService tokens;

    @Inject
    GameService games;

    public void opened(WebSocketConnection connection) {
        hub.addApp(connection);
    }

    public void closed(WebSocketConnection connection) {
        hub.removeApp(connection.id());
    }

    /** One text message from the app. Never throws: a problem becomes an {@code error} message. */
    public void received(WebSocketConnection connection, String text) {
        AppSession session = hub.app(connection.id());
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
        Dedupe.Verdict verdict = session.dedupe.check(m.id());
        if (verdict.duplicate()) {
            if (verdict.earlierAnswer() != null) {
                hub.sendText(connection, verdict.earlierAnswer());
            }
            return;
        }
        ObjectNode reply;
        try {
            reply = handle(session, m);
        } catch (RuntimeException e) {
            LOG.error("Could not handle a message of the app", e);
            reply = Protocol.error(json, m.id(), m.gameId(), "INTERNAL_ERROR", "The API could not handle this message.");
        }
        try {
            String answer = json.writeValueAsString(reply);
            session.dedupe.answered(m.id(), answer);
            hub.sendText(connection, answer);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        if ("error".equals(reply.path("type").asText()) && "auth".equals(m.type())) {
            hub.close(connection, Protocol.CLOSE_UNAUTHENTICATED, "Login failed.");
        }
    }

    private ObjectNode handle(AppSession session, Message m) {
        if ("auth".equals(m.type())) {
            return onAuth(session, m);
        }
        if (session.userId == null) {
            return Protocol.error(json, m.id(), m.gameId(), "NOT_AUTHENTICATED", "Send \"auth\" with your access token first.");
        }
        return switch (m.type()) {
            case "ping" -> Protocol.envelope(json, "pong", m.id(), m.gameId(), json.createObjectNode());
            case "subscribe" -> onSubscribe(session, m);
            case "unsubscribe" -> onUnsubscribe(session, m);
            default -> Protocol.error(json, m.id(), m.gameId(), "UNKNOWN_TYPE", "The API does not know the message type \"" + m.type() + "\".");
        };
    }

    /** {@code auth}: the access token. Sent again with a new token it keeps the connection going; it must be the same person. */
    private ObjectNode onAuth(AppSession session, Message m) {
        String token = m.data().path("accessToken").isTextual() ? m.data().get("accessToken").asText() : "";
        Optional<TokenService.Claims> claims = tokens.verify(token);
        if (claims.isEmpty()) {
            return Protocol.error(json, m.id(), null, "INVALID_TOKEN", "The access token is invalid or has expired.");
        }
        TokenService.Claims c = claims.get();
        if (session.userId != null && !session.userId.equals(c.userId())) {
            return Protocol.error(json, m.id(), null, "WRONG_USER", "This connection belongs to another person. Open a new connection.");
        }
        session.userId = c.userId();
        session.siteAdmin = AccountRepository.SITE_ROLE_ADMIN.equals(c.siteRole());
        session.authExpiresAt = c.expiresAt().plusSeconds(GRACE_SECONDS);
        ObjectNode d = json.createObjectNode();
        d.put("userId", c.userId().toString());
        d.put("heartbeatSeconds", Protocol.HEARTBEAT_SECONDS);
        d.put("protocolVersion", Protocol.VERSION);
        d.put("expiresAt", c.expiresAt().toString());
        return Protocol.ack(json, m, d);
    }

    private ObjectNode onSubscribe(AppSession session, Message m) {
        UUID gameId = gameId(m);
        if (gameId == null) {
            return Protocol.error(json, m.id(), null, "INVALID_GAME", "\"subscribe\" needs the \"gameId\" of a game.");
        }
        Optional<Game> game = games.viewableBy(gameId, session.userId, session.siteAdmin);
        if (game.isEmpty()) {
            // the same answer for a game that does not exist and one the person may not see
            return Protocol.error(json, m.id(), gameId.toString(), "NOT_FOUND", "This game does not exist.");
        }
        Game g = game.get();
        session.games.put(g.id(), g.serverId());
        ObjectNode d = json.createObjectNode();
        d.put("gameId", g.id().toString());
        d.put("name", g.name());
        d.put("state", g.state().id());
        d.put("settingsVersion", g.settingsVersion());
        d.put("serverOnline", hub.isOnline(g.serverId()));
        if (games.runs(g, session.userId, session.siteAdmin) && g.joinCode() != null) {
            d.put("joinCode", g.joinCode());
        }
        return Protocol.ack(json, m, d);
    }

    private ObjectNode onUnsubscribe(AppSession session, Message m) {
        UUID gameId = gameId(m);
        if (gameId == null) {
            return Protocol.error(json, m.id(), null, "INVALID_GAME", "\"unsubscribe\" needs the \"gameId\" of a game.");
        }
        session.games.remove(gameId);
        return Protocol.ack(json, m, json.createObjectNode());
    }

    /** The game id of a message: in {@code gameId} of the envelope, or in {@code data.gameId}. */
    private static UUID gameId(Message m) {
        String text = m.gameId() != null ? m.gameId() : (m.data() instanceof JsonNode d && d.path("gameId").isTextual() ? d.get("gameId").asText() : null);
        try {
            return text == null ? null : UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
