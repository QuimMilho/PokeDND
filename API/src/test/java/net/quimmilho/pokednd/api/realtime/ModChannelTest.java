/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.realtime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.quimmilho.pokednd.api.TestAccounts;
import net.quimmilho.pokednd.api.TestAccounts.Login;
import net.quimmilho.pokednd.api.auth.RateLimiter;
import net.quimmilho.pokednd.api.TestWorld;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The mod's channel {@code /ws/mod} (phase 40): who may connect, hello and the protocol version, presence, the commands the API sends
 * (lobby, spawn), reconnection, and repeated messages. The "mod" here is a raw WebSocket client; {@link FakeModIntegrationTest} uses the
 * real fake mod program.
 */
@QuarkusTest
@Tag("database")
class ModChannelTest {

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

    /** Connects with the server's token and says hello; returns the connection after hello.ack. */
    private static WsClient connectedMod(Table t, Object... helloData) {
        WsClient mod = WsClient.connect("/ws/mod", t.modBearer());
        mod.send("hello", concat(new Object[] {"protocolVersion", 1, "modVersion", "test 1", "minecraftVersion", "1.21.1", "features", List.of("fakePlayers")},
                helloData));
        mod.waitFor("hello.ack");
        return mod;
    }

    private static Object[] concat(Object[] a, Object[] b) {
        Object[] out = new Object[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static void eventually(BooleanSupplier condition, String what) {
        long end = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("Did not happen within ten seconds: " + what);
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static String status(Table t) {
        return TestWorld.get(t.owner(), "servers/" + t.server().id() + "/status").path("status");
    }

    // ---------------------------------------------------------------- who may connect

    @Test
    void aBadOrMissingTokenIsRefusedBeforeTheConnectionOpens() {
        Table t = Table.draft();
        assertEquals(401, assertThrows(WsClient.Refused.class, () -> WsClient.connect("/ws/mod", null)).status);
        assertEquals(401, assertThrows(WsClient.Refused.class, () -> WsClient.connect("/ws/mod", "Bearer pds_notatoken")).status);
        assertEquals(401, assertThrows(WsClient.Refused.class, () -> WsClient.connect("/ws/mod", "Basic " + t.server().token())).status);
        // a person's access token is not a server token
        Login admin = TestAccounts.admin();
        assertEquals(401, assertThrows(WsClient.Refused.class, () -> WsClient.connect("/ws/mod", admin.bearer())).status);
        assertEquals("offline", status(t));
    }

    @Test
    void aRotatedTokenStopsWorkingAtOnce() {
        Table t = Table.draft();
        String old = t.modBearer();
        WsClient.connect("/ws/mod", old).close();
        given().header("Authorization", TestAccounts.admin().bearer()).post("/api/v1/servers/" + t.server().id() + "/token/rotate").then().statusCode(200);
        assertEquals(401, assertThrows(WsClient.Refused.class, () -> WsClient.connect("/ws/mod", old)).status);
    }

    // ---------------------------------------------------------------- hello and presence

    @Test
    void helloIsAnsweredAndTheServerShowsOnlineUntilTheConnectionEnds() {
        Table t = Table.draft();
        assertEquals("offline", status(t));
        WsClient mod = WsClient.connect("/ws/mod", t.modBearer());
        String helloId = mod.send("hello", "protocolVersion", 1, "modVersion", "test 1", "minecraftVersion", "1.21.1", "features", List.of("fakePlayers"));
        JsonNode ack = mod.waitFor("hello.ack");
        assertEquals(helloId, ack.path("ref").asText(), "the answer points at the hello");
        assertEquals(1, ack.path("v").asInt());
        assertEquals(t.server().id(), data(ack).path("serverId").asText());
        assertEquals(1, data(ack).path("protocolVersion").asInt());
        assertEquals(20, data(ack).path("heartbeatSeconds").asInt());
        assertTrue(data(ack).path("game").isMissingNode(), "no active game yet");
        assertEquals("online", status(t));
        TestWorld.get(t.owner(), "servers/" + t.server().id() + "/status").then().body("modVersion", equalTo("test 1")).body("mcVersion", equalTo("1.21.1"));
        assertTrue(hub.isOnline(t.serverId()));

        mod.close();
        eventually(() -> "offline".equals(status(t)), "the server shows offline after the mod disconnected");
        assertFalse(hub.isOnline(t.serverId()));
    }

    @Test
    void anUnknownProtocolVersionIsRefusedWithAClearReason() {
        Table t = Table.draft();
        // in the hello
        WsClient mod = WsClient.connect("/ws/mod", t.modBearer());
        mod.send("hello", "protocolVersion", 7, "modVersion", "test 1", "minecraftVersion", "1.21.1");
        JsonNode error = mod.waitFor("error");
        assertEquals("UNSUPPORTED_PROTOCOL", data(error).path("code").asText());
        assertTrue(data(error).path("message").asText().contains("version 1") && data(error).path("message").asText().contains("version 7"), data(error).toString());
        assertEquals(Protocol.CLOSE_UNSUPPORTED_VERSION, mod.awaitClose());
        assertEquals("offline", status(t));

        // in the envelope of any message
        WsClient other = WsClient.connect("/ws/mod", t.modBearer());
        other.send(2, "hello", null);
        JsonNode error2 = other.waitFor("error");
        assertEquals("UNSUPPORTED_VERSION", data(error2).path("code").asText());
        assertEquals(Protocol.CLOSE_UNSUPPORTED_VERSION, other.awaitClose());
    }

    @Test
    void messagesBeforeHelloAndBadMessagesGetErrors() {
        Table t = Table.draft();
        WsClient mod = WsClient.connect("/ws/mod", t.modBearer());
        String id = mod.send("config.manifest", "version", 1);
        JsonNode early = mod.waitFor("error");
        assertEquals("HELLO_REQUIRED", data(early).path("code").asText());
        assertEquals(id, early.path("ref").asText());

        mod.sendRaw("this is not json");
        assertEquals("BAD_MESSAGE", data(mod.waitFor("error")).path("code").asText());
        mod.sendRaw("{\"v\":1,\"id\":\"x1\"}");
        assertEquals("BAD_MESSAGE", data(mod.waitFor("error")).path("code").asText());
        mod.sendRaw("[1,2]");
        assertEquals("BAD_MESSAGE", data(mod.waitFor("error")).path("code").asText());

        mod.send("hello", "protocolVersion", 1, "modVersion", "t", "minecraftVersion", "1.21.1");
        mod.waitFor("hello.ack");
        mod.send("no.such.message");
        assertEquals("UNKNOWN_TYPE", data(mod.waitFor("error")).path("code").asText());
        mod.send("player.joined", "mcUuid", UUID.randomUUID().toString());
        assertEquals("NOT_SUPPORTED_YET", data(mod.waitFor("error")).path("code").asText());
        assertFalse(mod.isClosed(), "a bad message does not end the connection");
        mod.close();
    }

    @Test
    void heartbeatIsAnsweredAndConfigSyncIsAcknowledged() {
        Table t = Table.draft();
        WsClient mod = connectedMod(t);
        String ping = mod.send("ping");
        JsonNode pong = mod.waitFor("pong");
        assertEquals(ping, pong.path("ref").asText());

        String id = mod.send("config.manifest", "version", 3, "files", java.util.Map.of("regions/a.json", "abc123"));
        JsonNode ack = mod.waitFor("ack");
        assertEquals(id, ack.path("ref").asText());
        assertEquals(3, hub.readyMod(t.serverId()).manifest.path("version").asInt(), "the API keeps the last manifest");
        String applied = mod.send("config.applied", "version", 4, "files", List.of("regions/a.json"));
        assertEquals(applied, mod.waitFor("ack").path("ref").asText());
        mod.close();
    }

    @Test
    void aNewConnectionReplacesTheOldOneOfTheSameServer() {
        Table t = Table.draft();
        WsClient first = connectedMod(t);
        WsClient second = connectedMod(t);
        assertEquals(Protocol.CLOSE_REPLACED, first.awaitClose());
        assertEquals("online", status(t), "the server stays online: the new connection is the mod");
        assertTrue(hub.isOnline(t.serverId()));
        second.close();
        eventually(() -> "offline".equals(status(t)), "offline after the last connection ended");
    }

    @Test
    void aModThatNeverSaysHelloIsCutOff() {
        Table t = Table.draft();
        WsClient mod = WsClient.connect("/ws/mod", t.modBearer());
        // the connection is registered a moment after the handshake, so the sweep is repeated until it finds it
        long end = System.currentTimeMillis() + 5000;
        while (hub.sweep(Instant.now().plusSeconds(60)) == 0 && System.currentTimeMillis() < end) {
            Thread.yield();
        }
        assertEquals(Protocol.CLOSE_UNAUTHENTICATED, mod.awaitClose());
        assertEquals("offline", status(t));
    }

    @Test
    void aSilentModIsCutOffAndShowsOffline() {
        Table t = Table.draft();
        WsClient mod = connectedMod(t);
        assertEquals("online", status(t));
        hub.sweep(Instant.now().plusSeconds(600));
        assertEquals(Protocol.CLOSE_IDLE, mod.awaitClose());
        eventually(() -> "offline".equals(status(t)), "offline after the idle close");
    }

    @Test
    void tooManyMessagesAreRefusedAndTheConnectionIsCutOff() {
        Table t = Table.draft();
        WsClient mod = connectedMod(t);
        for (int i = 0; i < 700; i++) {
            try {
                mod.send("ping");
            } catch (IllegalStateException e) {
                break; // the server has closed the connection
            }
        }
        assertEquals("RATE_LIMITED", data(mod.waitFor("error")).path("code").asText());
        assertEquals(Protocol.CLOSE_RATE_LIMITED, mod.awaitClose());
    }

    // ---------------------------------------------------------------- the commands the API sends

    @Test
    void theLobbyAndTheGameStateReachTheMod() {
        Table t = Table.draft();
        WsClient mod = connectedMod(t);
        t.command("lobby/open");
        JsonNode state = mod.waitFor("game.state");
        assertEquals("lobby", data(state).path("state").asText());
        assertEquals(t.game(), state.path("gameId").asText());
        JsonNode open = mod.waitFor("lobby.open");
        assertEquals(t.joinCode(), data(open).path("code").asText());

        t.command("start");
        assertEquals("running", data(mod.waitFor("game.state")).path("state").asText());
        mod.waitFor("lobby.close");
        t.command("pause");
        assertEquals("paused", data(mod.waitFor("game.state")).path("state").asText());
        t.command("end");
        assertEquals("ended", data(mod.waitFor("game.state")).path("state").asText());
        mod.close();
    }

    @Test
    void aTrainerIsSpawnedUpdatedAndRemoved() {
        Table t = Table.lobby();
        WsClient mod = connectedMod(t);
        Login ana = t.player();
        String name = Table.uniqueName();
        String trainerId = t.trainer(ana, name);

        JsonNode spawn = mod.waitFor("trainer.spawn");
        assertEquals(trainerId, data(spawn).path("trainerId").asText());
        assertEquals(name, data(spawn).path("name").asText());
        assertEquals(ana.id(), data(spawn).path("userId").asText());
        assertEquals(t.game(), spawn.path("gameId").asText());

        String confirmed = mod.send("trainer.spawned", "trainerId", trainerId, "uuid", UUID.randomUUID().toString());
        assertEquals(confirmed, mod.waitFor("ack").path("ref").asText());
        eventually(() -> hub.readyMod(t.serverId()).spawned.containsKey(trainerId), "the API notes the confirmed fake player");

        given().header("Authorization", ana.bearer()).delete("/api/v1/trainers/" + trainerId).then().statusCode(204);
        assertEquals(trainerId, data(mod.waitFor("trainer.remove")).path("trainerId").asText());
        mod.close();
    }

    // ---------------------------------------------------------------- reconnection (14.7)

    @Test
    void aReconnectingModIsBroughtUpToDate() {
        Table t = Table.lobby();
        Login ana = t.player();
        String trainerId = t.trainer(ana, Table.uniqueName());

        // the first connection: hello.ack already tells the game, then the catch-up follows
        WsClient mod = WsClient.connect("/ws/mod", t.modBearer());
        mod.send("hello", "protocolVersion", 1, "modVersion", "t", "minecraftVersion", "1.21.1");
        JsonNode ack = mod.waitFor("hello.ack");
        assertEquals(t.game(), data(ack).path("game").path("id").asText());
        assertEquals("lobby", data(ack).path("game").path("state").asText());
        assertEquals(t.joinCode(), data(ack).path("game").path("lobbyCode").asText());
        assertEquals("lobby", data(mod.waitFor("game.state")).path("state").asText());
        assertEquals(t.joinCode(), data(mod.waitFor("lobby.open")).path("code").asText());
        assertEquals(trainerId, data(mod.waitFor("trainer.spawn")).path("trainerId").asText());
        mod.close();
        eventually(() -> "offline".equals(status(t)), "offline");

        // the pause happens while the mod is away; nothing is queued for it
        t.command("start");
        t.command("pause");

        // a short drop: the mod still has its fake player, so it is not spawned again, but the new state arrives
        WsClient back = WsClient.connect("/ws/mod", t.modBearer());
        back.send("hello", "protocolVersion", 1, "modVersion", "t", "minecraftVersion", "1.21.1", "players", List.of(trainerId));
        assertEquals("paused", data(back.waitFor("hello.ack")).path("game").path("state").asText());
        assertEquals("paused", data(back.waitFor("game.state")).path("state").asText());
        back.waitFor("lobby.close");
        back.send("ping");
        back.waitFor("pong");
        assertFalse(back.types().contains("trainer.spawn"), "the fake player is still there, so nothing is spawned: " + back.types());
        back.close();
        eventually(() -> "offline".equals(status(t)), "offline again");

        // a restart of the Minecraft server: the mod has nothing, so the fake player is made again
        WsClient fresh = WsClient.connect("/ws/mod", t.modBearer());
        fresh.send("hello", "protocolVersion", 1, "modVersion", "t", "minecraftVersion", "1.21.1", "players", List.of());
        fresh.waitFor("hello.ack");
        assertEquals(trainerId, data(fresh.waitFor("trainer.spawn")).path("trainerId").asText());
        fresh.close();
    }

    @Test
    void aCommandForAnOfflineModIsNotKeptAndNothingBreaks() {
        Table t = Table.lobby();
        assertFalse(hub.sendToMod(t.serverId(), "game.state", UUID.fromString(t.game()), WsClient.JSON.createObjectNode()));
        Login ana = t.player();
        t.trainer(ana, Table.uniqueName());
        t.command("start");
        assertEquals("running", TestWorld.get(t.owner(), "games/" + t.game()).path("state"));
    }

    // ---------------------------------------------------------------- repeated and ordered messages

    @Test
    void aRepeatedMessageIsHandledOnceAndGetsTheSameAnswerAgain() {
        Table t = Table.draft();
        WsClient mod = connectedMod(t);
        long before = hub.readyMod(t.serverId()).handled.get();

        String id = mod.send("config.manifest", "version", 9, "files", java.util.Map.of());
        JsonNode first = mod.waitFor("ack");
        // the answer was lost on the way, so the mod sends the same message again
        mod.sendWithId(id, "config.manifest", "version", 9, "files", java.util.Map.of());
        JsonNode again = mod.waitFor("ack");
        assertEquals(first, again, "the same answer, with the same id");
        mod.sendWithId(id, "config.manifest", "version", 9, "files", java.util.Map.of());
        mod.waitFor("ack");
        mod.send("ping");
        mod.waitFor("pong");
        assertEquals(2, hub.readyMod(t.serverId()).handled.get() - before, "the manifest was handled once, plus the ping");
        mod.close();
    }

    @Test
    void aRepeatedHelloIsAnsweredAgainWithoutBeingHandledTwice() {
        Table t = Table.draft();
        WsClient mod = WsClient.connect("/ws/mod", t.modBearer());
        String id = mod.send("hello", "protocolVersion", 1, "modVersion", "t", "minecraftVersion", "1.21.1");
        JsonNode first = mod.waitFor("hello.ack");
        mod.sendWithId(id, "hello", "protocolVersion", 1, "modVersion", "t", "minecraftVersion", "1.21.1");
        assertEquals(first, mod.waitFor("hello.ack"));
        mod.close();
    }

    @Test
    void theIdsSurviveAReconnectOfTheSameServer() {
        Table t = Table.draft();
        WsClient mod = connectedMod(t);
        String id = mod.send("trainer.spawned", "trainerId", "ana", "uuid", "u-1");
        JsonNode first = mod.waitFor("ack");
        mod.close();
        eventually(() -> !hub.isOnline(t.serverId()), "gone");

        WsClient back = connectedMod(t);
        long before = hub.readyMod(t.serverId()).handled.get();
        back.sendWithId(id, "trainer.spawned", "trainerId", "ana", "uuid", "u-1");
        assertEquals(first, back.waitFor("ack"), "the old answer is sent again");
        assertEquals(before, hub.readyMod(t.serverId()).handled.get(), "and the message is not handled again");
        back.close();
    }

    @Test
    void messagesAreAnsweredInTheOrderTheyWereSent() {
        Table t = Table.draft();
        WsClient mod = connectedMod(t);
        mod.clear();
        List<String> sent = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            sent.add(mod.send("config.changed", "files", List.of("f" + i)));
        }
        List<String> answered = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            answered.add(mod.waitFor("ack").path("ref").asText());
        }
        assertEquals(sent, answered);
        assertNotNull(mod.all);
        mod.close();
    }
}
