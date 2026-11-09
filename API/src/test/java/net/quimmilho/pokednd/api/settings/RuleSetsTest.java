/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.settings;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.quimmilho.pokednd.api.TestAccounts;
import net.quimmilho.pokednd.api.TestAccounts.Login;
import net.quimmilho.pokednd.api.TestWorld;
import net.quimmilho.pokednd.api.TestWorld.Server;
import net.quimmilho.pokednd.api.auth.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Rule sets over HTTP: saving, sharing as files, who sees which, and loading them into games (phase 33, chapter 12.3). */
@QuarkusTest
@Tag("database")
class RuleSetsTest {

    @Inject
    RateLimiter limiter;

    @BeforeEach
    void freshLimits() {
        limiter.reset();
    }

    private static Response create(Login who, Map<String, Object> body) {
        return given().header("Authorization", who.bearer()).contentType(ContentType.JSON).body(body).post("/api/v1/rulesets");
    }

    private static String made(Login who, String name, Map<String, Object> settings) {
        return create(who, Map.of("name", name, "settings", settings)).then().statusCode(201).extract().path("id");
    }

    private static Response get(Login who, String id) {
        return given().header("Authorization", who.bearer()).get("/api/v1/rulesets/" + id);
    }

    // ---------------------------------------------------------------- the built-in ones

    @Test
    void theBuiltInRuleSetsAreListedReadableAndExportableButNotChangeable() {
        Login me = TestAccounts.newUser();
        given().header("Authorization", me.bearer()).get("/api/v1/rulesets").then().statusCode(200)
                .body("id", hasItem("builtin:defaults")).body("id", hasItem("builtin:casual")).body("id", hasItem("builtin:hardcore"))
                .body("id", hasItem("builtin:atTheTable")).body("id", hasItem("builtin:remote"))
                .body("find { it.id == 'builtin:casual' }.scope", equalTo("builtin")).body("find { it.id == 'builtin:casual' }.editable", equalTo(false))
                .body("find { it.id == 'builtin:casual' }.settings.'combat.ai.level'", equalTo("easy"));
        get(me, "builtin:hardcore").then().statusCode(200).body("name", equalTo("Hardcore")).body("settings.'trainer.ko.mode'", equalTo("deathSaves"));
        get(me, "builtin:nothing").then().statusCode(404);

        given().header("Authorization", me.bearer()).get("/api/v1/rulesets/builtin:casual/export").then().statusCode(200)
                .header("Content-Disposition", containsString("Casual.json")).body("name", equalTo("Casual")).body("formatVersion", equalTo(1))
                .body("basedOn", equalTo("builtin:defaults")).body("settings.'combat.ai.level'", equalTo("easy"));

        given().header("Authorization", me.bearer()).contentType(ContentType.JSON).body(Map.of("name", "Mine")).put("/api/v1/rulesets/builtin:casual").then().statusCode(403)
                .body("code", equalTo("BUILTIN_READ_ONLY"));
        given().header("Authorization", me.bearer()).delete("/api/v1/rulesets/builtin:casual").then().statusCode(403).body("code", equalTo("BUILTIN_READ_ONLY"));
        // a built-in name cannot be used for a base that does not exist
        create(me, Map.of("name", "x", "basedOn", "builtin:nothing")).then().statusCode(400).body("code", equalTo("INVALID_BASED_ON"));
    }

    // ---------------------------------------------------------------- saved rule sets of a person

