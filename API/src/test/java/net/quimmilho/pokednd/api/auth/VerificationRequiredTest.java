/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.UUID;
import net.quimmilho.pokednd.api.FakeServices;
import net.quimmilho.pokednd.api.FakeSmtpServer.Mail;
import net.quimmilho.pokednd.api.TestAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The production setting: an account with a password cannot log in before its e-mail address is verified. */
@QuarkusTest
@TestProfile(VerificationRequiredTest.MustVerify.class)
@Tag("database")
class VerificationRequiredTest {

    /** Switches the "must verify" rule on, as it is in production. */
    public static class MustVerify implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("pokednd.auth.require-email-verification", "true");
        }
    }

    @Inject
    RateLimiter limiter;

    @BeforeEach
    void freshLimits() {
        limiter.reset();
    }

    private static Response login(String email, String password) {
        return given().contentType(ContentType.JSON).body(Map.of("email", email, "password", password)).post("/api/v1/auth/login");
    }

    @Test
    void loginWaitsForTheVerificationLink() {
        String email = "must-verify-" + UUID.randomUUID() + "@example.test";
        TestAccounts.register(email);
        login(email, TestAccounts.PASSWORD).then().statusCode(403).body("code", equalTo("EMAIL_NOT_VERIFIED"));

        Mail mail = FakeServices.smtp().awaitNewest(email);
        given().contentType(ContentType.JSON).body(Map.of("token", mail.token())).post("/api/v1/auth/verify-email").then().statusCode(204);
        login(email, TestAccounts.PASSWORD).then().statusCode(200).body("user.emailVerified", equalTo(true));
    }

    @Test
    void aWrongPasswordIsStillAnOrdinaryFailure() {
        String email = "must-verify-" + UUID.randomUUID() + "@example.test";
        TestAccounts.register(email);
        // the verification state must not be revealed to somebody who does not know the password
        login(email, "not the password at all").then().statusCode(401).body("code", equalTo("INVALID_CREDENTIALS"));
    }

    @Test
    void resettingThePasswordAlsoVerifiesTheAddress() {
        String email = "must-verify-" + UUID.randomUUID() + "@example.test";
        TestAccounts.register(email);
        given().contentType(ContentType.JSON).body(Map.of("email", email)).post("/api/v1/auth/forgot").then().statusCode(204);
        Mail reset = FakeServices.smtp().await(email, 2).get(1);
        given().contentType(ContentType.JSON).body(Map.of("token", reset.token(), "password", "a brand new password")).post("/api/v1/auth/reset")
                .then().statusCode(204);
        login(email, "a brand new password").then().statusCode(200);
    }
}
