/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.realtime;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.quimmilho.pokednd.api.TestAccounts;
import net.quimmilho.pokednd.api.TestAccounts.Login;
import net.quimmilho.pokednd.api.TestWorld;
import net.quimmilho.pokednd.api.TestWorld.Server;

/** A server, a game and the people in it, made through the REST API, for the realtime tests. */
record Table(Server server, Login owner, String game) {

    /** A game in the draft state (nobody can join yet). */
    static Table draft() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String game = TestWorld.newGame(owner, server);
        Table t = new Table(server, owner, game);
        t.setting("pokemon.starter.method", "assign");
        return t;
    }

    /** A game whose lobby is open. */
    static Table lobby() {
        Table t = draft();
        t.command("lobby/open");
        return t;
    }

    Response command(String path) {
        Response r = TestWorld.post(owner, game, path);
        r.then().statusCode(200);
        return r;
    }

    String joinCode() {
        return TestWorld.get(owner, "games/" + game).path("joinCode");
    }

    /** Asks to join with the code; returns the id of the request. */
    String askToJoin(Login who, String role) {
        Map<String, Object> body = role == null ? Map.of("code", joinCode()) : Map.of("code", joinCode(), "role", role);
        given().header("Authorization", who.bearer()).contentType(ContentType.JSON).body(body).post("/api/v1/games/join").then().statusCode(200);
        List<Map<String, Object>> requests = TestWorld.get(owner, "games/" + game + "/requests").jsonPath().getList("$");
        return requests.stream().filter(r -> who.id().equals(r.get("userId"))).map(r -> (String) r.get("id")).findFirst().orElseThrow();
    }

    void accept(String requestId) {
        given().header("Authorization", owner.bearer()).post("/api/v1/games/" + game + "/requests/" + requestId + "/accept").then().statusCode(200);
    }

    /** A new person who has joined as a player and was accepted. */
    Login player() {
        Login p = TestAccounts.newUser();
        accept(askToJoin(p, null));
        return p;
    }

    void kick(Login who) {
        given().header("Authorization", owner.bearer()).delete("/api/v1/games/" + game + "/members/" + who.id()).then().statusCode(204);
    }

    void setting(String key, Object value) {
        given().header("Authorization", owner.bearer()).contentType(ContentType.JSON).body(Map.of("settings", Map.of(key, value)))
                .patch("/api/v1/games/" + game + "/settings").then().statusCode(200);
    }

    /** Makes the player's trainer (point buy 15 14 13 11 10 9) and returns its id. */
    String trainer(Login who, String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("classId", "ace_trainer");
        m.put("method", "pointBuy");
        m.put("scores", Map.of("str", 15, "dex", 14, "con", 13, "int", 11, "wis", 10, "cha", 9));
        m.put("skills", List.of("pokemon_handling", "insight"));
        Response r = given().header("Authorization", who.bearer()).contentType(ContentType.JSON).body(m).post("/api/v1/games/" + game + "/trainers");
        r.then().statusCode(201);
        return r.path("id");
    }

    static String uniqueName() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    /** The Authorization header value of the server's token. */
    String modBearer() {
        return "Bearer " + server.token();
    }

    /** The server's id as a UUID. */
    UUID serverId() {
        return UUID.fromString(server.id());
    }
}