    @Test
    void aPersonSavesReadsChangesAndDeletesTheirOwnRuleSet() {
        Login me = TestAccounts.newUser();
        Response r = create(me, Map.of("name", "  Kanto Weekend  ", "settings", Map.of("combat.turnTimer.enabled", true, "dice.input.modes", " playerSubmit , adminEnter "),
                "data", Map.of("classes", List.of(), "powerTable", Map.of())));
        r.then().statusCode(201).body("name", equalTo("Kanto Weekend")).body("scope", equalTo("user")).body("ownerId", equalTo(me.id())).body("formatVersion", equalTo(1))
                .body("basedOn", equalTo("builtin:defaults")).body("settings.'combat.turnTimer.enabled'", equalTo(true))
                .body("settings.'dice.input.modes'", equalTo("playerSubmit,adminEnter")).body("editable", equalTo(true)).body("data", hasKey("classes"));
        String id = r.path("id");

        given().header("Authorization", me.bearer()).get("/api/v1/rulesets").then().body("id", hasItem(id));
        get(me, id).then().statusCode(200).body("name", equalTo("Kanto Weekend"));

        // a change replaces what it names and keeps the rest
        given().header("Authorization", me.bearer()).contentType(ContentType.JSON).body(Map.of("name", "Kanto Weekend 2", "settings", Map.of("combat.ai.level", "hard")))
                .put("/api/v1/rulesets/" + id).then().statusCode(200).body("name", equalTo("Kanto Weekend 2")).body("settings.'combat.ai.level'", equalTo("hard"))
                .body("settings", not(hasKey("combat.turnTimer.enabled"))).body("data", hasKey("classes"));
        given().header("Authorization", me.bearer()).contentType(ContentType.JSON).body(Map.of("name", "Only a name")).put("/api/v1/rulesets/" + id).then().statusCode(200)
                .body("settings.'combat.ai.level'", equalTo("hard"));

        given().header("Authorization", me.bearer()).delete("/api/v1/rulesets/" + id).then().statusCode(204);
        get(me, id).then().statusCode(404);
        given().header("Authorization", me.bearer()).delete("/api/v1/rulesets/" + id).then().statusCode(404);
    }

    @Test
    void aPrivateRuleSetIsInvisibleToEveryoneElse() {
        Login me = TestAccounts.newUser();
        Login other = TestAccounts.newUser();
        String id = made(me, "Secret", Map.of());
        get(other, id).then().statusCode(404);
        given().header("Authorization", other.bearer()).contentType(ContentType.JSON).body(Map.of("name", "Taken")).put("/api/v1/rulesets/" + id).then().statusCode(404);
        given().header("Authorization", other.bearer()).delete("/api/v1/rulesets/" + id).then().statusCode(404);
        given().header("Authorization", other.bearer()).get("/api/v1/rulesets/" + id + "/export").then().statusCode(404);
        given().header("Authorization", other.bearer()).get("/api/v1/rulesets").then().body("id", not(hasItem(id)));
        // the site admin can look after everything
        get(TestAccounts.admin(), id).then().statusCode(200);
        get(me, "nonsense").then().statusCode(404);
        given().get("/api/v1/rulesets").then().statusCode(401);
    }

    @Test
    void badNamesSettingsAndDataAreRefused() {
        Login me = TestAccounts.newUser();
        create(me, Map.of("name", "")).then().statusCode(400).body("code", equalTo("INVALID_NAME"));
        create(me, Map.of("name", "x".repeat(81))).then().statusCode(400).body("code", equalTo("INVALID_NAME"));
        create(me, Map.of("settings", Map.of())).then().statusCode(400).body("code", equalTo("INVALID_NAME"));
        create(me, Map.of("name", "Bad", "settings", Map.of("combat.ai.level", "extreme", "nothing.here", 1))).then().statusCode(400)
                .body("code", equalTo("INVALID_SETTING")).body("details.errors.size()", equalTo(2)).body("details.errors.message", hasItem(containsString("easy, normal, hard")));
        create(me, Map.of("name", "Bad", "settings", List.of("a"))).then().statusCode(400).body("code", equalTo("INVALID_SETTING"));
        create(me, Map.of("name", "Bad", "data", List.of(1))).then().statusCode(400).body("code", equalTo("INVALID_DATA"));
        create(me, Map.of("name", "Bad", "scope", "galaxy")).then().statusCode(400);
        given().header("Authorization", me.bearer()).contentType(ContentType.JSON).post("/api/v1/rulesets").then().statusCode(400);
        List<String> big = java.util.Collections.nCopies(30000, "0123456789abcdef");
        create(me, Map.of("name", "Big", "data", Map.of("loot", big))).then().statusCode(400).body("code", equalTo("INVALID_DATA"));
    }

