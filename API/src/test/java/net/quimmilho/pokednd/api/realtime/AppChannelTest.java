/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.realtime;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.quimmilho.pokednd.api.TestAccounts;
import net.quimmilho.pokednd.api.TestAccounts.Login;
import net.quimmilho.pokednd.api.auth.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The app's channel {@code /ws/app} (phase 40): logging in with the access token, listening to a game only if the person may see it,
 * what each kind of person is told, and the rules for repeated messages and too many messages.
 */
@QuarkusTest
@Tag("database")
class AppChannelTest {

    @Inject
    RealtimeHub hub;

    @Inject
    RateLimiter limiter;

    @BeforeEach
    void freshLimits() {
        limiter.reset();
    }

    private static JsonNode data(JsonNode message) {
        return message.path("data");
    }

    /** Opens a connection, logs in and returns it. */
    private static WsClient app(Login who) {
        WsClient app = WsClient.connect("/ws/app", null);
        String id = app.send("auth", "accessToken", who.accessToken());
        JsonNode ack = app.waitFor("ack");
        assertEquals(id, ack.path("ref").asText());
        assertEquals(who.id(), data(ack).path("userId").asText());
        return app;
    }

    /** Logs in and listens to the game; returns the connection. */
    private static WsClient listening(Login who, String game) {
        WsClient app = app(who);
        app.send("subscribe", "gameId", game);
        app.waitFor("ack");
        return app;
    }

    // ---------------------------------------------------------------- logging in

    @Test
    void nothingIsAnsweredBeforeAuth() {
        WsClient app = WsClient.connect("/ws/app", null);
        String id = app.send("subscribe", "gameId", UUID.randomUUID().toString());
        JsonNode error = app.waitFor("error");
        assertEquals("NOT_AUTHENTICATED", data(error).path("code").asText());
        assertEquals(id, error.path("ref").asText());
        app.send("ping");
        assertEquals("NOT_AUTHENTICATED", data(app.waitFor("error")).path("code").asText());
        assertFalse(app.isClosed(), "the connection waits for the auth message");
        app.close();
    }

    @Test
    void aBadTokenIsRefusedAndTheConnectionEnds() {
        WsClient app = WsClient.connect("/ws/app", null);
        app.send("auth", "accessToken", "not.a.token");
        assertEquals("INVALID_TOKEN", data(app.waitFor("error")).path("code").asText());
        assertEquals(Protocol.CLOSE_UNAUTHENTICATED, app.awaitClose());

        // a server token is not an access token
        Table t = Table.draft();
        WsClient other = WsClient.connect("/ws/app", null);
        other.send("auth", "accessToken", t.server().token());
        assertEquals("INVALID_TOKEN", data(other.waitFor("error")).path("code").asText());
        assertEquals(Protocol.CLOSE_UNAUTHENTICATED, other.awaitClose());

        // no token at all
        WsClient empty = WsClient.connect("/ws/app", null);
        empty.send("auth");
        assertEquals("INVALID_TOKEN", data(empty.waitFor("error")).path("code").asText());
        assertEquals(Protocol.CLOSE_UNAUTHENTICATED, empty.awaitClose());
    }

    @Test
    void aConnectionThatNeverLogsInIsCutOff() {
        WsClient app = WsClient.connect("/ws/app", null);
        // the connection is registered a moment after the handshake, so the sweep is repeated until it finds it
        long end = System.currentTimeMillis() + 5000;
        while (hub.sweep(Instant.now().plusSeconds(60)) == 0 && System.currentTimeMillis() < end) {
            Thread.yield();
        }
        assertEquals(Protocol.CLOSE_UNAUTHENTICATED, app.awaitClose());
    }

