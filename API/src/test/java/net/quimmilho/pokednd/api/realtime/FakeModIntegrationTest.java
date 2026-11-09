/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.quimmilho.pokednd.api.TestAccounts.Login;
import net.quimmilho.pokednd.api.TestWorld;
import net.quimmilho.pokednd.api.auth.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The real fake mod ({@code tools/fake-mod}, a Node program) against the real API: hello, config sync, the lobby, a spawn, a walk and
 * its arrival, then a drop and a reconnect (phase 40). The scenario of each test is a JSON file that the fake mod runs; it ends with exit
 * code 0 only if every step happened. The tests are skipped when Node.js 22 or later is not installed.
 */
@QuarkusTest
@Tag("database")
class FakeModIntegrationTest {

    private static final Path FAKE_MOD = Path.of("..", "tools", "fake-mod", "fake-mod.mjs").toAbsolutePath().normalize();

    @Inject
    RealtimeHub hub;

    @Inject
    RateLimiter limiter;

    @BeforeEach
    void freshLimits() {
        limiter.reset();
    }

    /** A running fake mod: its output is collected, and it can be waited for. */
    private static final class Running {
        final Process process;
        final StringBuffer output = new StringBuffer();

        Running(Process process) {
            this.process = process;
            Thread reader = new Thread(() -> {
                try (var in = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        output.append(line).append('\n');
                    }
                } catch (IOException ignored) {
                    // the process ended
                }
            });
            reader.setDaemon(true);
            reader.start();
        }

        int finish() throws InterruptedException {
            if (!process.waitFor(40, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("The fake mod did not finish. Output so far:\n" + output);
            }
            Thread.sleep(100);
            return process.exitValue();
        }
    }

    private static void requireNode() {
        assumeTrue(Files.exists(FAKE_MOD), "tools/fake-mod is not next to the API: " + FAKE_MOD);
        try {
            Process p = new ProcessBuilder("node", "-e", "process.exit(Number(process.versions.node.split('.')[0]) >= 22 ? 0 : 3)").redirectErrorStream(true).start();
            assumeTrue(p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0, "Node.js 22 or later is needed to run the fake mod");
        } catch (IOException | InterruptedException e) {
            assumeTrue(false, "Node.js is not installed, so the fake mod cannot run");
        }
    }

    private static Running start(Table t, String scenario) throws IOException {
        Path file = Files.createTempFile("fake-mod-scenario", ".json");
        file.toFile().deleteOnExit();
        Files.writeString(file, scenario, StandardCharsets.UTF_8);
        Process p = new ProcessBuilder("node", FAKE_MOD.toString(), "--scenario", file.toString(), "--url", WsClient.url("/ws/mod"), "--token", t.server().token())
                .redirectErrorStream(true).start();
        return new Running(p);
    }

