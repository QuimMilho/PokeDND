/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Map;
import java.util.UUID;
import net.quimmilho.pokednd.api.TestAccounts;
import net.quimmilho.pokednd.api.TestAccounts.Login;
import net.quimmilho.pokednd.api.persistence.TestDb;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.keys.HmacKey;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Sign up, log in, refresh, log out: the success paths and the failures. Runs against a real PostgreSQL. */
@QuarkusTest
@Tag("database")
class AuthFlowTest {

    @Inject
    RateLimiter limiter;

    @Inject
    TestDb db;

    @ConfigProperty(name = "pokednd.auth.jwt-private-key")
    String privateKeyPem;

    @Inject
    AccountRepository accounts;

    @Inject
    AuthService auth;

    @BeforeEach
    void freshLimits() {
        limiter.reset();
    }

    private static Response login(String email, String password) {
        return given().contentType(ContentType.JSON).body(Map.of("email", email, "password", password)).post("/api/v1/auth/login");
    }

    private static Response refresh(String cookie) {
        return given().cookie("pokednd_refresh", cookie).post("/api/v1/auth/refresh");
    }

    // ---------------------------------------------------------------- the good path

    @Test
    void signUpLogInAndAskWhoIAm() {
        String email = "Anna." + UUID.randomUUID() + "@Example.test";
        given().contentType(ContentType.JSON).body(Map.of("email", email, "password", TestAccounts.PASSWORD, "displayName", "Anna", "locale", "pt"))
                .post("/api/v1/auth/register").then().statusCode(201)
                .body("email", equalTo(email.toLowerCase())).body("displayName", equalTo("Anna")).body("locale", equalTo("pt"))
                .body("siteRole", equalTo("none")).body("emailVerified", equalTo(false)).body("id", notNullValue())
                .body("passwordHash", org.hamcrest.Matchers.nullValue());

        // e-mail capitals do not matter when logging in
        Response r = login(email.toUpperCase(), TestAccounts.PASSWORD);
        r.then().statusCode(200).body("tokenType", equalTo("Bearer")).body("expiresIn", equalTo(900)).body("accessToken", notNullValue())
                .body("user.displayName", equalTo("Anna")).header("Cache-Control", containsString("no-store"));

        given().header("Authorization", "Bearer " + r.path("accessToken")).get("/api/v1/me").then().statusCode(200)
                .body("email", equalTo(email.toLowerCase())).body("id", equalTo(r.path("user.id")));
    }

    @Test
    void theProfileAndPreferencesCanBeChanged() {
        Login me = TestAccounts.newUser();
        given().header("Authorization", me.bearer()).contentType(ContentType.JSON).body(Map.of("theme", "dark", "locale", "pt"))
                .patch("/api/v1/me").then().statusCode(200).body("theme", equalTo("dark")).body("locale", equalTo("pt"));
        // what was left out stayed; the change is kept
        given().header("Authorization", me.bearer()).contentType(ContentType.JSON).body(Map.of("displayName", "  Ana Maria "))
                .patch("/api/v1/me").then().statusCode(200).body("displayName", equalTo("Ana Maria")).body("theme", equalTo("dark"));
        given().header("Authorization", me.bearer()).get("/api/v1/me").then().statusCode(200).body("displayName", equalTo("Ana Maria"))
                .body("theme", equalTo("dark")).body("locale", equalTo("pt"));

        // values that make no sense are refused
        given().header("Authorization", me.bearer()).contentType(ContentType.JSON).body(Map.of("theme", "neon")).patch("/api/v1/me").then()
                .statusCode(400).body("code", equalTo("INVALID_THEME"));
        given().header("Authorization", me.bearer()).contentType(ContentType.JSON).body(Map.of("locale", "xx")).patch("/api/v1/me").then()
                .statusCode(400).body("code", equalTo("INVALID_LOCALE"));
        given().header("Authorization", me.bearer()).contentType(ContentType.JSON).body(Map.of("displayName", "x".repeat(41))).patch("/api/v1/me")
                .then().statusCode(400).body("code", equalTo("INVALID_NAME"));
        given().contentType(ContentType.JSON).body(Map.of("theme", "dark")).patch("/api/v1/me").then().statusCode(401);
    }

