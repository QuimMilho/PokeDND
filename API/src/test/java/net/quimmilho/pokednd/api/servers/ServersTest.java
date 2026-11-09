/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.servers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.quimmilho.pokednd.api.TestAccounts;
import net.quimmilho.pokednd.api.TestAccounts.Login;
import net.quimmilho.pokednd.api.TestWorld;
import net.quimmilho.pokednd.api.TestWorld.Server;
import net.quimmilho.pokednd.api.auth.RateLimiter;
import net.quimmilho.pokednd.api.auth.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Registering servers, their tokens and their admins (phase 31). Runs against a real PostgreSQL. */
@QuarkusTest
@Tag("database")
class ServersTest {

    @Inject
    ServerService service;

    @Inject
    RateLimiter limiter;

    @Inject
    AgroalDataSource dataSource;

    @BeforeEach
    void freshLimits() {
        limiter.reset();
    }

    private static Response register(Login who, String name, String url) {
        Map<String, Object> body = url == null ? Map.of("name", name) : Map.of("name", name, "bluemapUrl", url);
        return given().header("Authorization", who.bearer()).contentType(ContentType.JSON).body(body).post("/api/v1/servers");
    }

    private static String uniqueName() {
        return "Test server " + UUID.randomUUID();
    }

    // ---------------------------------------------------------------- who may do what (permission table, 16.2)

    @Test
    void onlyASiteAdminRegistersServers() {
        given().contentType(ContentType.JSON).body(Map.of("name", uniqueName())).post("/api/v1/servers").then().statusCode(401);
        register(TestAccounts.newUser(), uniqueName(), null).then().statusCode(403);

        // a server admin (of some other server) may not register either
        Login serverAdmin = TestWorld.newServerAdmin(TestWorld.newServer());
        register(serverAdmin, uniqueName(), null).then().statusCode(403);

        register(TestAccounts.admin(), uniqueName(), null).then().statusCode(201);
    }

    @Test
    void aServerAdminManagesOnlyTheirOwnServer() {
        Server mine = TestWorld.newServer();
        Server other = TestWorld.newServer();
        Login me = TestWorld.newServerAdmin(mine);
        String bearer = me.bearer();

        // what they may do: see their own server, its status
        given().header("Authorization", bearer).get("/api/v1/me/servers").then().statusCode(200).body("id", equalTo(List.of(mine.id())))
                .body("[0].admins.id", hasItem(me.id()));
        given().header("Authorization", bearer).get("/api/v1/servers/" + mine.id() + "/status").then().statusCode(200).body("id", equalTo(mine.id()))
                .body("status", equalTo("offline"));

        // what they may not do: the status of another server, and every site-admin action, even on their own server
        given().header("Authorization", bearer).get("/api/v1/servers/" + other.id() + "/status").then().statusCode(403);
        given().header("Authorization", bearer).get("/api/v1/servers/" + UUID.randomUUID() + "/status").then().statusCode(403);
        given().header("Authorization", bearer).get("/api/v1/servers").then().statusCode(403);
        for (Server s : List.of(mine, other)) {
            given().header("Authorization", bearer).get("/api/v1/servers/" + s.id()).then().statusCode(403);
            given().header("Authorization", bearer).contentType(ContentType.JSON).body(Map.of("name", "Renamed")).patch("/api/v1/servers/" + s.id())
                    .then().statusCode(403);
            given().header("Authorization", bearer).delete("/api/v1/servers/" + s.id()).then().statusCode(403);
            given().header("Authorization", bearer).post("/api/v1/servers/" + s.id() + "/token/rotate").then().statusCode(403);
            given().header("Authorization", bearer).put("/api/v1/servers/" + s.id() + "/admins/" + me.id()).then().statusCode(403);
            given().header("Authorization", bearer).delete("/api/v1/servers/" + s.id() + "/admins/" + me.id()).then().statusCode(403);
        }
        // nothing changed
        given().header("Authorization", TestAccounts.admin().bearer()).get("/api/v1/servers/" + mine.id()).then().statusCode(200)
                .body("admins.id", hasItem(me.id()));
    }

    @Test
    void anOrdinaryUserSeesNoServersAndNoStatus() {
        Server s = TestWorld.newServer();
        Login user = TestAccounts.newUser();
        given().header("Authorization", user.bearer()).get("/api/v1/me/servers").then().statusCode(200).body("size()", equalTo(0));
        given().header("Authorization", user.bearer()).get("/api/v1/servers/" + s.id() + "/status").then().statusCode(403);
        given().get("/api/v1/servers/" + s.id() + "/status").then().statusCode(401);
        given().get("/api/v1/me/servers").then().statusCode(401);
    }

