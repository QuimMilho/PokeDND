/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import net.quimmilho.pokednd.api.TestAccounts;
import net.quimmilho.pokednd.api.TestAccounts.Login;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Seeing and ending sessions, and what a stolen refresh token does (phase 29). Runs against a real PostgreSQL. */
@QuarkusTest
@Tag("database")
class SessionsTest {

    @Inject
    RateLimiter limiter;

    @BeforeEach
    void freshLimits() {
        limiter.reset();
    }

    private static Response sessions(Login who) {
        return given().header("Authorization", who.bearer()).get("/api/v1/me/sessions");
    }

    private static Response login(String email, String userAgent) {
        return given().contentType(ContentType.JSON).header("User-Agent", userAgent).body(Map.of("email", email, "password", TestAccounts.PASSWORD))
                .post("/api/v1/auth/login");
    }

    private static Response refresh(String cookie) {
        return given().cookie("pokednd_refresh", cookie).post("/api/v1/auth/refresh");
    }

    @Test
    void theListShowsEachLoginAndMarksTheCurrentOne() {
        Login first = TestAccounts.newUser();
        Response second = login(first.email(), "TestBrowser/2.0");
        Login other = new Login(first.email(), first.id(), second.path("accessToken"), second.getCookie("pokednd_refresh"));

        sessions(first).then().statusCode(200).body("size()", equalTo(2));
        List<Map<String, Object>> list = sessions(other).jsonPath().getList("$");
        long current = list.stream().filter(s -> Boolean.TRUE.equals(s.get("current"))).count();
        org.junit.jupiter.api.Assertions.assertEquals(1, current);
        Map<String, Object> mine = list.stream().filter(s -> Boolean.TRUE.equals(s.get("current"))).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("TestBrowser/2.0", mine.get("userAgent"));
        // the refresh token itself is never shown
        org.junit.jupiter.api.Assertions.assertFalse(sessions(other).asString().contains(other.refreshCookie()));
    }

    @Test
    void refreshingKeepsTheSameSession() {
        Login me = TestAccounts.newUser();
        String id = sessions(me).path("[0].id");
        Response r = refresh(me.refreshCookie());
        r.then().statusCode(200);
        Login renewed = new Login(me.email(), me.id(), r.path("accessToken"), r.getCookie("pokednd_refresh"));
        sessions(renewed).then().statusCode(200).body("size()", equalTo(1)).body("[0].id", equalTo(id)).body("[0].current", equalTo(true));
    }

    @Test
    void endingASessionStopsItsRefreshTokenButNotTheOthers() {
        Login keep = TestAccounts.newUser();
        Response second = login(keep.email(), "Other/1.0");
        Login victim = new Login(keep.email(), keep.id(), second.path("accessToken"), second.getCookie("pokednd_refresh"));
        String victimId = sessions(victim).jsonPath().getList("findAll { it.current }.id").get(0).toString();

        given().header("Authorization", keep.bearer()).delete("/api/v1/me/sessions/" + victimId).then().statusCode(204);

        refresh(victim.refreshCookie()).then().statusCode(401);
        refresh(keep.refreshCookie()).then().statusCode(200);
        sessions(keep).then().statusCode(200).body("id", not(hasItem(victimId)));
        // ending it again, or an id that does not exist, is a 404
        given().header("Authorization", keep.bearer()).delete("/api/v1/me/sessions/" + victimId).then().statusCode(404);
        given().header("Authorization", keep.bearer()).delete("/api/v1/me/sessions/not-an-id").then().statusCode(404);
    }

    @Test
    void aSessionOfSomebodyElseCannotBeEnded() {
        Login me = TestAccounts.newUser();
        Login them = TestAccounts.newUser();
        String theirs = sessions(them).path("[0].id");
        given().header("Authorization", me.bearer()).delete("/api/v1/me/sessions/" + theirs).then().statusCode(404);
        refresh(them.refreshCookie()).then().statusCode(200); // still alive
    }

    @Test
    void sessionsNeedALogin() {
        given().get("/api/v1/me/sessions").then().statusCode(401);
        given().delete("/api/v1/me/sessions/00000000-0000-0000-0000-000000000000").then().statusCode(401);
    }

    @Test
    void aReusedRefreshTokenEndsTheWholeSessionFamily() {
        Login me = TestAccounts.newUser();
        Response r = refresh(me.refreshCookie());
        Login renewed = new Login(me.email(), me.id(), r.path("accessToken"), r.getCookie("pokednd_refresh"));
        sessions(renewed).then().body("size()", equalTo(1));

        // a thief shows the old token again: the login is closed, and the newer token dies with it
        refresh(me.refreshCookie()).then().statusCode(401).body("code", equalTo("REFRESH_TOKEN_REUSED"));
        refresh(renewed.refreshCookie()).then().statusCode(401);
        // the session is gone from the list (the person logs in again)
        Login again = TestAccounts.login(me.email());
        sessions(again).then().statusCode(200).body("size()", equalTo(1)).body("[0].current", equalTo(true));
    }

    @Test
    void loggingOutRemovesTheSessionFromTheList() {
        Login me = TestAccounts.newUser();
        Login second = TestAccounts.login(me.email());
        given().cookie("pokednd_refresh", me.refreshCookie()).post("/api/v1/auth/logout").then().statusCode(204);
        sessions(second).then().statusCode(200).body("size()", equalTo(1));
    }
}