    @Test
    void theRefreshTokenLivesOnlyInAnHttpOnlyCookie() {
        Response r = login(TestAccounts.newUser().email(), TestAccounts.PASSWORD);
        String setCookie = r.header("Set-Cookie");
        assertTrue(setCookie.startsWith("pokednd_refresh="), setCookie);
        assertTrue(setCookie.contains("HttpOnly"), setCookie);
        assertTrue(setCookie.contains("SameSite=Strict"), setCookie);
        assertTrue(setCookie.contains("Path=/api/v1/auth"), setCookie);
        assertTrue(!r.asString().contains(r.getCookie("pokednd_refresh")), "the refresh token must not be in the JSON body");
    }

    @Test
    void refreshGivesNewTokensAndEachRefreshTokenWorksOnce() {
        Login first = TestAccounts.newUser();
        Response r = refresh(first.refreshCookie());
        r.then().statusCode(200).body("accessToken", notNullValue()).body("user.id", equalTo(first.id()));
        String second = r.getCookie("pokednd_refresh");
        assertNotEquals(first.refreshCookie(), second);

        // the new access token works
        given().header("Authorization", "Bearer " + r.path("accessToken")).get("/api/v1/me").then().statusCode(200);
        // the new refresh token works too
        Response third = refresh(second);
        third.then().statusCode(200);
        assertNotEquals(second, third.getCookie("pokednd_refresh"));
    }

    @Test
    void reusingAnOldRefreshTokenClosesTheWholeLogin() {
        Login first = TestAccounts.newUser();
        Response r = refresh(first.refreshCookie());
        r.then().statusCode(200);
        String second = r.getCookie("pokednd_refresh");

        // somebody shows the first token again
        refresh(first.refreshCookie()).then().statusCode(401).body("code", equalTo("REFRESH_TOKEN_REUSED"));
        // the newer token of the same login is dead too
        refresh(second).then().statusCode(401);
        // but a fresh login of the same person is fine
        login(first.email(), TestAccounts.PASSWORD).then().statusCode(200);
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        Login me = TestAccounts.newUser();
        given().cookie("pokednd_refresh", me.refreshCookie()).post("/api/v1/auth/logout").then().statusCode(204);
        refresh(me.refreshCookie()).then().statusCode(401);
        // logging out again, or with no cookie, is not an error
        given().cookie("pokednd_refresh", me.refreshCookie()).post("/api/v1/auth/logout").then().statusCode(204);
        given().post("/api/v1/auth/logout").then().statusCode(204);
    }

    @Test
    void aMissingOrMadeUpRefreshTokenIsRefused() {
        given().post("/api/v1/auth/refresh").then().statusCode(401).body("code", equalTo("INVALID_REFRESH_TOKEN"));
        refresh("made-up-token").then().statusCode(401).body("code", equalTo("INVALID_REFRESH_TOKEN"));
    }

    // ---------------------------------------------------------------- the failures

    @Test
    void wrongPasswordAndUnknownAccountLookTheSame() {
        Login me = TestAccounts.newUser();
        Response wrong = login(me.email(), "not the password at all");
        Response unknown = login("nobody-" + UUID.randomUUID() + "@example.test", TestAccounts.PASSWORD);
        wrong.then().statusCode(401).body("code", equalTo("INVALID_CREDENTIALS"));
        unknown.then().statusCode(401).body("code", equalTo("INVALID_CREDENTIALS"));
        assertEquals((String) wrong.path("message"), unknown.path("message"));
        assertEquals(null, wrong.getCookie("pokednd_refresh"));
    }

    @Test
    void theSameAddressCannotSignUpTwice() {
        Login me = TestAccounts.newUser();
        given().log().ifValidationFails().contentType(ContentType.JSON).body(Map.of("email", me.email().toUpperCase(), "password", TestAccounts.PASSWORD))
                .post("/api/v1/auth/register").then().statusCode(409).body("code", equalTo("EMAIL_TAKEN"));
    }

