/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import java.util.UUID;
import net.quimmilho.pokednd.api.TestAccounts.Login;

/** Makes servers and games through the REST API, for the tests that need them. */
public final class TestWorld {

    private TestWorld() {
    }

    /** A registered server and its token. */
    public record Server(String id, String token) {
    }

    /** Registers a server as the site admin. */
    public static Server newServer() {
        Response r = given().header("Authorization", TestAccounts.admin().bearer()).contentType(ContentType.JSON)
                .body(Map.of("name", "Server " + UUID.randomUUID())).post("/api/v1/servers");
        r.then().statusCode(201);
        return new Server(r.path("server.id"), r.path("token"));
    }

    /** Makes the person an admin of the server (as the site admin does). */
    public static void makeServerAdmin(Server server, Login who) {
        given().header("Authorization", TestAccounts.admin().bearer()).put("/api/v1/servers/" + server.id() + "/admins/" + who.id()).then().statusCode(204);
    }

    /** A new account that administers the server. */
    public static Login newServerAdmin(Server server) {
        Login who = TestAccounts.newUser();
        makeServerAdmin(server, who);
        return who;
    }

    /** Creates a draft game on the server; {@code owner} must administer the server (or be the site admin). */
    public static String newGame(Login owner, Server server) {
        Response r = given().header("Authorization", owner.bearer()).contentType(ContentType.JSON)
                .body(Map.of("serverId", server.id(), "name", "Game " + UUID.randomUUID())).post("/api/v1/games");
        r.then().statusCode(201);
        return r.path("id");
    }

    /** Does something to a game as this person: {@code post(who, game, "lobby/open")}. */
    public static Response post(Login who, String gameId, String path) {
        return given().header("Authorization", who.bearer()).post("/api/v1/games/" + gameId + "/" + path);
    }

    public static Response get(Login who, String path) {
        return given().header("Authorization", who.bearer()).get("/api/v1/" + path);
    }
}