    // ---------------------------------------------------------------- files

    @Test
    void aRuleSetExportsAsAFileAndImportsBackAsACopy() {
        Login me = TestAccounts.newUser();
        String id = made(me, "Kanto Weekend", Map.of("dice.input.modes", "playerSubmit,adminEnter", "combat.turnTimer.enabled", true));
        Map<String, Object> file = given().header("Authorization", me.bearer()).get("/api/v1/rulesets/" + id + "/export").then().statusCode(200)
                .header("Content-Disposition", containsString("Kanto_Weekend.json")).body("formatVersion", equalTo(1)).body("name", equalTo("Kanto Weekend"))
                .body("basedOn", equalTo("builtin:defaults")).body("settings.'combat.turnTimer.enabled'", equalTo(true)).body("data", equalTo(Map.of())).extract().path("$");

        // a friend imports the file
        Login friend = TestAccounts.newUser();
        Response imported = given().header("Authorization", friend.bearer()).contentType(ContentType.JSON).body(file).post("/api/v1/rulesets/import");
        imported.then().statusCode(201).body("name", equalTo("Kanto Weekend")).body("ownerId", equalTo(friend.id())).body("scope", equalTo("user"))
                .body("settings.'dice.input.modes'", equalTo("playerSubmit,adminEnter"));
        assertTrue(!id.equals(imported.path("id")), "an import makes a new rule set");
    }

    @Test
    void theExampleFileOfChapter12_3Imports() {
        Login me = TestAccounts.newUser();
        String example = """
                { "name": "Kanto Weekend", "formatVersion": 1, "basedOn": "builtin:defaults",
                  "settings": { "dice.input.modes": "playerSubmit,adminEnter", "combat.turnTimer.enabled": true },
                  "data": { "classes": [], "powerTable": null } }""";
        given().header("Authorization", me.bearer()).contentType(ContentType.JSON).body(example).post("/api/v1/rulesets/import").then().statusCode(201)
                .body("name", equalTo("Kanto Weekend")).body("data.classes", equalTo(List.of())).body("data.powerTable", nullValue());
    }

    @Test
    void badFilesAreRefusedWithAReason() {
        Login me = TestAccounts.newUser();
        java.util.function.Function<String, Response> imp = body -> given().header("Authorization", me.bearer()).contentType(ContentType.JSON).body(body)
                .post("/api/v1/rulesets/import");
        imp.apply("[1,2]").then().statusCode(400).body("code", equalTo("BAD_RULESET_FILE"));
        imp.apply("{\"name\":\"x\"}").then().statusCode(400).body("code", equalTo("BAD_RULESET_FILE")).body("message", containsString("formatVersion"));
        imp.apply("{\"name\":\"x\",\"formatVersion\":2}").then().statusCode(422).body("code", equalTo("UNSUPPORTED_FORMAT")).body("message", containsString("version 2"));
        imp.apply("{\"name\":\"x\",\"formatVersion\":1,\"settings\":{\"from.the.future\":true}}").then().statusCode(400).body("code", equalTo("INVALID_SETTING"))
                .body("message", containsString("from.the.future"));
        imp.apply("{\"formatVersion\":1}").then().statusCode(400).body("code", equalTo("INVALID_NAME"));
        // a note about a rule set that only exists on the other server is dropped, not an error
        imp.apply("{\"name\":\"x\",\"formatVersion\":1,\"basedOn\":\"0192f2a0-1c2d-7abc-8def-0123456789ab\"}").then().statusCode(201).body("basedOn", equalTo("builtin:defaults"));
    }

