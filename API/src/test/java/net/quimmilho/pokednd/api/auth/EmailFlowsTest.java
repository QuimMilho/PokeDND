/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import net.quimmilho.pokednd.api.FakeServices;
import net.quimmilho.pokednd.api.FakeSmtpServer.Mail;
import net.quimmilho.pokednd.api.TestAccounts;
import net.quimmilho.pokednd.api.TestAccounts.Login;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository.EmailToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** E-mail verification and password reset against a fake mail server (phase 29). Runs against a real PostgreSQL. */
@QuarkusTest
@Tag("database")
class EmailFlowsTest {

    private static final String NEW_PASSWORD = "another long password 42";

    @Inject
    RateLimiter limiter;

    @Inject
    AccountRepository accounts;

    @BeforeEach
    void freshLimits() {
        limiter.reset();
    }

    private static String freshEmail() {
        return "mail-" + UUID.randomUUID() + "@example.test";
    }

    private static String register(String email, String locale) {
        return given().contentType(ContentType.JSON).body(Map.of("email", email, "password", TestAccounts.PASSWORD, "displayName", "Dani", "locale", locale))
                .post("/api/v1/auth/register").then().statusCode(201).extract().path("id");
    }

    private static Response post(String path, Map<String, ?> body) {
        return given().contentType(ContentType.JSON).body(body).post(path);
    }

    private static boolean verified(Login login) {
        return given().header("Authorization", login.bearer()).get("/api/v1/me").then().statusCode(200).extract().path("emailVerified");
    }

    // ---------------------------------------------------------------- verification

    @Test
    void signUpMailsALinkThatVerifiesTheAddressOnce() {
        String email = freshEmail();
        register(email, "en");
        Mail mail = FakeServices.smtp().awaitNewest(email);
        assertEquals("Confirm your PokeDnD e-mail address", mail.subject());
        assertTrue(mail.body().contains("Hi Dani"), mail.body());
        assertTrue(mail.link().startsWith("http://app.test/verify-email?token="), mail.link());
        assertTrue(!verified(TestAccounts.login(email)));

        post("/api/v1/auth/verify-email", Map.of("token", mail.token())).then().statusCode(204);
        assertTrue(verified(TestAccounts.login(email)));

        // the link cannot be used a second time
        post("/api/v1/auth/verify-email", Map.of("token", mail.token())).then().statusCode(400).body("code", equalTo("INVALID_TOKEN"));
    }

    @Test
    void theMailIsInThePersonsLanguage() {
        String email = freshEmail();
        register(email, "pt");
        Mail mail = FakeServices.smtp().awaitNewest(email);
        assertEquals("Confirme o seu endereço de e-mail do PokeDnD", mail.subject());
        assertTrue(mail.body().contains("Olá Dani"), mail.body());
        assertTrue(mail.body().contains("expira em 24 horas"), mail.body());
    }

    @Test
    void anExpiredVerificationLinkIsRefusedAndTheAddressStaysUnverified() {
        String email = freshEmail();
        String id = register(email, "en");
        String token = "expired-token-" + UUID.randomUUID();
        accounts.saveEmailToken(new EmailToken(UUID.randomUUID(), UUID.fromString(id), AccountRepository.TOKEN_VERIFY, TokenService.hash(token),
                Instant.now().minus(Duration.ofMinutes(1)), null));
        post("/api/v1/auth/verify-email", Map.of("token", token)).then().statusCode(400).body("code", equalTo("TOKEN_EXPIRED"));
        assertTrue(!verified(TestAccounts.login(email)));
    }