    @Test
    void badSignUpDataIsRefusedWithAClearCode() {
        post("/api/v1/auth/register", Map.of("email", "not-an-email", "password", TestAccounts.PASSWORD)).then().statusCode(400)
                .body("code", equalTo("INVALID_EMAIL"));
        post("/api/v1/auth/register", Map.of("email", "a@example.test", "password", "short")).then().statusCode(400)
                .body("code", equalTo("WEAK_PASSWORD"));
        post("/api/v1/auth/register", Map.of("email", "a@example.test", "password", TestAccounts.PASSWORD, "locale", "xx")).then()
                .statusCode(400).body("code", equalTo("INVALID_LOCALE"));
        post("/api/v1/auth/register", Map.of("email", "a@example.test", "password", TestAccounts.PASSWORD, "displayName", "x".repeat(41)))
                .then().statusCode(400).body("code", equalTo("INVALID_NAME"));
    }

    private static Response post(String path, Map<String, ?> body) {
        return given().contentType(ContentType.JSON).body(body).post(path);
    }

    // ---------------------------------------------------------------- passwords in the table

    @Test
    void passwordsAreStoredOnlyAsArgon2idHashes() throws Exception {
        Login me = TestAccounts.newUser();
        String stored = db.passwordHashOf(me.email());
        assertTrue(stored != null && stored.startsWith("$argon2id$v=19$"), stored);
        assertTrue(!stored.contains(TestAccounts.PASSWORD));
        // no column anywhere holds the plain password
        assertEquals(0, db.accountsHolding(TestAccounts.PASSWORD));
        // refresh tokens are stored as 64 hex characters (a hash), never as the token that the browser holds
        assertTrue(!db.hasRefreshTokenWithHash(me.refreshCookie()), "the refresh token itself must not be in the table");
    }

    // ---------------------------------------------------------------- access tokens

