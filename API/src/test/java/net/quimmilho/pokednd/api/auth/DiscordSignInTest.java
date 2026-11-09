/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import net.quimmilho.pokednd.api.FakeDiscord;
import net.quimmilho.pokednd.api.FakeServices;
import net.quimmilho.pokednd.api.TestAccounts;
import net.quimmilho.pokednd.api.TestAccounts.Login;
import net.quimmilho.pokednd.api.domain.accounts.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Sign-in with Discord, linking and unlinking, against a stub Discord (phase 28). Runs against a real PostgreSQL. */
@QuarkusTest
@Tag("database")
class DiscordSignInTest {

    private static final String BASE = "http://app.test";
    private static final String CALLBACK = BASE + "/api/v1/auth/oauth/discord/callback";

    @Inject
    RateLimiter limiter;

    @Inject
    AccountRepository accounts;

    @BeforeEach
    void freshLimits() {
        limiter.reset();
    }

    private static FakeDiscord discord() {
        return FakeServices.discord();
    }

    /** What the API gave when the browser was sent to Discord. */
    record Started(String location, String state, String stateCookie) {
    }

    private static Started start() {
        Response r = given().redirects().follow(false).get("/api/v1/auth/oauth/discord/start");
        r.then().statusCode(302);
        return started(r.header("Location"), r.getCookie("pokednd_oauth_state"));
    }