    @Test
    void madeUpEmptyAndWrongKindOfTokensAreRefused() {
        post("/api/v1/auth/verify-email", Map.of("token", "made-up")).then().statusCode(400).body("code", equalTo("INVALID_TOKEN"));
        post("/api/v1/auth/verify-email", Map.of("token", "")).then().statusCode(400).body("code", equalTo("INVALID_TOKEN"));
        given().contentType(ContentType.JSON).post("/api/v1/auth/verify-email").then().statusCode(400).body("code", equalTo("BAD_REQUEST"));

        // a password-reset link does not verify an address, and a verification link does not reset a password
        String email = freshEmail();
        register(email, "en");
        Mail verifyMail = FakeServices.smtp().awaitNewest(email);
        post("/api/v1/auth/reset", Map.of("token", verifyMail.token(), "password", NEW_PASSWORD)).then().statusCode(400).body("code", equalTo("INVALID_TOKEN"));
        post("/api/v1/auth/forgot", Map.of("email", email)).then().statusCode(204);
        Mail resetMail = FakeServices.smtp().await(email, 2).get(1);
        post("/api/v1/auth/verify-email", Map.of("token", resetMail.token())).then().statusCode(400).body("code", equalTo("INVALID_TOKEN"));
    }

    @Test
    void askingAgainMakesTheEarlierVerificationLinkUseless() {
        String email = freshEmail();
        register(email, "en");
        Mail first = FakeServices.smtp().awaitNewest(email);
        post("/api/v1/auth/resend-verification", Map.of("email", email)).then().statusCode(204);
        Mail second = FakeServices.smtp().await(email, 2).get(1);
        post("/api/v1/auth/verify-email", Map.of("token", first.token())).then().statusCode(400);
        post("/api/v1/auth/verify-email", Map.of("token", second.token())).then().statusCode(204);
        // an address that is verified already gets no more mail, and nobody learns whether an address has an account
        post("/api/v1/auth/resend-verification", Map.of("email", email)).then().statusCode(204);
        post("/api/v1/auth/resend-verification", Map.of("email", freshEmail())).then().statusCode(204);
        assertEquals(2, FakeServices.smtp().to(email).size());
    }

    // ---------------------------------------------------------------- password reset

    @Test
    void forgotMailsAResetLinkThatWorksOnce() {
        String email = freshEmail();
        register(email, "en");
        post("/api/v1/auth/forgot", Map.of("email", email.toUpperCase())).then().statusCode(204);
        Mail mail = FakeServices.smtp().await(email, 2).get(1);
        assertEquals("Reset your PokeDnD password", mail.subject());
        assertTrue(mail.link().startsWith("http://app.test/reset-password?token="), mail.link());

        post("/api/v1/auth/reset", Map.of("token", mail.token(), "password", NEW_PASSWORD)).then().statusCode(204);
        // the new password works, the old one does not, and reading the mail counted as verifying the address
        Response oldPassword = given().contentType(ContentType.JSON).body(Map.of("email", email, "password", TestAccounts.PASSWORD)).post("/api/v1/auth/login");
        oldPassword.then().statusCode(401);
        Response login = given().contentType(ContentType.JSON).body(Map.of("email", email, "password", NEW_PASSWORD)).post("/api/v1/auth/login");
        login.then().statusCode(200).body("user.emailVerified", equalTo(true));

        // the link cannot be used again
        post("/api/v1/auth/reset", Map.of("token", mail.token(), "password", "yet another long password")).then().statusCode(400)
                .body("code", equalTo("INVALID_TOKEN"));
    }

    @Test
    void anExpiredResetLinkIsRefused() {
        String email = freshEmail();
        String id = register(email, "en");
        String token = "expired-reset-" + UUID.randomUUID();
        accounts.saveEmailToken(new EmailToken(UUID.randomUUID(), UUID.fromString(id), AccountRepository.TOKEN_RESET, TokenService.hash(token),
                Instant.now().minus(Duration.ofSeconds(5)), null));
        post("/api/v1/auth/reset", Map.of("token", token, "password", NEW_PASSWORD)).then().statusCode(400).body("code", equalTo("TOKEN_EXPIRED"));
        TestAccounts.login(email); // the old password still works
    }

    @Test
    void aWeakNewPasswordIsRefusedWithoutUsingUpTheLink() {
        String email = freshEmail();
        register(email, "en");
        post("/api/v1/auth/forgot", Map.of("email", email)).then().statusCode(204);
        Mail mail = FakeServices.smtp().await(email, 2).get(1);
        post("/api/v1/auth/reset", Map.of("token", mail.token(), "password", "short")).then().statusCode(400).body("code", equalTo("WEAK_PASSWORD"));
        post("/api/v1/auth/reset", Map.of("token", mail.token(), "password", NEW_PASSWORD)).then().statusCode(204);
    }