    @Test
    void theConnectionLastsAsLongAsTheTokenAndAFreshTokenKeepsIt() {
        Login who = TestAccounts.newUser();
        WsClient app = app(who);
        RealtimeHub.AppSession session = hub.sessionsOf(UUID.fromString(who.id())).get(0);
        // the access token lasts 15 minutes; the connection lasts as long (and a little more)
        Instant first = session.authExpiresAt;
        assertTrue(first.isAfter(Instant.now().plusSeconds(14 * 60)), first.toString());
        hub.sweep(Instant.now().plusSeconds(30));
        app.send("ping");
        app.waitFor("pong");
        assertFalse(app.isClosed(), "the token is still good");

        // the app sends its refreshed token before the old one runs out
        Login again = TestAccounts.login(who.email());
        app.send("auth", "accessToken", again.accessToken());
        app.waitFor("ack");
        assertFalse(session.authExpiresAt.isBefore(first), "a fresh token does not shorten the connection");

        // a token that has run out ends the connection
        session.authExpiresAt = Instant.now().minusSeconds(1);
        hub.sweep(Instant.now());
        assertEquals(Protocol.CLOSE_UNAUTHENTICATED, app.awaitClose());
        assertEquals("The access token has expired. Log in again.", app.closeReason);
    }

    @Test
    void aConnectionCannotChangeToAnotherPerson() {
        WsClient app = app(TestAccounts.newUser());
        app.send("auth", "accessToken", TestAccounts.newUser().accessToken());
        assertEquals("WRONG_USER", data(app.waitFor("error")).path("code").asText());
        assertEquals(Protocol.CLOSE_UNAUTHENTICATED, app.awaitClose());
    }

    @Test
    void anUnknownProtocolVersionIsRefused() {
        WsClient app = WsClient.connect("/ws/app", null);
        app.send(2, "auth", null, "accessToken", "x");
        assertEquals("UNSUPPORTED_VERSION", data(app.waitFor("error")).path("code").asText());
        assertEquals(Protocol.CLOSE_UNSUPPORTED_VERSION, app.awaitClose());
    }

    @Test
    void badMessagesGetErrors() {
        WsClient app = app(TestAccounts.newUser());
        app.sendRaw("nonsense");
        assertEquals("BAD_MESSAGE", data(app.waitFor("error")).path("code").asText());
        app.send("roll.submit", "rollId", "x");
        assertEquals("UNKNOWN_TYPE", data(app.waitFor("error")).path("code").asText());
        app.send("subscribe", "gameId", "not-a-uuid");
        assertEquals("INVALID_GAME", data(app.waitFor("error")).path("code").asText());
        app.send("subscribe");
        assertEquals("INVALID_GAME", data(app.waitFor("error")).path("code").asText());
        assertFalse(app.isClosed());
        app.close();
    }

    @Test
    void tooManyMessagesAreRefusedAndTheConnectionIsCutOff() {
        WsClient app = app(TestAccounts.newUser());
        for (int i = 0; i < 100; i++) {
            try {
                app.send("ping");
            } catch (IllegalStateException e) {
                break;
            }
        }
        assertEquals("RATE_LIMITED", data(app.waitFor("error")).path("code").asText());
        assertEquals(Protocol.CLOSE_RATE_LIMITED, app.awaitClose());
    }

    // ---------------------------------------------------------------- who may listen to what

    @Test
    void aPersonHearsOnlyAboutGamesTheyMaySee() {
        Table t = Table.lobby();
        Login member = t.player();
        Login stranger = TestAccounts.newUser();

        WsClient strangerApp = app(stranger);
        strangerApp.send("subscribe", "gameId", t.game());
        JsonNode refused = strangerApp.waitFor("error");
        assertEquals("NOT_FOUND", data(refused).path("code").asText());
        String missing = UUID.randomUUID().toString();
        strangerApp.send("subscribe", "gameId", missing);
        assertEquals(data(refused).path("message").asText(), data(strangerApp.waitFor("error")).path("message").asText(),
                "a game that does not exist and a game that is not theirs look the same");

        // something happens in the game; the stranger hears nothing, the member does
        WsClient memberApp = listening(member, t.game());
        t.command("start");
        assertEquals("running", data(memberApp.waitFor("game.state")).path("state").asText());
        strangerApp.expectNothing(300);
        strangerApp.close();
        memberApp.close();
    }

