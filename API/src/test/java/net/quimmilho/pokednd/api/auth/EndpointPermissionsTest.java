/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.quimmilho.pokednd.api.TestAccounts;
import net.quimmilho.pokednd.api.TestAccounts.Login;
import net.quimmilho.pokednd.api.reference.BundleLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Every kind of caller against every kind of endpoint that exists so far. */
@QuarkusTest
@Tag("database")
class EndpointPermissionsTest {

    @Inject
    RateLimiter limiter;

    @Inject
    BundleLoader loader;

    @BeforeEach
    void freshLimits() {
        limiter.reset();
    }

    /** Who calls: nobody, an ordinary account, or the site admin. */
    enum Caller { ANONYMOUS, USER, SITE_ADMIN }

    record Case(Caller caller, String method, String path, int expected) {
        @Override
        public String toString() {
            return caller + " " + method + " " + path + " -> " + expected;
        }
    }

    static Stream<Case> cases() {
        List<Case> all = new ArrayList<>();
        // endpoint, then what anonymous, an ordinary user and the site admin get
        add(all, "GET", "/api/core-version", 200, 200, 200);
        add(all, "GET", "/api/v1/me", 401, 200, 200);
        add(all, "POST", "/api/v1/admin/data/reimport", 401, 403, -200);
        add(all, "GET", "/api/v1/data/natures", -200, -200, -200);
        add(all, "POST", "/api/v1/auth/logout", 204, 204, 204);
        add(all, "POST", "/api/v1/auth/refresh", 401, 401, 401);
        add(all, "GET", "/api/v1/auth/oauth/providers", 200, 200, 200);
        add(all, "GET", "/api/v1/me/sessions", 401, 200, 200);
        add(all, "GET", "/api/v1/me/identities", 401, 200, 200);
        add(all, "GET", "/api/v1/me/minecraft", 401, 200, 200);
        add(all, "POST", "/api/v1/me/minecraft/link-code", 401, 200, 200);
        // servers (phase 31): the list and the details are for the site admin; any person may ask for the servers they administer
        add(all, "GET", "/api/v1/servers", 401, 403, 200);
        add(all, "GET", "/api/v1/servers/00000000-0000-0000-0000-000000000000", 401, 403, 404);
        add(all, "DELETE", "/api/v1/servers/00000000-0000-0000-0000-000000000000", 401, 403, 404);
        add(all, "POST", "/api/v1/servers/00000000-0000-0000-0000-000000000000/token/rotate", 401, 403, 404);
        add(all, "PUT", "/api/v1/servers/00000000-0000-0000-0000-000000000000/admins/00000000-0000-0000-0000-000000000001", 401, 403, 404);
        add(all, "GET", "/api/v1/servers/00000000-0000-0000-0000-000000000000/status", 401, 403, 404);
        add(all, "GET", "/api/v1/me/servers", 401, 200, 200);
        // (the mod's endpoints take a server token only; ModLinkEndpointsTest tries every kind of token on them)
        // games (phase 32): a person with no role in a game cannot even tell that it exists
        add(all, "GET", "/api/v1/games", 401, 200, 200);
        add(all, "GET", "/api/v1/games/00000000-0000-0000-0000-000000000000", 401, 404, 404);
        add(all, "POST", "/api/v1/games/00000000-0000-0000-0000-000000000000/start", 401, 404, 404);
        add(all, "GET", "/api/v1/games/00000000-0000-0000-0000-000000000000/requests", 401, 404, 404);
        add(all, "GET", "/api/v1/games/00000000-0000-0000-0000-000000000000/members", 401, 404, 404);
        add(all, "DELETE", "/api/v1/games/00000000-0000-0000-0000-000000000000", 401, 404, 404);
        // settings and rule sets (phase 33)
        add(all, "GET", "/api/v1/settings/registry", 401, 200, 200);
        add(all, "GET", "/api/v1/rulesets", 401, 200, 200);
        add(all, "GET", "/api/v1/rulesets/builtin:casual", 401, 200, 200);
        add(all, "GET", "/api/v1/rulesets/builtin:casual/export", 401, 200, 200);
        add(all, "GET", "/api/v1/rulesets/00000000-0000-0000-0000-000000000000", 401, 404, 404);
        add(all, "DELETE", "/api/v1/rulesets/00000000-0000-0000-0000-000000000000", 401, 404, 404);
        add(all, "GET", "/api/v1/games/00000000-0000-0000-0000-000000000000/settings", 401, 404, 404);
        add(all, "GET", "/api/v1/games/00000000-0000-0000-0000-000000000000/presets", 401, 404, 404);
        add(all, "POST", "/api/v1/games/00000000-0000-0000-0000-000000000000/settings/undo", 401, 404, 404);
        // trainers (phase 38)
        add(all, "GET", "/api/v1/games/00000000-0000-0000-0000-000000000000/classes", 401, 404, 404);
        add(all, "GET", "/api/v1/games/00000000-0000-0000-0000-000000000000/trainer-options", 401, 404, 404);
        add(all, "GET", "/api/v1/games/00000000-0000-0000-0000-000000000000/trainers", 401, 404, 404);
        add(all, "GET", "/api/v1/trainers/00000000-0000-0000-0000-000000000000", 401, 404, 404);
        add(all, "DELETE", "/api/v1/trainers/00000000-0000-0000-0000-000000000000", 401, 404, 404);
        add(all, "GET", "/api/v1/trainers/00000000-0000-0000-0000-000000000000/skin", 401, 404, 404);
        add(all, "DELETE", "/api/v1/trainers/00000000-0000-0000-0000-000000000000/skin", 401, 404, 404);
        return all.stream();
    }