    @Test
    void badAccessTokensAreRefused() throws Exception {
        Login me = TestAccounts.newUser();
        PrivateKey key = PemKeys.privateKey(privateKeyPem);
        given().get("/api/v1/me").then().statusCode(401).body("code", equalTo("UNAUTHENTICATED"));
        given().header("Authorization", "Bearer garbage").get("/api/v1/me").then().statusCode(401).body("code", equalTo("INVALID_TOKEN"));
        // the right key, but expired
        given().header("Authorization", "Bearer " + rsaToken(key, -60, me.id())).get("/api/v1/me").then().statusCode(401)
                .body("code", equalTo("INVALID_TOKEN"));
        // not expired, but signed with another key
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair other = gen.generateKeyPair();
        given().header("Authorization", "Bearer " + rsaToken(other.getPrivate(), 600, me.id())).get("/api/v1/me").then().statusCode(401)
                .body("code", equalTo("INVALID_TOKEN"));
        // the algorithm trick: an HS256 token "signed" with the public key as if it were a secret must not be accepted
        byte[] publicKeyBytes = PemKeys.publicFrom(key).getEncoded();
        given().header("Authorization", "Bearer " + hmacToken(publicKeyBytes, 600, me.id())).get("/api/v1/me").then().statusCode(401)
                .body("code", equalTo("INVALID_TOKEN"));
        // a token with no signature at all
        String unsigned = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"iss\":\"pokednd\",\"sub\":\"" + me.id() + "\",\"exp\":9999999999}")
                        .getBytes(StandardCharsets.UTF_8)) + ".";
        given().header("Authorization", "Bearer " + unsigned).get("/api/v1/me").then().statusCode(401);
        // the good one still works
        given().header("Authorization", "Bearer " + rsaToken(key, 600, me.id())).get("/api/v1/me").then().statusCode(200);
        // a bad token on an open endpoint is simply ignored
        given().header("Authorization", "Bearer garbage").get("/api/v1/data/types").then().statusCode(anyOf200Or503());
    }

    private static org.hamcrest.Matcher<Integer> anyOf200Or503() {
        return org.hamcrest.Matchers.anyOf(equalTo(200), equalTo(503));
    }

    private static String rsaToken(PrivateKey key, int secondsFromNow, String userId) throws Exception {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims(secondsFromNow, userId));
        jws.setKey(key);
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        return jws.getCompactSerialization();
    }

    private static String hmacToken(byte[] secret, int secondsFromNow, String userId) throws Exception {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims(secondsFromNow, userId));
        jws.setKey(new HmacKey(secret));
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.HMAC_SHA256);
        jws.setDoKeyValidation(false);
        return jws.getCompactSerialization();
    }

    private static String claims(int secondsFromNow, String userId) {
        JwtClaims claims = new JwtClaims();
        claims.setIssuer("pokednd");
        claims.setSubject(userId);
        NumericDate exp = NumericDate.now();
        exp.addSeconds(secondsFromNow);
        claims.setExpirationTime(exp);
        claims.setStringClaim("role", "none");
        return claims.toJson();
    }

    // ---------------------------------------------------------------- site admin

    @Test
    void theFirstSiteAdminIsCreatedAtStartAndItsAddressCannotBeTaken() {
        Login admin = TestAccounts.admin();
        given().header("Authorization", admin.bearer()).get("/api/v1/me").then().statusCode(200).body("siteRole", equalTo("siteAdmin"))
                .body("emailVerified", equalTo(true));
        given().header("Authorization", TestAccounts.newUser().bearer()).get("/api/v1/me").then().statusCode(200)
                .body("siteRole", not(equalTo("siteAdmin")));
        // nobody can sign up with the admin's address
        given().contentType(ContentType.JSON).body(Map.of("email", TestAccounts.ADMIN_EMAIL, "password", "some other long password"))
                .post("/api/v1/auth/register").then().statusCode(409);
    }

    @Test
    void bootstrappingAgainDoesNothingWhileAnAdminExists() {
        long admins = accounts.countSiteAdmins();
        long users = accounts.countUsers();
        assertTrue(admins >= 1);
        auth.bootstrapAdmin();
        auth.bootstrapAdmin();
        assertEquals(admins, accounts.countSiteAdmins());
        assertEquals(users, accounts.countUsers());
    }

    // ---------------------------------------------------------------- rate limits

    @Test
    void tooManyWrongPasswordsForOneAddressAreStoppedEvenWithTheRightPasswordAfterwards() {
        Login me = TestAccounts.newUser();
        limiter.reset();
        for (int i = 0; i < 5; i++) {
            login(me.email(), "wrong password " + i).then().statusCode(401);
        }
        login(me.email(), TestAccounts.PASSWORD).then().statusCode(429).body("code", equalTo("RATE_LIMITED"));
        limiter.reset();
        login(me.email(), TestAccounts.PASSWORD).then().statusCode(200);
    }

    @Test
    void aSuccessfulLoginForgetsEarlierMistakes() {
        Login me = TestAccounts.newUser();
        limiter.reset();
        for (int i = 0; i < 4; i++) {
            login(me.email(), "wrong password " + i).then().statusCode(401);
        }
        login(me.email(), TestAccounts.PASSWORD).then().statusCode(200);
        for (int i = 0; i < 4; i++) {
            login(me.email(), "wrong again " + i).then().statusCode(401);
        }
    }

    @Test
    void oneAddressCannotTryLoginsWithoutEnd() {
        for (int i = 0; i < 20; i++) {
            login("nobody-" + i + "@example.test", "whatever password").then().statusCode(401);
        }
        login("nobody-x@example.test", "whatever password").then().statusCode(429);
    }

    @Test
    void signUpsAreLimitedPerAddress() {
        for (int i = 0; i < 10; i++) {
            post("/api/v1/auth/register", Map.of("email", "bad-" + i, "password", TestAccounts.PASSWORD)).then().statusCode(400);
        }
        post("/api/v1/auth/register", Map.of("email", "bad-x", "password", TestAccounts.PASSWORD)).then().statusCode(429)
                .body("code", equalTo("RATE_LIMITED"));
    }
}