    @Test
    void eventsOfAnotherGameNeverArrive() {
        Table mine = Table.lobby();
        Table other = Table.lobby();
        Login me = mine.player();
        WsClient app = listening(me, mine.game());
        app.send("subscribe", "gameId", other.game());
        assertEquals("NOT_FOUND", data(app.waitFor("error")).path("code").asText());

        Login stranger = other.player();
        other.command("start");
        other.setting("game.lobby.codeLength", 8);
        other.trainer(stranger, Table.uniqueName());
        app.expectNothing(400);
        app.close();
    }

    @Test
    void theSiteAdminMayListenToAnyGame() {
        Table t = Table.lobby();
        WsClient app = listening(TestAccounts.admin(), t.game());
        t.command("start");
        assertEquals("running", data(app.waitFor("game.state")).path("state").asText());
        app.close();
    }

    @Test
    void onlyAdminsHearTheJoinCodeAndTheRequests() {
        Table t = Table.draft();
        Login player = TestAccounts.newUser();
        WsClient adminApp = listening(t.owner(), t.game());
        t.command("lobby/open");

        JsonNode adminState = adminApp.waitFor("game.state");
        assertEquals("lobby", data(adminState).path("state").asText());
        assertEquals("draft", data(adminState).path("previous").asText());
        assertEquals(t.joinCode(), data(adminState).path("joinCode").asText());

        // the player asks to join: the admin is told, with who and what
        String request = t.askToJoin(player, null);
        JsonNode asked = adminApp.waitFor("membership.request");
        assertEquals(player.id(), data(asked).path("userId").asText());
        assertEquals("requested", data(asked).path("status").asText());
        assertEquals(request, data(asked).path("membershipId").asText());

        // the player is not in the game yet: they cannot listen, but they hear the answer to their own request
        WsClient playerApp = app(player);
        playerApp.send("subscribe", "gameId", t.game());
        assertEquals("NOT_FOUND", data(playerApp.waitFor("error")).path("code").asText());
        t.accept(request);
        JsonNode answer = playerApp.waitFor("membership.update");
        assertEquals("accepted", data(answer).path("status").asText());
        assertEquals(t.game(), answer.path("gameId").asText());
        assertEquals("accepted", data(adminApp.waitFor("membership.update")).path("status").asText());

        // now they may listen, and they hear the state without the join code
        playerApp.send("subscribe", "gameId", t.game());
        JsonNode subscribed = playerApp.waitFor("ack");
        assertTrue(data(subscribed).path("joinCode").isMissingNode(), "the join code is for admins");
        assertEquals("lobby", data(subscribed).path("state").asText());
        t.command("start");
        JsonNode playerState = playerApp.waitFor("game.state");
        assertEquals("running", data(playerState).path("state").asText());
        assertTrue(data(playerState).path("joinCode").isMissingNode());
        adminApp.close();
        playerApp.close();
    }

    @Test
    void aMemberWhoIsRemovedStopsHearingAboutTheGame() {
        Table t = Table.lobby();
        Login member = t.player();
        WsClient app = listening(member, t.game());
        WsClient adminApp = listening(t.owner(), t.game());

        t.kick(member);
        JsonNode update = app.waitFor("membership.update");
        assertEquals("kicked", data(update).path("status").asText());
        adminApp.waitFor("membership.update");

        t.command("start");
        adminApp.waitFor("game.state");
        app.expectNothing(400);
        app.send("subscribe", "gameId", t.game());
        assertEquals("NOT_FOUND", data(app.waitFor("error")).path("code").asText());
        app.close();
        adminApp.close();
    }

    @Test
    void unsubscribingStopsTheMessages() {
        Table t = Table.lobby();
        WsClient app = listening(t.owner(), t.game());
        app.send("unsubscribe", "gameId", t.game());
        app.waitFor("ack");
        t.command("start");
        app.expectNothing(400);
        app.close();
    }

