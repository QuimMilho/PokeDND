/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.games;

import static io.restassured.RestAssured.given;
import static net.quimmilho.pokednd.api.TestWorld.get;
import static net.quimmilho.pokednd.api.TestWorld.post;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.quimmilho.pokednd.api.TestAccounts;
import net.quimmilho.pokednd.api.TestAccounts.Login;
import net.quimmilho.pokednd.api.TestWorld;
import net.quimmilho.pokednd.api.TestWorld.Server;
import net.quimmilho.pokednd.api.auth.RateLimiter;
import net.quimmilho.pokednd.api.minecraft.MinecraftLinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Games: creating them, their states, the join code, requests, members and co-admins (phase 32). Runs against a real PostgreSQL. */
@QuarkusTest
@Tag("database")
class GamesTest {

    @Inject
    RateLimiter limiter;

    @BeforeEach
    void freshLimits() {
        limiter.reset();
    }

    private static Response join(Login who, String code) {
        return given().header("Authorization", who.bearer()).contentType(ContentType.JSON).body(Map.of("code", code)).post("/api/v1/games/join");
    }

    private static String state(Login who, String gameId) {
        return get(who, "games/" + gameId).path("state");
    }

    /** A game in the lobby: returns its id; the join code is read with {@link #code}. */
    private static String lobby(Login owner, Server server) {
        String game = TestWorld.newGame(owner, server);
        post(owner, game, "lobby/open").then().statusCode(200);
        return game;
    }

    private static String code(Login admin, String gameId) {
        return get(admin, "games/" + gameId).path("joinCode");
    }

    // ---------------------------------------------------------------- creating

    @Test
    void onlyASiteAdminOrAnAdminOfThatServerCreatesGames() {
        Server server = TestWorld.newServer();
        Server other = TestWorld.newServer();
        Map<String, String> body = Map.of("serverId", server.id(), "name", "Kanto run");

        given().contentType(ContentType.JSON).body(body).post("/api/v1/games").then().statusCode(401);
        // an ordinary player, and a server admin of another server
        given().header("Authorization", TestAccounts.newUser().bearer()).contentType(ContentType.JSON).body(body).post("/api/v1/games").then().statusCode(403);
        given().header("Authorization", TestWorld.newServerAdmin(other).bearer()).contentType(ContentType.JSON).body(body).post("/api/v1/games").then()
                .statusCode(403);

        Login owner = TestWorld.newServerAdmin(server);
        given().header("Authorization", owner.bearer()).contentType(ContentType.JSON).body(body).post("/api/v1/games").then().statusCode(201)
                .body("name", equalTo("Kanto run")).body("state", equalTo("draft")).body("lobbyOpen", equalTo(false)).body("ownerId", equalTo(owner.id()))
                .body("serverId", equalTo(server.id())).body("joinCode", nullValue()).body("admins.size()", equalTo(1))
                .body("admins[0].id", equalTo(owner.id())).body("admins[0].role", equalTo("owner")).body("myRoles", hasItem("game_admin"));

        // the site admin may create a game on any server
        given().header("Authorization", TestAccounts.admin().bearer()).contentType(ContentType.JSON).body(body).post("/api/v1/games").then().statusCode(201);
    }

    @Test
    void aGameNeedsAnExistingServerAndAGoodName() {
        Login admin = TestAccounts.admin();
        given().header("Authorization", admin.bearer()).contentType(ContentType.JSON).body(Map.of("serverId", UUID.randomUUID().toString(), "name", "x"))
                .post("/api/v1/games").then().statusCode(404);
        given().header("Authorization", admin.bearer()).contentType(ContentType.JSON).body(Map.of("serverId", "nonsense", "name", "x")).post("/api/v1/games")
                .then().statusCode(404);
        Server server = TestWorld.newServer();
        given().header("Authorization", admin.bearer()).contentType(ContentType.JSON).body(Map.of("serverId", server.id(), "name", " ")).post("/api/v1/games")
                .then().statusCode(400).body("code", equalTo("INVALID_NAME"));
        given().header("Authorization", admin.bearer()).contentType(ContentType.JSON).body(Map.of("serverId", server.id(), "name", "x".repeat(81)))
                .post("/api/v1/games").then().statusCode(400).body("code", equalTo("INVALID_NAME"));
        given().header("Authorization", admin.bearer()).contentType(ContentType.JSON).post("/api/v1/games").then().statusCode(400);
    }

