/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.servers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.quimmilho.pokednd.api.TestAccounts;
import net.quimmilho.pokednd.api.TestAccounts.Login;
import net.quimmilho.pokednd.api.TestWorld;
import net.quimmilho.pokednd.api.TestWorld.Server;
import net.quimmilho.pokednd.api.auth.RateLimiter;
import net.quimmilho.pokednd.api.minecraft.MinecraftLinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The two linking endpoints of the mod, which only a server token opens (phase 31). Runs against a real PostgreSQL. */
@QuarkusTest
@Tag("database")
class ModLinkEndpointsTest {

    @Inject
    RateLimiter limiter;

    @BeforeEach
    void freshLimits() {
        limiter.reset();
    }

    private static RequestSpecification asServer(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private static Response linkCode(String token, UUID mc, String name) {
        return asServer(token).body(Map.of("mcUuid", mc.toString(), "mcName", name)).post("/api/v1/mod/link-code");
    }

    private static Response linkComplete(String token, String code, UUID mc, String name) {
        return asServer(token).body(Map.of("code", code, "mcUuid", mc.toString(), "mcName", name)).post("/api/v1/mod/link-complete");
    }

    // ---------------------------------------------------------------- the token decides

    @Test
    void aMissingWrongOrRotatedServerTokenIsRefused() {
        Server s = TestWorld.newServer();
        UUID mc = UUID.randomUUID();
        Map<String, String> codeBody = Map.of("mcUuid", mc.toString(), "mcName", "Steve");
        Map<String, String> completeBody = Map.of("code", "ABCDEFGH", "mcUuid", mc.toString(), "mcName", "Steve");

        for (String path : List.of("/api/v1/mod/link-code", "/api/v1/mod/link-complete")) {
            Map<String, String> body = path.endsWith("code") ? codeBody : completeBody;
            // no token at all
            given().contentType(ContentType.JSON).body(body).post(path).then().statusCode(401).body("code", equalTo("INVALID_SERVER_TOKEN"));
            // a wrong token, a token with the right look, an empty one
            asServer("nonsense").body(body).post(path).then().statusCode(401).body("code", equalTo("INVALID_SERVER_TOKEN"));
            asServer(ServerService.newToken()).body(body).post(path).then().statusCode(401).body("code", equalTo("INVALID_SERVER_TOKEN"));
            asServer("").body(body).post(path).then().statusCode(401);
            // another kind of Authorization header
            given().header("Authorization", "Basic " + s.token()).contentType(ContentType.JSON).body(body).post(path).then().statusCode(401);
            // a person's access token, even the site admin's, is not a server token
            asServer(TestAccounts.admin().accessToken()).body(body).post(path).then().statusCode(401).body("code", equalTo("INVALID_SERVER_TOKEN"));
        }

        // after rotation the old token fails
        String old = s.token();
        String fresh = given().header("Authorization", TestAccounts.admin().bearer()).post("/api/v1/servers/" + s.id() + "/token/rotate").path("token");
        linkCode(old, mc, "Steve").then().statusCode(401).body("code", equalTo("INVALID_SERVER_TOKEN"));
        linkComplete(old, "ABCDEFGH", mc, "Steve").then().statusCode(401).body("code", equalTo("INVALID_SERVER_TOKEN"));
        linkCode(fresh, mc, "Steve").then().statusCode(200);
    }

    @Test
    void aServerTokenOpensNothingElse() {
        Server s = TestWorld.newServer();
        for (String path : List.of("/api/v1/me", "/api/v1/me/minecraft", "/api/v1/me/servers", "/api/v1/servers", "/api/v1/games")) {
            given().header("Authorization", "Bearer " + s.token()).get(path).then().statusCode(401);
        }
        given().header("Authorization", "Bearer " + s.token()).post("/api/v1/me/minecraft/link-code").then().statusCode(401);
        given().header("Authorization", "Bearer " + s.token()).get("/api/v1/servers/" + s.id() + "/status").then().statusCode(401);
    }

    // ---------------------------------------------------------------- with a good token

    @Test
    void aCodeMadeThroughTheModLinksAnAppAccount() {
        Server s = TestWorld.newServer();
        Login me = TestAccounts.newUser();
        UUID mc = UUID.randomUUID();

        Response made = linkCode(s.token(), mc, "Steve");
        made.then().statusCode(200);
        String code = made.path("code");
        assertEquals(MinecraftLinkService.CODE_LENGTH, code.length());
        assertTrue(made.path("expiresAt") != null);

        given().header("Authorization", me.bearer()).contentType(ContentType.JSON).body(Map.of("code", code)).post("/api/v1/me/minecraft/link").then()
                .statusCode(200).body("mcUuid", equalTo(mc.toString())).body("mcName", equalTo("Steve"));
        given().header("Authorization", me.bearer()).get("/api/v1/me/minecraft").then().body("size()", equalTo(1)).body("[0].mcName", equalTo("Steve"));

        // the account belongs to someone now, so the mod cannot make another code for it
        linkCode(s.token(), mc, "Steve").then().statusCode(409).body("code", equalTo("MC_ALREADY_LINKED"));
    }

    @Test
    void aCodeMadeInTheAppIsCompletedThroughTheMod() {
        Server s = TestWorld.newServer();
        Login me = TestAccounts.newUser();
        UUID mc = UUID.randomUUID();
        String code = given().header("Authorization", me.bearer()).post("/api/v1/me/minecraft/link-code").path("code");

        // a code made for a Minecraft account is no good here, and a wrong one neither
        linkComplete(s.token(), "ZZZZZZZZ", mc, "Alex").then().statusCode(400).body("code", equalTo("INVALID_CODE"));

        linkComplete(s.token(), code, mc, "Alex").then().statusCode(200).body("userId", equalTo(me.id())).body("displayName", equalTo("Test person"))
                .body("mcUuid", equalTo(mc.toString())).body("mcName", equalTo("Alex"));
        given().header("Authorization", me.bearer()).get("/api/v1/me/minecraft").then().body("size()", equalTo(1)).body("[0].mcUuid", equalTo(mc.toString()));

        // single use
        linkComplete(s.token(), code, UUID.randomUUID(), "Other").then().statusCode(400).body("code", equalTo("INVALID_CODE"));
    }

    @Test
    void anyServerMayLinkAnAccountAndTheCodeDoesNotDependOnWhichOne() {
        Server first = TestWorld.newServer();
        Server second = TestWorld.newServer();
        Login me = TestAccounts.newUser();
        String code = given().header("Authorization", me.bearer()).post("/api/v1/me/minecraft/link-code").path("code");
        linkComplete(second.token(), code, UUID.randomUUID(), "Alex").then().statusCode(200);
        linkCode(first.token(), UUID.randomUUID(), "Steve").then().statusCode(200);
    }

    @Test
    void badInputIsRefusedEvenWithAGoodToken() {
        Server s = TestWorld.newServer();
        asServer(s.token()).body(Map.of("mcUuid", "not-a-uuid", "mcName", "Steve")).post("/api/v1/mod/link-code").then().statusCode(400)
                .body("code", equalTo("INVALID_MINECRAFT_ACCOUNT"));
        asServer(s.token()).body(Map.of("mcUuid", UUID.randomUUID().toString(), "mcName", "bad name!")).post("/api/v1/mod/link-code").then().statusCode(400)
                .body("code", equalTo("INVALID_MINECRAFT_ACCOUNT"));
        asServer(s.token()).body(Map.of("mcName", "Steve")).post("/api/v1/mod/link-code").then().statusCode(400);
        asServer(s.token()).post("/api/v1/mod/link-code").then().statusCode(400);
        asServer(s.token()).body(Map.of("mcUuid", UUID.randomUUID().toString(), "mcName", "Steve")).post("/api/v1/mod/link-complete").then().statusCode(400)
                .body("code", equalTo("BAD_REQUEST"));
    }
}