    // ---------------------------------------------------------------- scopes

    @Test
    void aSiteRuleSetIsForEverybodyButOnlyTheSiteAdminChangesIt() {
        Login admin = TestAccounts.admin();
        Login me = TestAccounts.newUser();
        create(me, Map.of("name", "For all", "scope", "site")).then().statusCode(403);
        String id = create(admin, Map.of("name", "For all " + UUID.randomUUID(), "scope", "site", "settings", Map.of("combat.ai.level", "easy"))).then().statusCode(201)
                .body("scope", equalTo("site")).extract().path("id");
        get(me, id).then().statusCode(200).body("editable", equalTo(false));
        given().header("Authorization", me.bearer()).get("/api/v1/rulesets").then().body("id", hasItem(id));
        given().header("Authorization", me.bearer()).contentType(ContentType.JSON).body(Map.of("name", "Mine now")).put("/api/v1/rulesets/" + id).then().statusCode(403);
        given().header("Authorization", me.bearer()).delete("/api/v1/rulesets/" + id).then().statusCode(403);
        given().header("Authorization", admin.bearer()).delete("/api/v1/rulesets/" + id).then().statusCode(204);
    }

    @Test
    void aServerRuleSetIsForTheAdminsOfThatServer() {
        Server server = TestWorld.newServer();
        Server other = TestWorld.newServer();
        Login admin = TestWorld.newServerAdmin(server);
        Login otherAdmin = TestWorld.newServerAdmin(other);
        Login player = TestAccounts.newUser();

        create(player, Map.of("name", "Nope", "scope", "server", "serverId", server.id())).then().statusCode(403);
        create(otherAdmin, Map.of("name", "Nope", "scope", "server", "serverId", server.id())).then().statusCode(403);
        create(admin, Map.of("name", "No server", "scope", "server")).then().statusCode(400);
        create(admin, Map.of("name", "Ghost server", "scope", "server", "serverId", UUID.randomUUID().toString())).then().statusCode(404);

        String id = create(admin, Map.of("name", "House rules", "scope", "server", "serverId", server.id(), "settings", Map.of("world.shiny.oneIn", 100))).then().statusCode(201)
                .body("serverId", equalTo(server.id())).extract().path("id");
        Login coAdmin = TestAccounts.newUser();
        TestWorld.makeServerAdmin(server, coAdmin);
        get(coAdmin, id).then().statusCode(200).body("editable", equalTo(true));
        given().header("Authorization", coAdmin.bearer()).get("/api/v1/rulesets").then().body("id", hasItem(id));
        get(player, id).then().statusCode(404);
        get(otherAdmin, id).then().statusCode(404);
        given().header("Authorization", otherAdmin.bearer()).get("/api/v1/rulesets").then().body("id", not(hasItem(id)));
        given().header("Authorization", coAdmin.bearer()).contentType(ContentType.JSON).body(Map.of("name", "Renamed")).put("/api/v1/rulesets/" + id).then().statusCode(200);
    }

    // ---------------------------------------------------------------- from a game

    @Test
    void aGamesRulesAreSavedAsOnlyWhatDiffersFromTheDefaults() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String game = TestWorld.newGame(owner, server);
        given().header("Authorization", owner.bearer()).contentType(ContentType.JSON).body(Map.of("ruleSetId", "builtin:casual")).post("/api/v1/games/" + game + "/rulesets/apply")
                .then().statusCode(200);
        given().header("Authorization", owner.bearer()).contentType(ContentType.JSON)
                .body(Map.of("settings", Map.of("combat.ai.level", "hard", "world.shiny.oneIn", 512, "combat.turnTimer.seconds", 45))).patch("/api/v1/games/" + game + "/settings")
                .then().statusCode(200);