    private static Started started(String location, String cookie) {
        Map<String, String> q = query(URI.create(location));
        return new Started(location, q.get("state"), cookie);
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> q = new HashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            int i = pair.indexOf('=');
            q.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
        }
        return q;
    }

    private static Response callback(String code, String state, String cookieState) {
        var req = given().redirects().follow(false);
        if (cookieState != null) {
            req = req.cookie("pokednd_oauth_state", cookieState);
        }
        var params = new HashMap<String, Object>();
        if (code != null) {
            params.put("code", code);
        }
        if (state != null) {
            params.put("state", state);
        }
        return req.queryParams(params).get("/api/v1/auth/oauth/discord/callback");
    }

    /** Registers a Discord account in the stub and walks the whole login. */
    private static Response discordLogin(String discordId, String email, boolean verified) {
        String code = "code-" + UUID.randomUUID();
        discord().account(code, discordId, "Misty", email, verified);
        Started s = start();
        return callback(code, s.state(), s.stateCookie());
    }

    private static String newDiscordId() {
        return "d-" + UUID.randomUUID();
    }

    private static String freshEmail() {
        return "discord-" + UUID.randomUUID() + "@example.test";
    }

    private static Response refresh(String cookie) {
        return given().cookie("pokednd_refresh", cookie).post("/api/v1/auth/refresh");
    }

    private static Response methods(Login who) {
        return given().header("Authorization", who.bearer()).get("/api/v1/me/identities");
    }

    // ---------------------------------------------------------------- starting

    @Test
    void startSendsTheBrowserToDiscordWithARandomStateAndABindingCookie() {
        Response r = given().redirects().follow(false).get("/api/v1/auth/oauth/discord/start");
        r.then().statusCode(302);
        URI location = URI.create(r.header("Location"));
        assertTrue(r.header("Location").startsWith(discord().authorizeUrl() + "?"), r.header("Location"));
        Map<String, String> q = query(location);
        assertEquals(FakeDiscord.CLIENT_ID, q.get("client_id"));
        assertEquals("code", q.get("response_type"));
        assertEquals("identify email", q.get("scope"));
        assertEquals(CALLBACK, q.get("redirect_uri"));
        assertTrue(q.get("state").length() >= 40, "the state must be long and random");
        assertFalse(r.header("Location").contains(FakeDiscord.CLIENT_SECRET), "the secret must never be in the address");

        String cookie = r.header("Set-Cookie");
        assertTrue(cookie.startsWith("pokednd_oauth_state=" + q.get("state")), cookie);
        assertTrue(cookie.contains("HttpOnly"), cookie);
        assertTrue(cookie.contains("SameSite=Lax"), cookie);
        assertTrue(cookie.contains("Path=/api/v1/auth/oauth"), cookie);
        // every start gets its own state
        assertFalse(q.get("state").equals(query(URI.create(start().location())).get("state")));
    }

    @Test
    void theLoginScreenCanAskWhichProvidersExist() {
        given().get("/api/v1/auth/oauth/providers").then().statusCode(200).body("providers", equalTo(List.of("discord")));
        given().redirects().follow(false).get("/api/v1/auth/oauth/google/start").then().statusCode(404).body("code", equalTo("OAUTH_DISABLED"));
    }

    // ---------------------------------------------------------------- a new user and a returning user

    @Test
    void aNewDiscordUserGetsAnAccountAndIsLoggedIn() {
        String email = freshEmail();
        String discordId = newDiscordId();
        Response r = discordLogin(discordId, email.toUpperCase(), true);
        r.then().statusCode(302).header("Location", equalTo(BASE + "/login?oauth=ok"));
        assertEquals(CALLBACK, discord().lastRedirectUri(), "the same redirect address must be used at both steps");
        String refreshCookie = r.getCookie("pokednd_refresh");
        assertNotNull(refreshCookie);
        assertTrue(r.header("Set-Cookie").contains("pokednd_oauth_state=;") || r.headers().getValues("Set-Cookie").stream().anyMatch(c -> c.startsWith("pokednd_oauth_state=")),
                "the state cookie is cleared");

        Response session = refresh(refreshCookie);
        session.then().statusCode(200).body("user.email", equalTo(email)).body("user.emailVerified", equalTo(true))
                .body("user.displayName", equalTo("Misty G")).body("user.locale", equalTo("pt")).body("user.siteRole", equalTo("none"));
        Login me = new Login(email, session.path("user.id"), session.path("accessToken"), session.getCookie("pokednd_refresh"));
        // Discord is the only way in: no password was made
        methods(me).then().statusCode(200).body("provider", equalTo(List.of("discord")));
        given().contentType(ContentType.JSON).body(Map.of("email", email, "password", "any password at all")).post("/api/v1/auth/login").then().statusCode(401);
    }

    @Test
    void aReturningDiscordUserGetsTheSameAccount() {
        String discordId = newDiscordId();
        String email = freshEmail();
        Response first = discordLogin(discordId, email, true);
        String userId = refresh(first.getCookie("pokednd_refresh")).path("user.id");
        long users = accounts.countUsers();

        // even when the address in Discord has changed since
        Response second = discordLogin(discordId, freshEmail(), true);
        second.then().statusCode(302).header("Location", equalTo(BASE + "/login?oauth=ok"));
        assertEquals(userId, refresh(second.getCookie("pokednd_refresh")).path("user.id"));
        assertEquals(users, accounts.countUsers());
    }

    // ---------------------------------------------------------------- an account with the same address

    @Test
    void discordIsLinkedToTheAccountWithTheSameVerifiedAddress() {
        Login existing = TestAccounts.newUser();
        accounts.markEmailVerified(java.util.UUID.fromString(existing.id()));
        Response r = discordLogin(newDiscordId(), existing.email(), true);
        r.then().statusCode(302).header("Location", equalTo(BASE + "/login?oauth=ok"));
        assertEquals(existing.id(), refresh(r.getCookie("pokednd_refresh")).path("user.id"));
        // the password still works, and both ways are listed
        TestAccounts.login(existing.email());
        methods(existing).then().body("provider", equalTo(List.of("password", "discord")));
    }

    @Test
    void linkingToAnAccountWhoseAddressWasNeverVerifiedRemovesItsPassword() {
        // somebody signs up with an address that is not theirs; the real owner then signs in with Discord
        Login squatter = TestAccounts.newUser();
        Response r = discordLogin(newDiscordId(), squatter.email(), true);
        r.then().statusCode(302).header("Location", equalTo(BASE + "/login?oauth=ok"));
        Response session = refresh(r.getCookie("pokednd_refresh"));
        assertEquals(squatter.id(), session.path("user.id"));

        given().contentType(ContentType.JSON).body(Map.of("email", squatter.email(), "password", TestAccounts.PASSWORD)).post("/api/v1/auth/login")
                .then().statusCode(401);
        refresh(squatter.refreshCookie()).then().statusCode(401); // the squatter's open logins ended too
        given().header("Authorization", "Bearer " + session.path("accessToken")).get("/api/v1/me").then()
                .body("emailVerified", equalTo(true));
    }

    // ---------------------------------------------------------------- things that go wrong

    @Test
    void anUnverifiedOrMissingDiscordAddressIsRefusedAndNoAccountIsMade() {
        long users = accounts.countUsers();
        String email = freshEmail();
        Response unverified = discordLogin(newDiscordId(), email, false);
        unverified.then().statusCode(302).header("Location", equalTo(BASE + "/login?oauth=error&code=DISCORD_EMAIL_UNVERIFIED"));
        assertNull(unverified.getCookie("pokednd_refresh"));
        discordLogin(newDiscordId(), null, true).then().statusCode(302).header("Location", containsString("code=DISCORD_EMAIL_UNVERIFIED"));
        assertEquals(users, accounts.countUsers());
        assertTrue(accounts.findUserByEmail(email).isEmpty());
    }

    @Test
    void aWrongOrMissingOrReusedStateIsRefusedBeforeDiscordIsAsked() {
        int asked = discord().tokenRequests();
        String code = "code-" + UUID.randomUUID();
        discord().account(code, newDiscordId(), "Brock", freshEmail(), true);
        Started s = start();

        // the cookie of another browser, no cookie, no state, an invented state
        callback(code, s.state(), "another-browsers-state").then().statusCode(302).header("Location", equalTo(BASE + "/login?oauth=error&code=INVALID_STATE"));
        callback(code, s.state(), null).then().statusCode(302).header("Location", containsString("code=INVALID_STATE"));
        callback(code, null, s.stateCookie()).then().statusCode(302).header("Location", containsString("code=INVALID_STATE"));
        callback(code, "invented", "invented").then().statusCode(302).header("Location", containsString("code=INVALID_STATE"));
        assertEquals(asked, discord().tokenRequests(), "Discord must not be asked with an unchecked state");

        // the right state works, but only once
        callback(code, s.state(), s.stateCookie()).then().statusCode(302).header("Location", equalTo(BASE + "/login?oauth=ok"));
        discord().account(code, newDiscordId(), "Brock", freshEmail(), true);
        callback(code, s.state(), s.stateCookie()).then().statusCode(302).header("Location", containsString("code=INVALID_STATE"));
    }

    @Test
    void deniedConsentSendsThePersonBackWithoutALogin() {
        Started s = start();
        Response r = given().redirects().follow(false).cookie("pokednd_oauth_state", s.stateCookie())
                .queryParams("error", "access_denied", "error_description", "The resource owner denied the request", "state", s.state())
                .get("/api/v1/auth/oauth/discord/callback");
        r.then().statusCode(302).header("Location", equalTo(BASE + "/login?oauth=error&code=OAUTH_DENIED"));
        assertNull(r.getCookie("pokednd_refresh"));
    }

    @Test
    void aCodeThatDiscordRefusesFailsCleanly() {
        Started s = start();
        Response r = callback("code-discord-does-not-know", s.state(), s.stateCookie());
        r.then().statusCode(302).header("Location", equalTo(BASE + "/login?oauth=error&code=OAUTH_FAILED"));
        assertNull(r.getCookie("pokednd_refresh"));
    }

    // ---------------------------------------------------------------- linking and unlinking

    private static String linkStart(Login who) {
        Response r = given().header("Authorization", who.bearer()).post("/api/v1/me/identities/discord/link");
        r.then().statusCode(200);
        return r.getCookie("pokednd_oauth_state") + "|" + query(URI.create(r.path("url"))).get("state");
    }

    private static Response linkWithDiscord(Login who, String discordId) {
        String code = "code-" + UUID.randomUUID();
        discord().account(code, discordId, "Ash", "ash-" + UUID.randomUUID() + "@example.test", true);
        String[] cookieAndState = linkStart(who).split("\\|");
        return callback(code, cookieAndState[1], cookieAndState[0]);
    }

    @Test
    void aSignedInUserCanLinkAndUnlinkDiscord() {
        Login me = TestAccounts.newUser();
        String discordId = newDiscordId();
        linkWithDiscord(me, discordId).then().statusCode(302).header("Location", equalTo(BASE + "/account?oauth=linked"));
        methods(me).then().body("provider", equalTo(List.of("password", "discord")));

        // the linked Discord account now logs in to this account, whatever its address
        Response r = discordLogin(discordId, freshEmail(), true);
        assertEquals(me.id(), refresh(r.getCookie("pokednd_refresh")).path("user.id"));

        given().header("Authorization", me.bearer()).delete("/api/v1/me/identities/discord").then().statusCode(204);
        methods(me).then().body("provider", equalTo(List.of("password")));
        given().header("Authorization", me.bearer()).delete("/api/v1/me/identities/discord").then().statusCode(404);
    }

    @Test
    void aDiscordAccountThatBelongsToSomeoneElseCannotBeLinked() {
        String discordId = newDiscordId();
        Login owner = TestAccounts.newUser();
        linkWithDiscord(owner, discordId).then().statusCode(302);
        Login other = TestAccounts.newUser();
        linkWithDiscord(other, discordId).then().statusCode(302).header("Location", equalTo(BASE + "/account?oauth=error&code=DISCORD_ALREADY_LINKED"));
        methods(other).then().body("provider", equalTo(List.of("password")));
        // and one account holds only one Discord account
        linkWithDiscord(owner, newDiscordId()).then().statusCode(302).header("Location", containsString("code=DISCORD_ALREADY_LINKED"));
    }

    @Test
    void theStateDecidesWhetherTheCallbackIsALoginOrALinking() {
        Login me = TestAccounts.newUser();
        String[] cookieAndState = linkStart(me).split("\\|");
        String code = "code-" + UUID.randomUUID();
        String discordId = newDiscordId();
        discord().account(code, discordId, "Gary", freshEmail(), true);
        // the callback decides by what the state was made for, not by anything in the address
        callback(code, cookieAndState[1], cookieAndState[0]).then().header("Location", equalTo(BASE + "/account?oauth=linked"));
        // so the linking happened to "me", and no new account was made for this Discord id
        assertEquals(me.id(), accounts.findIdentity("discord", discordId).orElseThrow().userId().toString());
    }

    @Test
    void theLastWayToLogInCannotBeRemoved() {
        // an account made by Discord has no password: Discord is its only way in
        String discordId = newDiscordId();
        String email = freshEmail();
        Response r = discordLogin(discordId, email, true);
        Response session = refresh(r.getCookie("pokednd_refresh"));
        Login me = new Login(email, session.path("user.id"), session.path("accessToken"), session.getCookie("pokednd_refresh"));
        given().header("Authorization", me.bearer()).delete("/api/v1/me/identities/discord").then().statusCode(409).body("code", equalTo("LAST_LOGIN_METHOD"));
        methods(me).then().body("provider", equalTo(List.of("discord")));

        // after setting a password (by the reset mail) Discord can go
        given().contentType(ContentType.JSON).body(Map.of("email", email)).post("/api/v1/auth/forgot").then().statusCode(204);
        String token = FakeServices.smtp().awaitNewest(email).token();
        given().contentType(ContentType.JSON).body(Map.of("token", token, "password", "a password for later")).post("/api/v1/auth/reset").then().statusCode(204);
        // the reset ended every login, so ask for a new access token the way the app does: log in
        Response again = given().contentType(ContentType.JSON).body(Map.of("email", email, "password", "a password for later")).post("/api/v1/auth/login");
        again.then().statusCode(200);
        given().header("Authorization", "Bearer " + again.path("accessToken")).delete("/api/v1/me/identities/discord").then().statusCode(204);
        // and now the password is the last way
        given().header("Authorization", "Bearer " + again.path("accessToken")).get("/api/v1/me/identities").then().body("provider", equalTo(List.of("password")));
    }

    @Test
    void unknownProvidersAndAnonymousCallersAreRefused() {
        Login me = TestAccounts.newUser();
        given().header("Authorization", me.bearer()).delete("/api/v1/me/identities/google").then().statusCode(400).body("code", equalTo("UNKNOWN_PROVIDER"));
        given().header("Authorization", me.bearer()).post("/api/v1/me/identities/google/link").then().statusCode(404);
        given().get("/api/v1/me/identities").then().statusCode(401);
        given().post("/api/v1/me/identities/discord/link").then().statusCode(401);
        given().delete("/api/v1/me/identities/discord").then().statusCode(401);
        methods(me).then().body("provider", not(hasItem("discord")));
    }

    // ---------------------------------------------------------------- secrets

    /** Collects everything the API writes to its log while a test runs. */
    private static final class LogCatcher extends Handler {
        final List<String> lines = new ArrayList<>();

        @Override
        public synchronized void publish(LogRecord record) {
            String text = record instanceof org.jboss.logmanager.ExtLogRecord ext ? ext.getFormattedMessage() : record.getMessage();
            StringBuilder sb = new StringBuilder(String.valueOf(text));
            for (Throwable t = record.getThrown(); t != null; t = t.getCause()) {
                sb.append('\n').append(t);
            }
            lines.add(sb.toString());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    @Test
    void noSecretReachesTheLogs() {
        LogCatcher catcher = new LogCatcher();
        catcher.setLevel(java.util.logging.Level.ALL);
        java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        root.addHandler(catcher);
        List<String> secrets = new ArrayList<>();
        secrets.add(FakeDiscord.CLIENT_SECRET);
        secrets.add(Base64.getEncoder().encodeToString((FakeDiscord.CLIENT_ID + ":" + FakeDiscord.CLIENT_SECRET).getBytes(StandardCharsets.UTF_8)));
        try {
            // a good login, a refused code, a bad state, a denied consent, an unverified address and a linking: everything that can log
            String code = "secret-test-code-" + UUID.randomUUID();
            secrets.add(code);
            discord().account(code, newDiscordId(), "Jessie", freshEmail(), true);
            Started s = start();
            callback(code, s.state(), s.stateCookie()).then().statusCode(302);
            Started s2 = start();
            callback("secret-refused-code-" + UUID.randomUUID(), s2.state(), s2.stateCookie()).then().statusCode(302);
            callback("secret-bad-state-code", "bad", "bad").then().statusCode(302);
            discordLogin(newDiscordId(), freshEmail(), false).then().statusCode(302);
            linkWithDiscord(TestAccounts.newUser(), newDiscordId()).then().statusCode(302);
        } finally {
            root.removeHandler(catcher);
        }
        assertFalse(catcher.lines.isEmpty(), "the test should have seen some log lines");
        assertTrue(catcher.lines.stream().anyMatch(l -> l.toLowerCase().contains("discord")), "the Discord client should log what happened");
        synchronized (catcher) {
            for (String line : catcher.lines) {
                for (String secret : secrets) {
                    assertFalse(line.contains(secret), "a secret was logged: " + line);
                }
            }
        }
    }
}