    @Test
    void aGameCanBeRenamedUntilItIsArchived() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String game = TestWorld.newGame(owner, server);
        given().header("Authorization", owner.bearer()).contentType(ContentType.JSON).body(Map.of("name", "New name")).patch("/api/v1/games/" + game).then()
                .statusCode(200).body("name", equalTo("New name"));
        given().header("Authorization", owner.bearer()).contentType(ContentType.JSON).body(Map.of("name", "")).patch("/api/v1/games/" + game).then()
                .statusCode(400);
        given().header("Authorization", TestAccounts.newUser().bearer()).contentType(ContentType.JSON).body(Map.of("name", "Hijack")).patch("/api/v1/games/" + game)
                .then().statusCode(404);
        given().header("Authorization", owner.bearer()).delete("/api/v1/games/" + game).then().statusCode(200).body("state", equalTo("archived"));
        given().header("Authorization", owner.bearer()).contentType(ContentType.JSON).body(Map.of("name", "Late")).patch("/api/v1/games/" + game).then()
                .statusCode(409).body("code", equalTo("INVALID_TRANSITION"));
    }

    // ---------------------------------------------------------------- the states, through the endpoints

    @Test
    void aGameWalksTheWholeDiagram() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String game = TestWorld.newGame(owner, server);
        assertEquals("draft", state(owner, game));

        post(owner, game, "lobby/open").then().statusCode(200).body("state", equalTo("lobby")).body("lobbyOpen", equalTo(true));
        post(owner, game, "start").then().statusCode(200).body("state", equalTo("running")).body("lobbyOpen", equalTo(false)).body("joinCode", nullValue());
        post(owner, game, "pause").then().statusCode(200).body("state", equalTo("paused"));
        post(owner, game, "resume").then().statusCode(200).body("state", equalTo("running"));
        // late join: reopen the lobby while the game runs, then close it again
        post(owner, game, "lobby/open").then().statusCode(200).body("state", equalTo("lobby"));
        post(owner, game, "lobby/close").then().statusCode(200).body("state", equalTo("running"));
        post(owner, game, "pause").then().statusCode(200);
        post(owner, game, "end").then().statusCode(200).body("state", equalTo("ended"));
        given().header("Authorization", owner.bearer()).delete("/api/v1/games/" + game).then().statusCode(200).body("state", equalTo("archived"));
    }

    @Test
    void closingTheLobbyOfANewGameStartsItToo() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String game = lobby(owner, server);
        post(owner, game, "lobby/close").then().statusCode(200).body("state", equalTo("running"));
    }

    @Test
    void aDraftCanBeDiscardedAndAnEndedGameArchived() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String draft = TestWorld.newGame(owner, server);
        given().header("Authorization", owner.bearer()).delete("/api/v1/games/" + draft).then().statusCode(200).body("state", equalTo("archived"));
        // archived games leave the list, but the owner can still read them
        get(owner, "games").then().body("id", not(hasItem(draft)));
        get(owner, "games/" + draft).then().statusCode(200).body("state", equalTo("archived"));
    }

    @Test
    void forbiddenTransitionsAreRefusedWithTheStateAndTheCommand() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String game = TestWorld.newGame(owner, server);

        // a draft cannot start, close a lobby it never opened, pause, resume or end
        for (String command : List.of("start", "lobby/close", "pause", "resume", "end")) {
            post(owner, game, command).then().statusCode(409).body("code", equalTo("INVALID_TRANSITION")).body("details.state", equalTo("draft"));
        }
        assertEquals("draft", state(owner, game));

        post(owner, game, "lobby/open").then().statusCode(200);
        // a lobby cannot open again, pause, resume or end
        for (String command : List.of("lobby/open", "pause", "resume", "end")) {
            post(owner, game, command).then().statusCode(409).body("code", equalTo("INVALID_TRANSITION")).body("details.state", equalTo("lobby"));
        }
        given().header("Authorization", owner.bearer()).delete("/api/v1/games/" + game).then().statusCode(409);

        post(owner, game, "start").then().statusCode(200);
        for (String command : List.of("start", "lobby/close", "resume")) {
            post(owner, game, command).then().statusCode(409).body("details.state", equalTo("running"));
        }
        given().header("Authorization", owner.bearer()).delete("/api/v1/games/" + game).then().statusCode(409);

        post(owner, game, "pause").then().statusCode(200);
        for (String command : List.of("start", "lobby/open", "lobby/close", "pause")) {
            post(owner, game, command).then().statusCode(409).body("details.state", equalTo("paused"));
        }
        given().header("Authorization", owner.bearer()).delete("/api/v1/games/" + game).then().statusCode(409);

        post(owner, game, "end").then().statusCode(200);
        for (String command : List.of("start", "lobby/open", "lobby/close", "pause", "resume", "end")) {
            post(owner, game, command).then().statusCode(409).body("details.state", equalTo("ended"));
        }
        given().header("Authorization", owner.bearer()).delete("/api/v1/games/" + game).then().statusCode(200);
        for (String command : List.of("start", "lobby/open", "lobby/close", "pause", "resume", "end")) {
            post(owner, game, command).then().statusCode(409).body("details.state", equalTo("archived"));
        }
        given().header("Authorization", owner.bearer()).delete("/api/v1/games/" + game).then().statusCode(409);
    }

    @Test
    void onlyThePeopleWhoRunTheGameMoveIt() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        Login otherServerAdmin = TestWorld.newServerAdmin(server);   // administers the server, but not this game
        Login stranger = TestAccounts.newUser();
        String game = lobby(owner, server);

        // a player of the game
        Login player = TestAccounts.newUser();
        join(player, code(owner, game)).then().statusCode(200);
        post(owner, game, "requests/" + get(owner, "games/" + game + "/requests").path("[0].id") + "/accept").then().statusCode(200);

        for (String command : List.of("start", "lobby/close", "pause", "end")) {
            given().post("/api/v1/games/" + game + "/" + command).then().statusCode(401);
            post(stranger, game, command).then().statusCode(404);
            post(player, game, command).then().statusCode(403);
            // a server admin runs a game only when they are also its game admin (16.2)
            post(otherServerAdmin, game, command).then().statusCode(403);
        }
        assertEquals("lobby", state(owner, game));
        // the site admin holds every power
        post(TestAccounts.admin(), game, "start").then().statusCode(200).body("state", equalTo("running"));
    }

    // ---------------------------------------------------------------- one server, one active game

    @Test
    void aSecondActiveGameOnOneServerIsRefused() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String first = TestWorld.newGame(owner, server);
        String second = TestWorld.newGame(owner, server);

        // drafts do not count: both exist
        post(owner, first, "lobby/open").then().statusCode(200);
        post(owner, second, "lobby/open").then().statusCode(409).body("code", equalTo("SERVER_HAS_ACTIVE_GAME")).body("details.gameId", equalTo(first))
                .body("details.state", equalTo("lobby"));
        assertEquals("draft", state(owner, second));

        // running and paused games are active too
        post(owner, first, "start").then().statusCode(200);
        post(owner, second, "lobby/open").then().statusCode(409).body("code", equalTo("SERVER_HAS_ACTIVE_GAME"));
        post(owner, first, "pause").then().statusCode(200);
        post(owner, second, "lobby/open").then().statusCode(409).body("code", equalTo("SERVER_HAS_ACTIVE_GAME")).body("details.state", equalTo("paused"));

        // an ended game is not
        post(owner, first, "end").then().statusCode(200);
        post(owner, second, "lobby/open").then().statusCode(200);
        assertEquals("lobby", state(owner, second));
    }

    @Test
    void anotherServerHasItsOwnActiveGame() {
        Server a = TestWorld.newServer();
        Server b = TestWorld.newServer();
        Login admin = TestAccounts.admin();
        String onA = TestWorld.newGame(admin, a);
        String onB = TestWorld.newGame(admin, b);
        post(admin, onA, "lobby/open").then().statusCode(200);
        post(admin, onB, "lobby/open").then().statusCode(200);
    }

    @Test
    void aGameThatReopensItsOwnLobbyIsNotBlockedByItself() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String game = lobby(owner, server);
        post(owner, game, "start").then().statusCode(200);
        post(owner, game, "lobby/open").then().statusCode(200);
    }

    @Test
    void twoLobbiesOpenedAtTheSameMomentOnOneServerLetOnlyOneThrough() throws Exception {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String a = TestWorld.newGame(owner, server);
        String b = TestWorld.newGame(owner, server);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> openA = () -> post(owner, a, "lobby/open").statusCode();
            Callable<Integer> openB = () -> post(owner, b, "lobby/open").statusCode();
            Future<Integer> fa = pool.submit(openA);
            Future<Integer> fb = pool.submit(openB);
            List<Integer> statuses = List.of(fa.get(), fb.get()).stream().sorted().toList();
            assertEquals(List.of(200, 409), statuses);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aServerWithGamesCannotBeDeleted() {
        Server server = TestWorld.newServer();
        Login admin = TestAccounts.admin();
        String game = TestWorld.newGame(admin, server);
        given().header("Authorization", admin.bearer()).delete("/api/v1/servers/" + server.id()).then().statusCode(409).body("code", equalTo("SERVER_HAS_GAMES"));
        // archiving the game keeps the history, so the server stays
        given().header("Authorization", admin.bearer()).delete("/api/v1/games/" + game).then().statusCode(200);
        given().header("Authorization", admin.bearer()).delete("/api/v1/servers/" + server.id()).then().statusCode(409);
    }

    // ---------------------------------------------------------------- the join code

    @Test
    void theJoinCodeIsSixCharactersWithoutLookAlikes() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String game = lobby(owner, server);
        String code = code(owner, game);
        assertEquals(6, code.length(), code);
        for (char c : code.toCharArray()) {
            assertTrue(MinecraftLinkService.ALPHABET.indexOf(c) >= 0, "bad character in " + code);
            assertEquals(-1, MinecraftLinkService.LOOK_ALIKES.indexOf(c), "look-alike in " + code);
        }
    }

    @Test
    void theCodeIsValidOnlyWhileTheLobbyIsOpen() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String game = TestWorld.newGame(owner, server);
        // a draft has no code: nothing to type
        get(owner, "games/" + game).then().body("joinCode", nullValue());
        join(TestAccounts.newUser(), "ABCDEF").then().statusCode(400).body("code", equalTo("INVALID_JOIN_CODE"));

        post(owner, game, "lobby/open").then().statusCode(200);
        String first = code(owner, game);
        Login early = TestAccounts.newUser();
        join(early, first).then().statusCode(200).body("gameId", equalTo(game)).body("status", equalTo("requested")).body("role", equalTo("player"));

        // closing the lobby ends the code
        post(owner, game, "start").then().statusCode(200);
        get(owner, "games/" + game).then().body("joinCode", nullValue());
        join(TestAccounts.newUser(), first).then().statusCode(400).body("code", equalTo("INVALID_JOIN_CODE"));

        // a late-join lobby gets a new code; the old one stays dead
        post(owner, game, "lobby/open").then().statusCode(200);
        String second = code(owner, game);
        assertNotEquals(first, second);
        join(TestAccounts.newUser(), first).then().statusCode(400);
        join(TestAccounts.newUser(), second).then().statusCode(200);

        // and so does every other way out of the lobby
        post(owner, game, "lobby/close").then().statusCode(200);
        join(TestAccounts.newUser(), second).then().statusCode(400);
        post(owner, game, "pause").then().statusCode(200);
        post(owner, game, "end").then().statusCode(200);
        join(TestAccounts.newUser(), second).then().statusCode(400);
    }

    @Test
    void theCodeMayBeTypedInLowerCaseWithADash() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String game = lobby(owner, server);
        String code = code(owner, game);
        join(TestAccounts.newUser(), " " + code.substring(0, 3).toLowerCase() + "-" + code.substring(3).toLowerCase() + " ").then().statusCode(200);
    }

    @Test
    void twoOpenLobbiesNeverShareACode() {
        Server a = TestWorld.newServer();
        Server b = TestWorld.newServer();
        Login admin = TestAccounts.admin();
        String one = lobby(admin, a);
        String two = lobby(admin, b);
        assertNotEquals(code(admin, one), code(admin, two));
    }

    @Test
    void onlyPeopleWhoRunTheGameSeeTheCode() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String game = lobby(owner, server);
        Login player = TestAccounts.newUser();
        join(player, code(owner, game)).then().statusCode(200);
        String rid = get(owner, "games/" + game + "/requests").path("[0].id");
        post(owner, game, "requests/" + rid + "/accept").then().statusCode(200);

        get(player, "games/" + game).then().statusCode(200).body("joinCode", nullValue()).body("myRoles", hasItem("player")).body("myMembership", equalTo("accepted"));
        get(TestAccounts.admin(), "games/" + game).then().body("joinCode", equalTo(code(owner, game)));
        // a server admin who does not run this game is told nothing either
        Login otherServerAdmin = TestWorld.newServerAdmin(server);
        get(otherServerAdmin, "games/" + game).then().statusCode(200).body("joinCode", nullValue()).body("myRoles", hasItem("server_admin"));
    }

    // ---------------------------------------------------------------- requests: ask, accept, reject

    @Test
    void aRequestIsAcceptedByTheAdmin() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String game = lobby(owner, server);
        Login bruno = TestAccounts.newUser();

        join(bruno, code(owner, game)).then().statusCode(200).body("status", equalTo("requested")).body("gameName", org.hamcrest.Matchers.startsWith("Game "));
        // asking again changes nothing
        join(bruno, code(owner, game)).then().statusCode(200).body("status", equalTo("requested"));
        get(owner, "games/" + game + "/requests").then().statusCode(200).body("size()", equalTo(1)).body("[0].userId", equalTo(bruno.id()))
                .body("[0].displayName", equalTo("Test person")).body("[0].status", equalTo("requested")).body("[0].role", equalTo("player"));

        // while waiting: not a member, sees the request in their list, cannot open the game
        get(bruno, "games/" + game).then().statusCode(404);
        get(bruno, "games").then().body("find { it.id == '" + game + "' }.myMembership", equalTo("requested"));
        get(owner, "games/" + game + "/members").then().body("size()", equalTo(0));

        String rid = get(owner, "games/" + game + "/requests").path("[0].id");
        post(owner, game, "requests/" + rid + "/accept").then().statusCode(200).body("status", equalTo("accepted")).body("userId", equalTo(bruno.id()));

        get(owner, "games/" + game + "/requests").then().body("size()", equalTo(0));
        get(owner, "games/" + game + "/members").then().body("size()", equalTo(1)).body("[0].userId", equalTo(bruno.id())).body("[0].status", equalTo("accepted"));
        get(bruno, "games/" + game).then().statusCode(200);
        get(bruno, "games/" + game + "/members").then().statusCode(200).body("size()", equalTo(1));
        // answering twice
        post(owner, game, "requests/" + rid + "/accept").then().statusCode(409).body("code", equalTo("NOT_PENDING"));
        post(owner, game, "requests/" + rid + "/reject").then().statusCode(409).body("code", equalTo("NOT_PENDING"));
        // a member joining again
        join(bruno, code(owner, game)).then().statusCode(409).body("code", equalTo("ALREADY_MEMBER"));
    }

    @Test
    void aRejectedPersonMayAskAgainButARemovedOneMayNot() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String game = lobby(owner, server);
        Login carla = TestAccounts.newUser();
        Login dino = TestAccounts.newUser();

        join(carla, code(owner, game)).then().statusCode(200);
        String rid = get(owner, "games/" + game + "/requests").path("[0].id");
        post(owner, game, "requests/" + rid + "/reject").then().statusCode(200).body("status", equalTo("rejected"));
        get(owner, "games/" + game + "/requests").then().body("size()", equalTo(0));
        get(carla, "games/" + game).then().statusCode(404);
        get(carla, "games").then().body("id", not(hasItem(game)));

        // she asks again, this time as a spectator
        given().header("Authorization", carla.bearer()).contentType(ContentType.JSON).body(Map.of("code", code(owner, game), "role", "spectator"))
                .post("/api/v1/games/join").then().statusCode(200).body("status", equalTo("requested")).body("role", equalTo("spectator"));
        post(owner, game, "requests/" + get(owner, "games/" + game + "/requests").path("[0].id") + "/accept").then().statusCode(200);
        get(carla, "games/" + game).then().body("myRoles", hasItem("spectator"));

        // the admin removes Dino after accepting him, and he cannot come back with the code
        join(dino, code(owner, game)).then().statusCode(200);
        post(owner, game, "requests/" + get(owner, "games/" + game + "/requests").path("[0].id") + "/accept").then().statusCode(200);
        given().header("Authorization", owner.bearer()).delete("/api/v1/games/" + game + "/members/" + dino.id()).then().statusCode(204);
        get(dino, "games/" + game).then().statusCode(404);
        get(owner, "games/" + game + "/members").then().body("userId", not(hasItem(dino.id())));
        join(dino, code(owner, game)).then().statusCode(403).body("code", equalTo("KICKED"));
        given().header("Authorization", owner.bearer()).delete("/api/v1/games/" + game + "/members/" + dino.id()).then().statusCode(404);
    }

    @Test
    void badRolesAndOutsidersAreRefused() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String game = lobby(owner, server);
        given().header("Authorization", TestAccounts.newUser().bearer()).contentType(ContentType.JSON).body(Map.of("code", code(owner, game), "role", "admin"))
                .post("/api/v1/games/join").then().statusCode(400).body("code", equalTo("INVALID_ROLE"));
        given().header("Authorization", TestAccounts.newUser().bearer()).contentType(ContentType.JSON).post("/api/v1/games/join").then().statusCode(400);
        given().contentType(ContentType.JSON).body(Map.of("code", "ABCDEF")).post("/api/v1/games/join").then().statusCode(401);

        Login stranger = TestAccounts.newUser();
        Login player = TestAccounts.newUser();
        join(player, code(owner, game)).then().statusCode(200);
        String rid = get(owner, "games/" + game + "/requests").path("[0].id");
        // only the people who run the game see and answer requests
        get(stranger, "games/" + game + "/requests").then().statusCode(404);
        get(player, "games/" + game + "/requests").then().statusCode(404);
        post(stranger, game, "requests/" + rid + "/accept").then().statusCode(404);
        post(player, game, "requests/" + rid + "/accept").then().statusCode(404);
        given().get("/api/v1/games/" + game + "/requests").then().statusCode(401);
        post(owner, game, "requests/" + UUID.randomUUID() + "/accept").then().statusCode(404);
        // the request of one game cannot be answered through another
        Server other = TestWorld.newServer();
        String otherGame = lobby(TestAccounts.admin(), other);
        post(TestAccounts.admin(), otherGame, "requests/" + rid + "/accept").then().statusCode(404);
        get(owner, "games/" + game + "/requests").then().body("size()", equalTo(1));
    }

    @Test
    void guessingJoinCodesIsRateLimited() {
        Login guesser = TestAccounts.newUser();
        for (int i = 0; i < 10; i++) {
            join(guesser, "WRONG" + i).then().statusCode(400);
        }
        join(guesser, "WRONG99").then().statusCode(429).body("code", equalTo("RATE_LIMITED"));
    }

    @Test
    void requestsCannotBeAnsweredOnceTheGameHasEnded() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String game = lobby(owner, server);
        Login p = TestAccounts.newUser();
        join(p, code(owner, game)).then().statusCode(200);
        String rid = get(owner, "games/" + game + "/requests").path("[0].id");
        post(owner, game, "start").then().statusCode(200);
        post(owner, game, "end").then().statusCode(200);
        post(owner, game, "requests/" + rid + "/accept").then().statusCode(409).body("code", equalTo("INVALID_TRANSITION"));
    }

    // ---------------------------------------------------------------- co-admins

    @Test
    void theOwnerAddsCoAdminsFromTheSameServer() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        Login colleague = TestWorld.newServerAdmin(server);
        Login outsider = TestWorld.newServerAdmin(TestWorld.newServer());   // admin of a different server
        Login ordinary = TestAccounts.newUser();
        String game = lobby(owner, server);
        String path = "/api/v1/games/" + game + "/admins/";

        given().header("Authorization", owner.bearer()).put(path + outsider.id()).then().statusCode(400).body("code", equalTo("NOT_A_SERVER_ADMIN"));
        given().header("Authorization", owner.bearer()).put(path + ordinary.id()).then().statusCode(400).body("code", equalTo("NOT_A_SERVER_ADMIN"));
        given().header("Authorization", owner.bearer()).put(path + colleague.id()).then().statusCode(204);
        given().header("Authorization", owner.bearer()).put(path + colleague.id()).then().statusCode(204);
        get(owner, "games/" + game).then().body("admins.size()", equalTo(2)).body("admins.find { it.id == '" + colleague.id() + "' }.role", equalTo("co"));

        // the co-admin has the same powers over the game ...
        get(colleague, "games/" + game).then().statusCode(200).body("myRoles", hasItem("game_admin")).body("joinCode", org.hamcrest.Matchers.notNullValue());
        post(colleague, game, "start").then().statusCode(200);
        post(colleague, game, "pause").then().statusCode(200);
        // ... except choosing the other admins
        given().header("Authorization", colleague.bearer()).put(path + outsider.id()).then().statusCode(403);
        given().header("Authorization", colleague.bearer()).delete(path + colleague.id()).then().statusCode(403);
        given().header("Authorization", colleague.bearer()).delete(path + owner.id()).then().statusCode(403);
        // and nobody else may
        given().header("Authorization", ordinary.bearer()).put(path + ordinary.id()).then().statusCode(404);

        // the owner cannot be removed; a co-admin can
        given().header("Authorization", owner.bearer()).delete(path + owner.id()).then().statusCode(400).body("code", equalTo("CANNOT_REMOVE_OWNER"));
        given().header("Authorization", owner.bearer()).delete(path + colleague.id()).then().statusCode(204);
        given().header("Authorization", owner.bearer()).delete(path + colleague.id()).then().statusCode(404);
        post(colleague, game, "resume").then().statusCode(403);
        // the site admin may choose admins too
        given().header("Authorization", TestAccounts.admin().bearer()).put(path + colleague.id()).then().statusCode(204);
    }

    @Test
    void aCoAdminStopsBeingOneWhenTheyStopAdministeringTheServer() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        Login colleague = TestWorld.newServerAdmin(server);
        String game = TestWorld.newGame(owner, server);
        given().header("Authorization", owner.bearer()).put("/api/v1/games/" + game + "/admins/" + colleague.id()).then().statusCode(204);
        post(colleague, game, "lobby/open").then().statusCode(200);

        given().header("Authorization", TestAccounts.admin().bearer()).delete("/api/v1/servers/" + server.id() + "/admins/" + colleague.id()).then().statusCode(204);
        get(colleague, "games/" + game).then().statusCode(404);
        post(colleague, game, "start").then().statusCode(404);
        get(owner, "games/" + game).then().body("admins.size()", equalTo(1));
    }

    // ---------------------------------------------------------------- reading

    @Test
    void myGamesShowsWhatIRunWhatIPlayAndWhatIAskedFor() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        Login serverAdmin = TestWorld.newServerAdmin(server);
        Login player = TestAccounts.newUser();
        Login waiting = TestAccounts.newUser();
        Login stranger = TestAccounts.newUser();

        String game = lobby(owner, server);
        join(player, code(owner, game)).then().statusCode(200);
        post(owner, game, "requests/" + get(owner, "games/" + game + "/requests").path("[0].id") + "/accept").then().statusCode(200);
        join(waiting, code(owner, game)).then().statusCode(200);
        String draftOnServer = TestWorld.newGame(owner, server);

        get(owner, "games").then().body("id", hasItem(game)).body("id", hasItem(draftOnServer));
        get(serverAdmin, "games").then().body("id", hasItem(game)).body("id", hasItem(draftOnServer));
        get(player, "games").then().body("id", hasItem(game)).body("id", not(hasItem(draftOnServer)))
                .body("find { it.id == '" + game + "' }.myMembership", equalTo("accepted")).body("find { it.id == '" + game + "' }.joinCode", nullValue());
        get(waiting, "games").then().body("id", hasItem(game)).body("find { it.id == '" + game + "' }.myMembership", equalTo("requested"));
        get(stranger, "games").then().body("id", not(hasItem(game)));
        get(TestAccounts.admin(), "games").then().body("id", hasItem(game)).body("id", hasItem(draftOnServer));
        given().get("/api/v1/games").then().statusCode(401);

        // a game the person has nothing to do with does not exist for them
        get(stranger, "games/" + game).then().statusCode(404);
        get(stranger, "games/" + game + "/members").then().statusCode(404);
        get(stranger, "games/" + UUID.randomUUID()).then().statusCode(404);
        get(owner, "games/" + UUID.randomUUID()).then().statusCode(404);
        given().get("/api/v1/games/" + game).then().statusCode(401);
        // nothing in the list gives away the join code to the people who only play
        List<String> codes = new ArrayList<>(get(player, "games").jsonPath().getList("joinCode"));
        assertTrue(codes.stream().allMatch(c -> c == null));
    }
}
