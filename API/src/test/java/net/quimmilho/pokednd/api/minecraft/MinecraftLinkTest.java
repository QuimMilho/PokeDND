/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.minecraft;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.quimmilho.pokednd.api.TestAccounts;
import net.quimmilho.pokednd.api.TestAccounts.Login;
import net.quimmilho.pokednd.api.auth.Ids;
import net.quimmilho.pokednd.api.auth.RateLimiter;
import net.quimmilho.pokednd.api.domain.minecraft.MinecraftLinkRepository;
import net.quimmilho.pokednd.api.domain.minecraft.MinecraftLinkRepository.LinkCode;
import net.quimmilho.pokednd.api.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Linking Minecraft accounts by code, in both directions (phase 30). Runs against a real PostgreSQL. */
@QuarkusTest
@Tag("database")
class MinecraftLinkTest {

    @Inject
    MinecraftLinkService service;

    @Inject
    MinecraftLinkRepository links;

    @Inject
    RateLimiter limiter;

    @BeforeEach
    void freshLimits() {
        limiter.reset();
    }

    private static Response link(Login who, String code) {
        return given().header("Authorization", who.bearer()).contentType(ContentType.JSON).body(Map.of("code", code)).post("/api/v1/me/minecraft/link");
    }

    private static Response list(Login who) {
        return given().header("Authorization", who.bearer()).get("/api/v1/me/minecraft");
    }

    private static void assertCodeShape(String code) {
        assertEquals(8, code.length(), code);
        for (char c : code.toCharArray()) {
            assertTrue(MinecraftLinkService.ALPHABET.indexOf(c) >= 0, "bad character in " + code);
        }
    }

    // ---------------------------------------------------------------- code made by the mod, typed in the app

    @Test
    void aValidCodeLinksTheMinecraftAccountOnce() {
        Login me = TestAccounts.newUser();
        UUID mc = UUID.randomUUID();
        MinecraftLinkService.IssuedCode issued = service.codeForMinecraft(mc, "Steve");
        assertCodeShape(issued.code());
        // valid for ten minutes
        Duration left = Duration.between(Instant.now(), issued.expiresAt());
        assertTrue(left.compareTo(Duration.ofMinutes(9)) > 0 && left.compareTo(Duration.ofMinutes(10)) <= 0, left.toString());

        link(me, issued.code()).then().statusCode(200).body("mcUuid", equalTo(mc.toString())).body("mcName", equalTo("Steve"));
        list(me).then().statusCode(200).body("size()", equalTo(1)).body("[0].mcUuid", equalTo(mc.toString())).body("[0].mcName", equalTo("Steve"));

        // a code works once, for anybody
        link(me, issued.code()).then().statusCode(400).body("code", equalTo("INVALID_CODE"));
        link(TestAccounts.newUser(), issued.code()).then().statusCode(400).body("code", equalTo("INVALID_CODE"));
    }

    @Test
    void theCodeMayBeTypedInLowerCaseWithADashOrSpaces() {
        Login me = TestAccounts.newUser();
        String code = service.codeForMinecraft(UUID.randomUUID(), "Alex_1").code();
        link(me, " " + code.substring(0, 4).toLowerCase() + "-" + code.substring(4).toLowerCase() + " ").then().statusCode(200);
    }

    @Test
    void anExpiredCodeAWrongCodeAndAnEmptyBodyAreRefused() {
        String expiredCode = MinecraftLinkService.newCode();
        Login me = TestAccounts.newUser();
        UUID mc = UUID.randomUUID();
        links.saveCode(new LinkCode(Ids.newId(), expiredCode, MinecraftLinkRepository.FOR_MINECRAFT, null, mc, "Late", Instant.now().minus(Duration.ofSeconds(1)), null));
        link(me, expiredCode).then().statusCode(400).body("code", equalTo("CODE_EXPIRED"));
        link(me, "ZZZZZZZZ").then().statusCode(400).body("code", equalTo("INVALID_CODE"));
        link(me, "").then().statusCode(400).body("code", equalTo("INVALID_CODE"));
        given().header("Authorization", me.bearer()).contentType(ContentType.JSON).post("/api/v1/me/minecraft/link").then().statusCode(400)
                .body("code", equalTo("BAD_REQUEST"));
        list(me).then().body("size()", equalTo(0));
    }

    @Test
    void aNewCodeForTheSameMinecraftAccountReplacesTheOldOne() {
        Login me = TestAccounts.newUser();
        UUID mc = UUID.randomUUID();
        String first = service.codeForMinecraft(mc, "Steve").code();
        String second = service.codeForMinecraft(mc, "Steve").code();
        link(me, first).then().statusCode(400).body("code", equalTo("INVALID_CODE"));
        link(me, second).then().statusCode(200);
    }