    @Test
    void theSiteAdminSeesEverythingAndTheStatusOfAnyServer() {
        Server s = TestWorld.newServer();
        Login admin = TestAccounts.admin();
        given().header("Authorization", admin.bearer()).get("/api/v1/servers").then().statusCode(200).body("id", hasItem(s.id()));
        given().header("Authorization", admin.bearer()).get("/api/v1/servers/" + s.id()).then().statusCode(200).body("id", equalTo(s.id()));
        given().header("Authorization", admin.bearer()).get("/api/v1/servers/" + s.id() + "/status").then().statusCode(200);
        given().header("Authorization", admin.bearer()).get("/api/v1/servers/" + UUID.randomUUID() + "/status").then().statusCode(404);
        given().header("Authorization", admin.bearer()).get("/api/v1/servers/not-an-id").then().statusCode(404);
    }

    // ---------------------------------------------------------------- the token

    @Test
    void theTokenIsShownOnceAndOnlyAtRegistrationAndRotation() {
        Login admin = TestAccounts.admin();
        Response r = register(admin, uniqueName(), "https://map.example.test/");
        r.then().statusCode(201).body("server.status", equalTo("offline")).body("server.bluemapUrl", equalTo("https://map.example.test/"));
        String token = r.path("token");
        String id = r.path("server.id");
        assertTrue(token.startsWith("pds_"), token);
        // 256 bits written as URL-safe text: 43 characters after the mark
        assertEquals(4 + 43, token.length(), token);
        assertEquals(token.substring(0, 8), r.path("server.tokenPrefix"));

        // no other answer carries the token
        String list = given().header("Authorization", admin.bearer()).get("/api/v1/servers").asString();
        String one = given().header("Authorization", admin.bearer()).get("/api/v1/servers/" + id).asString();
        String status = given().header("Authorization", admin.bearer()).get("/api/v1/servers/" + id + "/status").asString();
        for (String body : List.of(list, one, status)) {
            assertFalse(body.contains(token), "the token is in an answer: " + body);
            assertFalse(body.contains(TokenService.hash(token)), "the hash is in an answer: " + body);
            assertFalse(body.contains("tokenHash"));
        }

        // rotation shows the new token once, and the answer to rotating is the only other place
        Response rotated = given().header("Authorization", admin.bearer()).post("/api/v1/servers/" + id + "/token/rotate");
        rotated.then().statusCode(200);
        String second = rotated.path("token");
        assertNotEquals(token, second);
        assertTrue(second.startsWith("pds_"));
        assertEquals(second.substring(0, 8), rotated.path("server.tokenPrefix"));
        assertFalse(given().header("Authorization", admin.bearer()).get("/api/v1/servers/" + id).asString().contains(second));
    }

    @Test
    void afterRotationTheOldTokenFailsAndTheNewOneWorks() {
        Server s = TestWorld.newServer();
        assertTrue(service.authenticate(s.token()).isPresent());

        Response rotated = given().header("Authorization", TestAccounts.admin().bearer()).post("/api/v1/servers/" + s.id() + "/token/rotate");
        rotated.then().statusCode(200);
        String fresh = rotated.path("token");

        assertTrue(service.authenticate(s.token()).isEmpty(), "the old token still works");
        assertEquals(s.id(), service.authenticate(fresh).orElseThrow().id().toString());
        // a second rotation again kills the one before
        given().header("Authorization", TestAccounts.admin().bearer()).post("/api/v1/servers/" + s.id() + "/token/rotate").then().statusCode(200);
        assertTrue(service.authenticate(fresh).isEmpty());
    }

    @Test
    void aTokenIsBoundToOneServer() {
        Server a = TestWorld.newServer();
        Server b = TestWorld.newServer();
        assertEquals(a.id(), service.authenticate(a.token()).orElseThrow().id().toString());
        assertEquals(b.id(), service.authenticate(b.token()).orElseThrow().id().toString());
        assertNotEquals(a.token(), b.token());
        // rotating one server's token leaves the other's alone
        given().header("Authorization", TestAccounts.admin().bearer()).post("/api/v1/servers/" + a.id() + "/token/rotate").then().statusCode(200);
        assertTrue(service.authenticate(b.token()).isPresent());
    }

    @Test
    void nonsenseIsNotAToken() {
        assertTrue(service.authenticate(null).isEmpty());
        assertTrue(service.authenticate("").isEmpty());
        assertTrue(service.authenticate("pds_").isEmpty());
        assertTrue(service.authenticate("pds_" + "x".repeat(500)).isEmpty());
        assertTrue(service.authenticate(TestAccounts.admin().accessToken()).isEmpty());
    }

