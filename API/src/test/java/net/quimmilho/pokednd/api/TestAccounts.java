/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import java.util.UUID;

/** Makes accounts for the tests that need a logged-in person. */
public final class TestAccounts {

    public static final String PASSWORD = "correct horse battery staple";
    /** The address that the test configuration makes site admin ({@code %test.pokednd.auth.site-admin-email}). */
    public static final String ADMIN_EMAIL = "admin@example.test";

    private TestAccounts() {
    }

    /** A logged-in person. */
    public record Login(String email, String id, String accessToken, String refreshCookie) {
        /** The header value for {@code Authorization}. */
        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    /** Signs up a new person with a random address and logs them in. */
    public static Login newUser() {
        String email = "user-" + UUID.randomUUID() + "@example.test";
        register(email);
        return login(email);
    }

    /** The site admin: created at start from the bootstrap settings of the test profile. */
    public static Login admin() {
        return login(ADMIN_EMAIL);
    }

    public static void register(String email) {
        given().contentType(ContentType.JSON).body(Map.of("email", email, "password", PASSWORD, "displayName", "Test person"))
                .post("/api/v1/auth/register").then().statusCode(201);
    }

    public static Login login(String email) {
        Response r = given().contentType(ContentType.JSON).body(Map.of("email", email, "password", PASSWORD)).post("/api/v1/auth/login");
        r.then().statusCode(200);
        return new Login(email, r.path("user.id"), r.path("accessToken"), r.getCookie("pokednd_refresh"));
    }
}