    // ---------------------------------------------------------------- what they are told

    @Test
    void settingsAndTrainerChangesAreAnnounced() {
        Table t = Table.lobby();
        Login ana = t.player();
        WsClient app = listening(ana, t.game());

        t.setting("game.lobby.codeLength", 8);
        JsonNode settings = app.waitFor("game.settings");
        assertTrue(data(settings).path("version").asLong() >= 1, data(settings).toString());

        String trainerId = t.trainer(ana, Table.uniqueName());
        JsonNode created = app.waitFor("trainer.update");
        assertEquals(trainerId, data(created).path("trainerId").asText());
        assertEquals("created", data(created).path("change").asText());
        assertFalse(data(created).has("abilityScores"), "the sheet itself is fetched with REST, where the permissions apply");

        given().header("Authorization", ana.bearer()).delete("/api/v1/trainers/" + trainerId).then().statusCode(204);
        assertEquals("removed", data(app.waitFor("trainer.update")).path("change").asText());
        app.close();
    }

    @Test
    void thePresenceOfTheServerIsAnnounced() {
        Table t = Table.lobby();
        WsClient app = listening(t.owner(), t.game());
        // the answer to subscribe already says whether the mod is there
        app.send("subscribe", "gameId", t.game());
        assertFalse(data(app.waitFor("ack")).path("serverOnline").asBoolean());

        WsClient mod = WsClient.connect("/ws/mod", t.modBearer());
        mod.send("hello", "protocolVersion", 1, "modVersion", "t", "minecraftVersion", "1.21.1");
        mod.waitFor("hello.ack");
        JsonNode online = app.waitFor("server.status");
        assertTrue(data(online).path("online").asBoolean());
        assertEquals(t.server().id(), data(online).path("serverId").asText());

        app.send("subscribe", "gameId", t.game());
        assertTrue(data(app.waitFor("ack")).path("serverOnline").asBoolean());

        mod.close();
        assertFalse(data(app.waitFor("server.status")).path("online").asBoolean());
        app.close();
    }

    @Test
    void aProblemReportedByTheModReachesTheAdminsOnly() {
        Table t = Table.lobby();
        Login player = t.player();
        WsClient adminApp = listening(t.owner(), t.game());
        WsClient playerApp = listening(player, t.game());
        WsClient mod = WsClient.connect("/ws/mod", t.modBearer());
        mod.send("hello", "protocolVersion", 1, "modVersion", "t", "minecraftVersion", "1.21.1");
        mod.waitFor("hello.ack");

        mod.send("error", "code", "SKIN_FAILED", "message", "The skin could not be applied.");
        JsonNode note = adminApp.waitFor("notification");
        assertEquals("SKIN_FAILED", data(note).path("code").asText());
        assertEquals("mod", data(note).path("source").asText());
        playerApp.waitFor("server.status");
        playerApp.expectNothing(300);
        mod.close();
        adminApp.close();
        playerApp.close();
    }

    // ---------------------------------------------------------------- repeated and ordered messages

    @Test
    void aRepeatedMessageGetsTheSameAnswerAndIsHandledOnce() {
        Table t = Table.lobby();
        WsClient app = app(t.owner());
        String id = app.send("subscribe", "gameId", t.game());
        JsonNode first = app.waitFor("ack");
        app.sendWithId(id, "subscribe", "gameId", t.game());
        assertEquals(first, app.waitFor("ack"));
        assertEquals(1, hub.listeners(UUID.fromString(t.game())).size(), "one subscription, not two");
        app.close();
    }

    @Test
    void answersComeInTheOrderOfTheQuestions() {
        WsClient app = app(TestAccounts.newUser());
        app.clear();
        List<String> sent = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            sent.add(app.send("ping"));
        }
        List<String> answered = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            answered.add(app.waitFor("pong").path("ref").asText());
        }
        assertEquals(sent, answered);
        app.close();
    }
}