    @Test
    void aMinecraftAccountThatBelongsToSomeoneElseCannotBeLinked() {
        String takeover = MinecraftLinkService.newCode();
        String again = MinecraftLinkService.newCode();
        UUID mc = UUID.randomUUID();
        Login owner = TestAccounts.newUser();
        link(owner, service.codeForMinecraft(mc, "Steve").code()).then().statusCode(200);

        // the mod refuses to make a code for an account that is taken
        ApiException e = org.junit.jupiter.api.Assertions.assertThrows(ApiException.class, () -> service.codeForMinecraft(mc, "Steve"));
        assertEquals("MC_ALREADY_LINKED", e.code());
        assertEquals(409, e.status());

        // and even a code made some other way cannot take it over
        links.saveCode(new LinkCode(Ids.newId(), takeover, MinecraftLinkRepository.FOR_MINECRAFT, null, mc, "Steve", Instant.now().plus(Duration.ofMinutes(5)), null));
        Login thief = TestAccounts.newUser();
        link(thief, takeover).then().statusCode(409).body("code", equalTo("MC_ALREADY_LINKED"));
        list(thief).then().body("size()", equalTo(0));
        list(owner).then().body("size()", equalTo(1));

        // the owner using such a code again is harmless
        links.saveCode(new LinkCode(Ids.newId(), again, MinecraftLinkRepository.FOR_MINECRAFT, null, mc, "Steve", Instant.now().plus(Duration.ofMinutes(5)), null));
        link(owner, again).then().statusCode(200);
        list(owner).then().body("size()", equalTo(1));
    }

    @Test
    void oneUserMayLinkSeveralMinecraftAccounts() {
        Login me = TestAccounts.newUser();
        link(me, service.codeForMinecraft(UUID.randomUUID(), "Main_Account").code()).then().statusCode(200);
        link(me, service.codeForMinecraft(UUID.randomUUID(), "Alt_Account").code()).then().statusCode(200);
        list(me).then().body("size()", equalTo(2)).body("mcName", equalTo(List.of("Main_Account", "Alt_Account")));
    }