    private void waitUntilOnline(Table t) throws InterruptedException {
        long end = System.currentTimeMillis() + 15_000;
        while (!hub.isOnline(t.serverId())) {
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("The fake mod did not come online");
            }
            Thread.sleep(25);
        }
    }

    @Test
    void helloLobbySpawnWalkAndArrival() throws Exception {
        requireNode();
        Table t = Table.draft();
        Running run = start(t, """
                {
                  "options": { "walkDelayMs": 100 },
                  "steps": [
                    { "waitFor": "game.state", "data": { "state": "lobby" }, "timeoutMs": 20000 },
                    { "waitFor": "lobby.open", "timeoutMs": 20000 },
                    { "expect": { "lobby.open": true, "gameState": "lobby", "serverId": "SERVER" } },
                    { "waitFor": "trainer.spawn", "timeoutMs": 20000 },
                    { "waitFor": "trainer.spawned", "dir": "out" },
                    { "waitFor": "party.walk", "timeoutMs": 20000 },
                    { "waitFor": "walk.progress", "dir": "out" },
                    { "waitFor": "walk.arrived", "dir": "out", "data": { "position": { "x": 10, "y": 64, "z": 5 } } },
                    { "waitFor": "game.state", "data": { "state": "running" }, "timeoutMs": 20000 },
                    { "waitFor": "lobby.close", "timeoutMs": 20000 },
                    { "expect": { "lobby.open": false, "gameState": "running" } }
                  ]
                }
                """.replace("SERVER", t.server().id()));
        waitUntilOnline(t);

        // config sync: the fake mod answers its hello.ack with the manifest of its world files, and the API keeps it
        long manifestEnd = System.currentTimeMillis() + 15_000;
        while (hub.readyMod(t.serverId()).manifest == null && System.currentTimeMillis() < manifestEnd) {
            Thread.sleep(25);
        }
        assertNotNull(hub.readyMod(t.serverId()).manifest, "the API kept the manifest of the fake mod\n" + run.output);
        assertTrue(hub.readyMod(t.serverId()).manifest.path("files").isObject(), "the manifest lists the world files by hash");

        t.command("lobby/open");
        String code = t.joinCode();
        Login ana = t.player();
        String trainerId = t.trainer(ana, Table.uniqueName());

        // the API learns that the fake player exists (the mod confirms the spawn)
        long end = System.currentTimeMillis() + 15_000;
        while (!hub.readyMod(t.serverId()).spawned.containsKey(trainerId) && System.currentTimeMillis() < end) {
            Thread.sleep(25);
        }
        assertTrue(hub.readyMod(t.serverId()).spawned.containsKey(trainerId), "the API learned that the fake player exists\n" + run.output);

        // a walk command (the overworld phases decide when; here the hub sends one directly), and the arrival is reported
        ObjectNode walk = WsClient.JSON.createObjectNode();
        walk.putArray("trainerIds").add(trainerId);
        walk.putObject("to").put("x", 10).put("y", 64).put("z", 5);
        walk.put("walkId", "w1");
        assertTrue(hub.sendToMod(t.serverId(), "party.walk", UUID.fromString(t.game()), walk));
        Thread.sleep(1000); // longer than the walk takes, so the arrival is logged before the game state below

        t.command("start");
        assertEquals(0, run.finish(), "the scenario failed. Output:\n" + run.output);
        assertTrue(run.output.toString().contains("JOIN CODE: " + code), "the fake mod showed the join code " + code + "\n" + run.output);
    }

    @Test
    void aFakeModThatReconnectsGetsItsTrainersAgain() throws Exception {
        requireNode();
        Table t = Table.lobby();
        Login ana = t.player();
        String name = Table.uniqueName();
        String trainerId = t.trainer(ana, name);

        // the first run: hello, then the catch-up (state, join code, a spawn), then it goes away
        Running first = start(t, """
                {
                  "steps": [
                    { "waitFor": "game.state", "data": { "state": "lobby" } },
                    { "waitFor": "lobby.open" },
                    { "waitFor": "trainer.spawn" },
                    { "waitFor": "trainer.spawned", "dir": "out" },
                    { "close": true }
                  ]
                }
                """);
        assertEquals(0, first.finish(), first.output.toString());
        long end = System.currentTimeMillis() + 10_000;
        while (hub.isOnline(t.serverId()) && System.currentTimeMillis() < end) {
            Thread.sleep(25);
        }
        assertEquals("offline", TestWorld.get(t.owner(), "servers/" + t.server().id() + "/status").path("status"));

        // the second run is a mod that lost everything (the Minecraft server restarted): it is told the same again
        Running second = start(t, """
                {
                  "steps": [
                    { "waitFor": "game.state", "data": { "state": "lobby" } },
                    { "waitFor": "lobby.open" },
                    { "waitFor": "trainer.spawn", "data": { "trainerId": "ID" } },
                    { "waitFor": "trainer.spawned", "dir": "out" },
                    { "expect": { "players.ID.name": "NAME" } },
                    { "close": true }
                  ]
                }
                """.replace("ID", trainerId).replace("NAME", name));
        assertEquals(0, second.finish(), second.output.toString());
    }

    @Test
    void aFakeModWithTheWrongTokenFailsWithAClearMessage() throws Exception {
        requireNode();
        Table t = Table.draft();
        Process p = new ProcessBuilder("node", FAKE_MOD.toString(), "--url", WsClient.url("/ws/mod"), "--token", "pds_wrong").redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(30, TimeUnit.SECONDS));
        assertTrue(p.exitValue() != 0, out);
        assertTrue(out.contains("Could not connect"), out);
        assertEquals("offline", TestWorld.get(t.owner(), "servers/" + t.server().id() + "/status").path("status"));
    }
}