    @Test
    void theDatabaseNeverContainsTheToken() throws SQLException {
        Login admin = TestAccounts.admin();
        Response r = register(admin, uniqueName(), null);
        String token = r.path("token");
        String id = r.path("server.id");
        String rotated = given().header("Authorization", admin.bearer()).post("/api/v1/servers/" + id + "/token/rotate").path("token");
        // the first token was replaced; both must be absent, and so must their middle parts
        List<String> secrets = List.of(token, rotated, token.substring(4), rotated.substring(4));

        List<String> tables = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("select tablename from pg_tables where schemaname = 'public'")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        assertTrue(tables.contains("server"), tables.toString());
        int rows = 0;
        for (String table : tables) {
            try (Connection c = dataSource.getConnection(); Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("select t::text from \"" + table + "\" t")) {
                while (rs.next()) {
                    String row = rs.getString(1);
                    rows++;
                    for (String secret : secrets) {
                        assertFalse(row.contains(secret), "the token is in table " + table + ": " + row);
                    }
                }
            }
        }
        assertTrue(rows > 0);

        // what is stored is the hash and the short prefix
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("select token_hash, token_prefix from server where id = '" + id + "'")) {
            assertTrue(rs.next());
            assertEquals(TokenService.hash(rotated), rs.getString(1));
            assertEquals(rotated.substring(0, 8), rs.getString(2));
        }
    }

    // ---------------------------------------------------------------- registering, editing, deleting

    @Test
    void namesAndAddressesAreChecked() {
        Login admin = TestAccounts.admin();
        String name = uniqueName();
        register(admin, name, null).then().statusCode(201);
        register(admin, name.toUpperCase(), null).then().statusCode(409).body("code", equalTo("SERVER_NAME_TAKEN"));
        register(admin, "   ", null).then().statusCode(400).body("code", equalTo("INVALID_NAME"));
        register(admin, "x".repeat(65), null).then().statusCode(400).body("code", equalTo("INVALID_NAME"));
        register(admin, uniqueName(), "ftp://map.example.test").then().statusCode(400).body("code", equalTo("INVALID_URL"));
        register(admin, uniqueName(), "not a url").then().statusCode(400).body("code", equalTo("INVALID_URL"));
        given().header("Authorization", admin.bearer()).contentType(ContentType.JSON).post("/api/v1/servers").then().statusCode(400);
    }

    @Test
    void aServerCanBeRenamedAndItsMapAddressChangedOrRemoved() {
        Login admin = TestAccounts.admin();
        Response r = register(admin, uniqueName(), "http://map.example.test");
        String id = r.path("server.id");
        String newName = uniqueName();

        given().header("Authorization", admin.bearer()).contentType(ContentType.JSON).body(Map.of("name", newName)).patch("/api/v1/servers/" + id).then()
                .statusCode(200).body("name", equalTo(newName)).body("bluemapUrl", equalTo("http://map.example.test"));
        given().header("Authorization", admin.bearer()).contentType(ContentType.JSON).body(Map.of("bluemapUrl", "")).patch("/api/v1/servers/" + id).then()
                .statusCode(200).body("name", equalTo(newName)).body("bluemapUrl", org.hamcrest.Matchers.nullValue());

        // another server's name cannot be taken
        String other = register(admin, uniqueName(), null).path("server.name");
        given().header("Authorization", admin.bearer()).contentType(ContentType.JSON).body(Map.of("name", other)).patch("/api/v1/servers/" + id).then()
                .statusCode(409);
        // but keeping its own name is fine
        given().header("Authorization", admin.bearer()).contentType(ContentType.JSON).body(Map.of("name", newName)).patch("/api/v1/servers/" + id).then()
                .statusCode(200);
        given().header("Authorization", admin.bearer()).contentType(ContentType.JSON).body(Map.of("name", "x")).patch("/api/v1/servers/" + UUID.randomUUID())
                .then().statusCode(404);
    }

    @Test
    void deletingAServerRemovesItsAdminsAndItsToken() {
        Login admin = TestAccounts.admin();
        Server s = TestWorld.newServer();
        Login serverAdmin = TestWorld.newServerAdmin(s);

        given().header("Authorization", admin.bearer()).delete("/api/v1/servers/" + s.id()).then().statusCode(204);
        given().header("Authorization", admin.bearer()).get("/api/v1/servers/" + s.id()).then().statusCode(404);
        given().header("Authorization", admin.bearer()).delete("/api/v1/servers/" + s.id()).then().statusCode(404);
        assertTrue(service.authenticate(s.token()).isEmpty());
        given().header("Authorization", serverAdmin.bearer()).get("/api/v1/me/servers").then().body("size()", equalTo(0));
    }

    // ---------------------------------------------------------------- admins

    @Test
    void serverAdminsCanBeAssignedAndRemoved() {
        Login admin = TestAccounts.admin();
        Server s = TestWorld.newServer();
        Login person = TestAccounts.newUser();
        String path = "/api/v1/servers/" + s.id() + "/admins/" + person.id();

        given().header("Authorization", admin.bearer()).put(path).then().statusCode(204);
        // assigning twice is harmless
        given().header("Authorization", admin.bearer()).put(path).then().statusCode(204);
        given().header("Authorization", admin.bearer()).get("/api/v1/servers/" + s.id()).then().body("admins.size()", equalTo(1))
                .body("admins[0].id", equalTo(person.id()));
        given().header("Authorization", person.bearer()).get("/api/v1/me/servers").then().body("id", hasItem(s.id()));

        // a person who does not exist
        given().header("Authorization", admin.bearer()).put("/api/v1/servers/" + s.id() + "/admins/" + UUID.randomUUID()).then().statusCode(404);
        given().header("Authorization", admin.bearer()).put("/api/v1/servers/" + UUID.randomUUID() + "/admins/" + person.id()).then().statusCode(404);

        given().header("Authorization", admin.bearer()).delete(path).then().statusCode(204);
        given().header("Authorization", admin.bearer()).delete(path).then().statusCode(404);
        given().header("Authorization", person.bearer()).get("/api/v1/me/servers").then().body("id", not(hasItem(s.id())));
        given().header("Authorization", person.bearer()).get("/api/v1/servers/" + s.id() + "/status").then().statusCode(403);
    }

    @Test
    void aServerCanHaveSeveralAdminsAndAPersonSeveralServers() {
        Server a = TestWorld.newServer();
        Server b = TestWorld.newServer();
        Login one = TestAccounts.newUser();
        Login two = TestAccounts.newUser();
        TestWorld.makeServerAdmin(a, one);
        TestWorld.makeServerAdmin(b, one);
        TestWorld.makeServerAdmin(a, two);
        given().header("Authorization", one.bearer()).get("/api/v1/me/servers").then().body("size()", equalTo(2));
        given().header("Authorization", two.bearer()).get("/api/v1/me/servers").then().body("size()", equalTo(1));
        given().header("Authorization", TestAccounts.admin().bearer()).get("/api/v1/servers/" + a.id()).then().body("admins.size()", equalTo(2));
    }

    // ---------------------------------------------------------------- online status

    @Test
    void theOnlineStateIsStoredAndStartsOffline() {
        Login admin = TestAccounts.admin();
        Server s = TestWorld.newServer();
        UUID id = UUID.fromString(s.id());
        String status = "/api/v1/servers/" + s.id() + "/status";

        given().header("Authorization", admin.bearer()).get(status).then().body("status", equalTo("offline")).body("lastSeenAt", org.hamcrest.Matchers.nullValue());

        service.markOnline(id, "0.1.0", "1.21.1");
        given().header("Authorization", admin.bearer()).get(status).then().body("status", equalTo("online")).body("modVersion", equalTo("0.1.0"))
                .body("mcVersion", equalTo("1.21.1")).body("lastSeenAt", org.hamcrest.Matchers.notNullValue());

        // a heartbeat without versions keeps the ones it knew
        service.markOnline(id, null, null);
        given().header("Authorization", admin.bearer()).get(status).then().body("status", equalTo("online")).body("modVersion", equalTo("0.1.0"));

        service.markOffline(id);
        given().header("Authorization", admin.bearer()).get(status).then().body("status", equalTo("offline")).body("modVersion", equalTo("0.1.0"));

        service.markOnline(id, "0.1.0", "1.21.1");
        assertTrue(service.markAllOffline() >= 1);
        given().header("Authorization", admin.bearer()).get(status).then().body("status", equalTo("offline"));
        assertNotNull(service.get(id));
    }

    @Test
    void onlyAShortPrefixOfTheTokenIsKeptInTheClear() {
        Response r = register(TestAccounts.admin(), uniqueName(), null);
        r.then().body("server.tokenPrefix", containsString("pds_"));
        assertEquals(ServerService.PREFIX_LENGTH, ((String) r.path("server.tokenPrefix")).length());
    }
}