    /** A negative number means "this status, or 503/404 when the data bundle is not built on this machine". */
    private static void add(List<Case> all, String method, String path, int anon, int user, int admin) {
        all.add(new Case(Caller.ANONYMOUS, method, path, anon));
        all.add(new Case(Caller.USER, method, path, user));
        all.add(new Case(Caller.SITE_ADMIN, method, path, admin));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void everyCallerGetsTheRightAnswer(Case c) {
        RequestSpecification req = given();
        switch (c.caller()) {
            case USER -> req = req.header("Authorization", TestAccounts.newUser().bearer());
            case SITE_ADMIN -> req = req.header("Authorization", TestAccounts.admin().bearer());
            default -> {
            }
        }
        int status = req.request(c.method(), c.path()).statusCode();
        if (c.expected() < 0) {
            int want = -c.expected();
            boolean bundleThere = loader.bundleExists();
            if (bundleThere) {
                assertEquals(want, status, c.toString());
            } else {
                assertTrue(status == 503 || status == 404, c + " but got " + status);
            }
        } else {
            assertEquals(c.expected(), status, c.toString());
        }
    }

    @Test
    void anAdminTokenIsNotNeededForOpenEndpointsAndAUserTokenDoesNotOpenAdminOnes() {
        Login user = TestAccounts.newUser();
        given().header("Authorization", user.bearer()).post("/api/v1/admin/data/reimport").then().statusCode(403);
        given().post("/api/v1/admin/data/reimport").then().statusCode(401);
    }

    /** No endpoint may be open by accident: every REST method of the API says what it needs. */
    @Test
    void everyRestMethodHasARequiresAnnotation() throws IOException {
        Path classes = Path.of("build/classes/java/main");
        assertTrue(Files.isDirectory(classes), "run the test from the API folder after compiling");
        List<String> missing = new ArrayList<>();
        int seen = 0;
        try (Stream<Path> walk = Files.walk(classes)) {
            for (Path p : (Iterable<Path>) walk.filter(f -> f.toString().endsWith(".class"))::iterator) {
                String name = classes.relativize(p).toString().replace('\\', '.').replace('/', '.');
                name = name.substring(0, name.length() - ".class".length());
                Class<?> type;
                try {
                    type = Class.forName(name, false, getClass().getClassLoader());
                } catch (ReflectiveOperationException | LinkageError e) {
                    continue;
                }
                if (!type.isAnnotationPresent(jakarta.ws.rs.Path.class)) {
                    continue;
                }
                for (Method m : type.getDeclaredMethods()) {
                    boolean rest = m.isAnnotationPresent(GET.class) || m.isAnnotationPresent(POST.class) || m.isAnnotationPresent(PUT.class)
                            || m.isAnnotationPresent(PATCH.class) || m.isAnnotationPresent(DELETE.class);
                    if (!rest) {
                        continue;
                    }
                    seen++;
                    if (AuthFilters.requirement(m) == null) {
                        missing.add(type.getSimpleName() + "." + m.getName());
                    }
                }
            }
        }
        assertTrue(seen > 10, "found only " + seen + " REST methods: is the scan looking in the right place?");
        assertTrue(missing.isEmpty(), "these endpoints have no @Requires: " + missing);
        assertFalse(missing.contains("x"));
    }
}