    @Test
    void aCodeGivenToTwoPeopleAtOnceLinksForOnlyOne() throws Exception {
        String code = service.codeForMinecraft(UUID.randomUUID(), "Racer").code();
        Login a = TestAccounts.newUser();
        Login b = TestAccounts.newUser();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> ta = () -> link(a, code).statusCode();
            Callable<Integer> tb = () -> link(b, code).statusCode();
            Future<Integer> fa = pool.submit(ta);
            Future<Integer> fb = pool.submit(tb);
            List<Integer> statuses = List.of(fa.get(), fb.get()).stream().sorted().toList();
            assertEquals(List.of(200, 400), statuses);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------- code made by the app, typed in Minecraft

    @Test
    void anAppCodeTypedInMinecraftLinksTheAccountOfWhoeverMadeIt() {
        Login me = TestAccounts.newUser();
        Response made = given().header("Authorization", me.bearer()).post("/api/v1/me/minecraft/link-code");
        made.then().statusCode(200);
        String code = made.path("code");
        assertCodeShape(code);

        // it is no good typed into the app: it is for Minecraft
        link(me, code).then().statusCode(400).body("code", equalTo("INVALID_CODE"));

        UUID mc = UUID.randomUUID();
        MinecraftLinkService.Completed done = service.completeInMinecraft(code.substring(0, 4).toLowerCase() + "-" + code.substring(4), mc, "Alex");
        assertEquals(me.id(), done.userId().toString());
        list(me).then().body("size()", equalTo(1)).body("[0].mcUuid", equalTo(mc.toString())).body("[0].mcName", equalTo("Alex"));

        // single use
        ApiException again = org.junit.jupiter.api.Assertions.assertThrows(ApiException.class, () -> service.completeInMinecraft(code, UUID.randomUUID(), "Other"));
        assertEquals("INVALID_CODE", again.code());
    }

    @Test
    void theMinecraftSideRefusesExpiredWrongAndTakenAccounts() {
        String oldApp = MinecraftLinkService.newCode();
        Login me = TestAccounts.newUser();
        UUID user = UUID.fromString(me.id());
        links.saveCode(new LinkCode(Ids.newId(), oldApp, MinecraftLinkRepository.FOR_APP_USER, user, null, null, Instant.now().minus(Duration.ofSeconds(1)), null));
        assertEquals("CODE_EXPIRED", org.junit.jupiter.api.Assertions.assertThrows(ApiException.class, () -> service.completeInMinecraft(oldApp, UUID.randomUUID(), "Late")).code());
        assertEquals("INVALID_CODE", org.junit.jupiter.api.Assertions.assertThrows(ApiException.class, () -> service.completeInMinecraft("NOSUCH77", UUID.randomUUID(), "Late")).code());
        // a code made for a Minecraft account cannot be typed in Minecraft
        String mcCode = service.codeForMinecraft(UUID.randomUUID(), "Other").code();
        assertEquals("INVALID_CODE", org.junit.jupiter.api.Assertions.assertThrows(ApiException.class, () -> service.completeInMinecraft(mcCode, UUID.randomUUID(), "Other")).code());

        // an account that belongs to somebody else
        UUID taken = UUID.randomUUID();
        link(TestAccounts.newUser(), service.codeForMinecraft(taken, "Taken").code()).then().statusCode(200);
        String appCode = given().header("Authorization", me.bearer()).post("/api/v1/me/minecraft/link-code").path("code");
        assertEquals("MC_ALREADY_LINKED", org.junit.jupiter.api.Assertions.assertThrows(ApiException.class, () -> service.completeInMinecraft(appCode, taken, "Taken")).code());
        list(me).then().body("size()", equalTo(0));
    }

    @Test
    void badMinecraftNamesAreRefused() {
        assertEquals("INVALID_MINECRAFT_ACCOUNT", org.junit.jupiter.api.Assertions.assertThrows(ApiException.class, () -> service.codeForMinecraft(UUID.randomUUID(), "")).code());
        assertEquals("INVALID_MINECRAFT_ACCOUNT", org.junit.jupiter.api.Assertions.assertThrows(ApiException.class, () -> service.codeForMinecraft(UUID.randomUUID(), "x".repeat(17))).code());
        assertEquals("INVALID_MINECRAFT_ACCOUNT", org.junit.jupiter.api.Assertions.assertThrows(ApiException.class, () -> service.codeForMinecraft(UUID.randomUUID(), "bad name!")).code());
        assertEquals("INVALID_MINECRAFT_ACCOUNT", org.junit.jupiter.api.Assertions.assertThrows(ApiException.class, () -> service.codeForMinecraft(null, "Steve")).code());
    }

    // ---------------------------------------------------------------- unlinking

    @Test
    void unlinkingFreesTheMinecraftAccountForAnotherPerson() {
        UUID mc = UUID.randomUUID();
        Login first = TestAccounts.newUser();
        Login second = TestAccounts.newUser();
        link(first, service.codeForMinecraft(mc, "Steve").code()).then().statusCode(200);

        // somebody else cannot unlink it
        given().header("Authorization", second.bearer()).delete("/api/v1/me/minecraft/" + mc).then().statusCode(404);
        list(first).then().body("size()", equalTo(1));

        given().header("Authorization", first.bearer()).delete("/api/v1/me/minecraft/" + mc).then().statusCode(204);
        list(first).then().body("size()", equalTo(0));
        given().header("Authorization", first.bearer()).delete("/api/v1/me/minecraft/" + mc).then().statusCode(404);
        given().header("Authorization", first.bearer()).delete("/api/v1/me/minecraft/not-a-uuid").then().statusCode(404);

        link(second, service.codeForMinecraft(mc, "Steve").code()).then().statusCode(200);
        list(second).then().body("[0].mcUuid", equalTo(mc.toString()));
    }

    // ---------------------------------------------------------------- limits and login

    @Test
    void guessingCodesIsRateLimited() {
        Login me = TestAccounts.newUser();
        for (int i = 0; i < 10; i++) {
            link(me, "WRONG" + (100 + i)).then().statusCode(400);
        }
        link(me, "WRONG999").then().statusCode(429).body("code", equalTo("RATE_LIMITED"));
        // even the right code is refused while the limit holds
        link(me, service.codeForMinecraft(UUID.randomUUID(), "Late").code()).then().statusCode(429);
    }

    @Test
    void makingCodesIsRateLimited() {
        Login me = TestAccounts.newUser();
        for (int i = 0; i < 5; i++) {
            given().header("Authorization", me.bearer()).post("/api/v1/me/minecraft/link-code").then().statusCode(200);
        }
        given().header("Authorization", me.bearer()).post("/api/v1/me/minecraft/link-code").then().statusCode(429).body("code", equalTo("RATE_LIMITED"));

        UUID mc = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            service.codeForMinecraft(mc, "Spammer");
        }
        assertEquals(429, org.junit.jupiter.api.Assertions.assertThrows(ApiException.class, () -> service.codeForMinecraft(mc, "Spammer")).status());
    }

    @Test
    void everythingNeedsALogin() {
        given().get("/api/v1/me/minecraft").then().statusCode(401);
        given().contentType(ContentType.JSON).body(Map.of("code", "ABCDEFGH")).post("/api/v1/me/minecraft/link").then().statusCode(401);
        given().post("/api/v1/me/minecraft/link-code").then().statusCode(401);
        given().delete("/api/v1/me/minecraft/" + UUID.randomUUID()).then().statusCode(401);
    }
}