        Response saved = create(owner, Map.of("name", "Saved from game", "fromGameId", game));
        saved.then().statusCode(201).body("basedOn", equalTo("builtin:defaults"))
                // the game's own value beats the preset
                .body("settings.'combat.ai.level'", equalTo("hard")).body("settings.'combat.turnTimer.seconds'", equalTo(45))
                // the preset's values are in
                .body("settings.'dice.input.modes'", equalTo("digital")).body("settings.'world.fogOfWar'", equalTo(false))
                // 512 is the default, so it is not stored
                .body("settings", not(hasKey("world.shiny.oneIn")));
        Map<String, Object> stored = saved.path("settings");
        for (Map.Entry<String, Object> e : stored.entrySet()) {
            assertFalse(e.getKey().isEmpty());
        }
        assertTrue(stored.size() < 15, "only the differences are saved, not all 234 settings: " + stored.size());

        // a new game started from it behaves like the old one
        String id = saved.path("id");
        Response second = given().header("Authorization", owner.bearer()).contentType(ContentType.JSON)
                .body(Map.of("serverId", server.id(), "name", "Second", "ruleSetId", id)).post("/api/v1/games");
        second.then().statusCode(201).body("ruleSet", equalTo(id));
        Object ai = given().header("Authorization", owner.bearer()).get("/api/v1/games/" + second.path("id") + "/settings").path("settings.find { it.key == 'combat.ai.level' }.value");
        assertEquals("hard", ai);
    }

    @Test
    void onlyTheAdminOfAGameSavesItsRulesAndNotTogetherWithSettings() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String game = TestWorld.newGame(owner, server);
        Login stranger = TestAccounts.newUser();
        create(stranger, Map.of("name", "Steal", "fromGameId", game)).then().statusCode(404);
        create(owner, Map.of("name", "Both", "fromGameId", game, "settings", Map.of("combat.ai.level", "hard"))).then().statusCode(400);
        create(owner, Map.of("name", "Ghost", "fromGameId", UUID.randomUUID().toString())).then().statusCode(404);
    }

    // ---------------------------------------------------------------- in use

    @Test
    void aRuleSetThatAGameUsesCannotBeDeletedUntilTheGameLetsGo() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String game = TestWorld.newGame(owner, server);
        String id = made(owner, "In use", Map.of("combat.ai.level", "easy"));
        given().header("Authorization", owner.bearer()).contentType(ContentType.JSON).body(Map.of("ruleSetId", id)).post("/api/v1/games/" + game + "/rulesets/apply").then()
                .statusCode(200);
        given().header("Authorization", owner.bearer()).delete("/api/v1/rulesets/" + id).then().statusCode(409).body("code", equalTo("RULESET_IN_USE"));
        given().header("Authorization", owner.bearer()).contentType(ContentType.JSON).body(Map.of("ruleSetId", "builtin:defaults")).post("/api/v1/games/" + game + "/rulesets/apply")
                .then().statusCode(200);
        given().header("Authorization", owner.bearer()).delete("/api/v1/rulesets/" + id).then().statusCode(204);
    }

    @Test
    void changingAUsedRuleSetChangesTheGame() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String game = TestWorld.newGame(owner, server);
        String id = made(owner, "Shared", Map.of("combat.ai.level", "easy"));
        given().header("Authorization", owner.bearer()).contentType(ContentType.JSON).body(Map.of("ruleSetId", id)).post("/api/v1/games/" + game + "/rulesets/apply").then()
                .statusCode(200);
        given().header("Authorization", owner.bearer()).contentType(ContentType.JSON).body(Map.of("settings", Map.of("combat.ai.level", "hard"))).put("/api/v1/rulesets/" + id)
                .then().statusCode(200);
        assertEquals("hard", given().header("Authorization", owner.bearer()).get("/api/v1/games/" + game + "/settings")
                .path("settings.find { it.key == 'combat.ai.level' }.value"));
    }
}