    @Test
    void forgotLooksTheSameForUnknownAddressesAndSendsNothingToThem() {
        String unknown = freshEmail();
        post("/api/v1/auth/forgot", Map.of("email", unknown)).then().statusCode(204);
        assertEquals(0, FakeServices.smtp().to(unknown).size());
        post("/api/v1/auth/forgot", Map.of("email", "not an address")).then().statusCode(400).body("code", equalTo("INVALID_EMAIL"));
    }

    @Test
    void aNewResetLinkMakesTheEarlierOneUseless() {
        String email = freshEmail();
        register(email, "en");
        post("/api/v1/auth/forgot", Map.of("email", email)).then().statusCode(204);
        post("/api/v1/auth/forgot", Map.of("email", email)).then().statusCode(204);
        Mail first = FakeServices.smtp().await(email, 3).get(1);
        Mail second = FakeServices.smtp().await(email, 3).get(2);
        post("/api/v1/auth/reset", Map.of("token", first.token(), "password", NEW_PASSWORD)).then().statusCode(400);
        post("/api/v1/auth/reset", Map.of("token", second.token(), "password", NEW_PASSWORD)).then().statusCode(204);
    }

    @Test
    void resettingThePasswordEndsEveryOldLogin() {
        String email = freshEmail();
        register(email, "en");
        Login a = TestAccounts.login(email);
        Login b = TestAccounts.login(email);
        given().header("Authorization", a.bearer()).get("/api/v1/me/sessions").then().statusCode(200).body("size()", equalTo(2));

        post("/api/v1/auth/forgot", Map.of("email", email)).then().statusCode(204);
        Mail mail = FakeServices.smtp().await(email, 2).get(1);
        post("/api/v1/auth/reset", Map.of("token", mail.token(), "password", NEW_PASSWORD)).then().statusCode(204);

        given().cookie("pokednd_refresh", a.refreshCookie()).post("/api/v1/auth/refresh").then().statusCode(401);
        given().cookie("pokednd_refresh", b.refreshCookie()).post("/api/v1/auth/refresh").then().statusCode(401);
        // a fresh login has no sessions but its own
        Response fresh = given().contentType(ContentType.JSON).body(Map.of("email", email, "password", NEW_PASSWORD)).post("/api/v1/auth/login");
        given().header("Authorization", "Bearer " + fresh.path("accessToken")).get("/api/v1/me/sessions").then().statusCode(200).body("size()", equalTo(1));
    }

    // ---------------------------------------------------------------- rate limits

    @Test
    void onlyAFewMailsAreSentToOneAddressPerHour() {
        String email = freshEmail();
        register(email, "en"); // mail 1 is the verification mail; resets are counted separately
        for (int i = 0; i < 5; i++) {
            post("/api/v1/auth/forgot", Map.of("email", email)).then().statusCode(204);
        }
        // 1 verification + 3 resets; the 4th and 5th reset were skipped without telling the caller
        assertEquals(4, FakeServices.smtp().await(email, 4).size());
        assertEquals(4, FakeServices.smtp().to(email).size());
    }

    @Test
    void oneAddressCannotHammerTheEmailEndpoints() {
        for (int i = 0; i < 30; i++) {
            post("/api/v1/auth/forgot", Map.of("email", freshEmail())).then().statusCode(204);
        }
        post("/api/v1/auth/forgot", Map.of("email", freshEmail())).then().statusCode(429).body("code", equalTo("RATE_LIMITED"));
        post("/api/v1/auth/verify-email", Map.of("token", "x")).then().statusCode(429);
        post("/api/v1/auth/reset", Map.of("token", "x", "password", NEW_PASSWORD)).then().statusCode(429);
        post("/api/v1/auth/resend-verification", Map.of("email", freshEmail())).then().statusCode(429);
    }
}
