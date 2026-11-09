/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.settings;

import static io.restassured.RestAssured.given;
import static net.quimmilho.pokednd.api.TestWorld.get;
import static net.quimmilho.pokednd.api.TestWorld.post;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.quimmilho.pokednd.api.TestAccounts;
import net.quimmilho.pokednd.api.TestAccounts.Login;
import net.quimmilho.pokednd.api.TestWorld;
import net.quimmilho.pokednd.api.TestWorld.Server;
import net.quimmilho.pokednd.api.auth.RateLimiter;
import net.quimmilho.pokednd.api.domain.audit.AuditRepository;
import net.quimmilho.pokednd.api.domain.games.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The settings of a game over HTTP: layers and provenance, changing and resetting, presets, undo and the audit trail (phase 33). */
@QuarkusTest
@Tag("database")
class GameSettingsTest {

    @Inject
    RateLimiter limiter;

    @Inject
    AuditRepository audit;

    @Inject
    GameRepository games;

    @Inject
    SettingsService settings;

    @BeforeEach
    void freshLimits() {
        limiter.reset();
    }

    /** A server, its admin and a draft game they own. */
    private record Table(Server server, Login owner, String game) {
    }

    private static Table table() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        return new Table(server, owner, TestWorld.newGame(owner, server));
    }

    private static Response patch(Login who, String game, Map<String, Object> changes) {
        return given().header("Authorization", who.bearer()).contentType(ContentType.JSON).body(Map.of("settings", changes))
                .patch("/api/v1/games/" + game + "/settings");
    }

    private static Response settingsOf(Login who, String game) {
        return get(who, "games/" + game + "/settings");
    }

    private static String sourceOf(Login who, String game, String key) {
        return settingsOf(who, game).path("settings.find { it.key == '" + key + "' }.source");
    }

    private static Object valueOf(Login who, String game, String key) {
        return settingsOf(who, game).path("settings.find { it.key == '" + key + "' }.value");
    }

    private static Response applyRuleSet(Login who, String game, String ruleSetId, boolean dryRun) {
        return given().header("Authorization", who.bearer()).contentType(ContentType.JSON).body(Map.of("ruleSetId", ruleSetId, "dryRun", dryRun))
                .post("/api/v1/games/" + game + "/rulesets/apply");
    }

    /** Someone who plays in the game: asks with the lobby code and is accepted. Leaves the game in the lobby. */
    private static Login player(Table t) {
        post(t.owner(), t.game(), "lobby/open").then().statusCode(200);
        String code = get(t.owner(), "games/" + t.game()).path("joinCode");
        Login p = TestAccounts.newUser();
        given().header("Authorization", p.bearer()).contentType(ContentType.JSON).body(Map.of("code", code)).post("/api/v1/games/join").then().statusCode(200);
        String rid = get(t.owner(), "games/" + t.game() + "/requests").path("[0].id");
        post(t.owner(), t.game(), "requests/" + rid + "/accept").then().statusCode(200);
        return p;
    }

    // ---------------------------------------------------------------- reading

    @Test
    void aNewGameShowsEverySettingAtItsDefault() {
        Table t = table();
        Response r = settingsOf(t.owner(), t.game());
        r.then().statusCode(200).body("gameId", equalTo(t.game())).body("ruleSet.id", equalTo("builtin:defaults")).body("ruleSet.name", equalTo("Defaults"))
                .body("settings", hasSize(234));
        List<Map<String, Object>> all = r.path("settings");
        assertTrue(all.stream().allMatch(s -> "default".equals(s.get("source")) && Boolean.FALSE.equals(s.get("overridden"))));
        assertEquals("normal", valueOf(t.owner(), t.game(), "combat.ai.level"));
        assertEquals(6, valueOf(t.owner(), t.game(), "game.lobby.codeLength"));
        r.then().body("settings.find { it.key == 'combat.ai.level' }.allowed", hasItem("hard"))
                .body("settings.find { it.key == 'combat.ai.level' }.defaultValue", equalTo("normal"))
                .body("settings.find { it.key == 'combat.ai.level' }.chapter", equalTo(7));
    }

    @Test
    void theRegistryEndpointListsAppendixA() {
        given().header("Authorization", TestAccounts.newUser().bearer()).get("/api/v1/settings/registry").then().statusCode(200).body("size()", equalTo(234))
                .body("find { it.key == 'dice.input.modes' }.type", equalTo("csv"));
        given().get("/api/v1/settings/registry").then().statusCode(401);
    }

    @Test
    void playersAndSpectatorsMayReadButOnlyAdminsChange() {
        Table t = table();
        Login p = player(t);
        settingsOf(p, t.game()).then().statusCode(200);
        patch(p, t.game(), Map.of("combat.ai.level", "hard")).then().statusCode(403);
        given().header("Authorization", p.bearer()).contentType(ContentType.JSON).body(Map.of("keys", List.of("combat.ai.level"))).post("/api/v1/games/" + t.game() + "/settings/reset")
                .then().statusCode(403);
        post(p, t.game(), "settings/undo").then().statusCode(403);
        applyRuleSet(p, t.game(), "builtin:casual", false).then().statusCode(403);
        given().header("Authorization", p.bearer()).get("/api/v1/games/" + t.game() + "/presets").then().statusCode(403);

        // somebody with no role in the game does not even learn that it exists; nobody at all must log in
        Login stranger = TestAccounts.newUser();
        settingsOf(stranger, t.game()).then().statusCode(404);
        patch(stranger, t.game(), Map.of("combat.ai.level", "hard")).then().statusCode(404);
        given().get("/api/v1/games/" + t.game() + "/settings").then().statusCode(401);
        patch(TestAccounts.admin(), t.game(), Map.of("combat.ai.level", "hard")).then().statusCode(200);
    }

    // ---------------------------------------------------------------- layers

    @Test
    void aGameValueBeatsARuleSetValueBeatsTheDefault() {
        Table t = table();
        assertEquals("default", sourceOf(t.owner(), t.game(), "combat.ai.level"));

        applyRuleSet(t.owner(), t.game(), "builtin:casual", false).then().statusCode(200);
        assertEquals("easy", valueOf(t.owner(), t.game(), "combat.ai.level"));
        assertEquals("ruleSet", sourceOf(t.owner(), t.game(), "combat.ai.level"));
        assertEquals("default", sourceOf(t.owner(), t.game(), "combat.crit.multiplier"));

        patch(t.owner(), t.game(), Map.of("combat.ai.level", "hard")).then().statusCode(200).body("changed", hasSize(1)).body("changed[0].value", equalTo("hard"))
                .body("changed[0].source", equalTo("game"));
        assertEquals("hard", valueOf(t.owner(), t.game(), "combat.ai.level"));
        assertEquals("game", sourceOf(t.owner(), t.game(), "combat.ai.level"));

        // resetting removes the game's own value: the rule set shows through again
        given().header("Authorization", t.owner().bearer()).contentType(ContentType.JSON).body(Map.of("keys", List.of("combat.ai.level")))
                .post("/api/v1/games/" + t.game() + "/settings/reset").then().statusCode(200).body("changed[0].value", equalTo("easy"))
                .body("changed[0].source", equalTo("ruleSet"));

        // and without a rule set value, the default
        patch(t.owner(), t.game(), Map.of("combat.crit.multiplier", 2)).then().statusCode(200);
        assertEquals("game", sourceOf(t.owner(), t.game(), "combat.crit.multiplier"));
    }

    @Test
    void aNullRemovesTheGamesOwnValue() {
        Table t = table();
        patch(t.owner(), t.game(), Map.of("combat.crit.multiplier", 2.5)).then().statusCode(200);
        assertEquals(2.5, ((Number) valueOf(t.owner(), t.game(), "combat.crit.multiplier")).doubleValue());
        Map<String, Object> remove = new HashMap<>();
        remove.put("combat.crit.multiplier", null);
        patch(t.owner(), t.game(), remove).then().statusCode(200).body("changed[0].source", equalTo("default"));
        assertEquals(1.5, ((Number) valueOf(t.owner(), t.game(), "combat.crit.multiplier")).doubleValue());
    }

    @Test
    void resetAllRemovesEveryOwnValue() {
        Table t = table();
        patch(t.owner(), t.game(), Map.of("combat.ai.level", "hard", "dice.style", "classic", "world.shiny.oneIn", 100)).then().statusCode(200).body("changed", hasSize(3));
        given().header("Authorization", t.owner().bearer()).contentType(ContentType.JSON).body(Map.of("all", true)).post("/api/v1/games/" + t.game() + "/settings/reset")
                .then().statusCode(200).body("changed", hasSize(3));
        List<Map<String, Object>> all = settingsOf(t.owner(), t.game()).path("settings");
        assertTrue(all.stream().allMatch(s -> "default".equals(s.get("source"))));
    }

    @Test
    void aSavedRuleSetLayersTheSameWay() {
        Table t = table();
        String rs = given().header("Authorization", t.owner().bearer()).contentType(ContentType.JSON)
                .body(Map.of("name", "Kanto Weekend", "settings", Map.of("combat.turnTimer.enabled", true, "dice.input.modes", "playerSubmit,adminEnter")))
                .post("/api/v1/rulesets").then().statusCode(201).extract().path("id");
        applyRuleSet(t.owner(), t.game(), rs, false).then().statusCode(200).body("ruleSet.id", equalTo(rs)).body("ruleSet.name", equalTo("Kanto Weekend"));
        assertEquals(true, valueOf(t.owner(), t.game(), "combat.turnTimer.enabled"));
        settingsOf(t.owner(), t.game()).then().body("ruleSet.id", equalTo(rs));
        get(t.owner(), "games/" + t.game()).then().body("ruleSet", equalTo(rs));
        patch(t.owner(), t.game(), Map.of("combat.turnTimer.enabled", false)).then().statusCode(200);
        assertEquals(false, valueOf(t.owner(), t.game(), "combat.turnTimer.enabled"));
        assertEquals("playerSubmit,adminEnter", valueOf(t.owner(), t.game(), "dice.input.modes"));
    }

    // ---------------------------------------------------------------- validation

    @Test
    void invalidValuesAreRefusedWithAClearMessageAndNothingChanges() {
        Table t = table();
        patch(t.owner(), t.game(), Map.of("combat.ai.level", "extreme")).then().statusCode(400).body("code", equalTo("INVALID_SETTING"))
                .body("message", containsString("combat.ai.level")).body("message", containsString("easy, normal, hard")).body("details.errors", hasSize(1))
                .body("details.errors[0].key", equalTo("combat.ai.level"));
        patch(t.owner(), t.game(), Map.of("combat.whiteout.moneyLossPct", 250)).then().statusCode(400).body("message", containsString("at most 100"));
        patch(t.owner(), t.game(), Map.of("game.admin.plays", "yes")).then().statusCode(400).body("message", containsString("true or false"));
        patch(t.owner(), t.game(), Map.of("not.a.setting", true)).then().statusCode(400).body("message", containsString("no such setting"));

        // one bad value refuses the whole change: the good one is not kept either
        Map<String, Object> mixed = new HashMap<>();
        mixed.put("dice.style", "classic");
        mixed.put("combat.ai.level", "extreme");
        patch(t.owner(), t.game(), mixed).then().statusCode(400).body("details.errors", hasSize(1));
        assertEquals("smart", valueOf(t.owner(), t.game(), "dice.style"));
        assertEquals("default", sourceOf(t.owner(), t.game(), "dice.style"));

        patch(t.owner(), t.game(), Map.of()).then().statusCode(400);
        given().header("Authorization", t.owner().bearer()).contentType(ContentType.JSON).body("{}").patch("/api/v1/games/" + t.game() + "/settings").then().statusCode(400);
        given().header("Authorization", t.owner().bearer()).contentType(ContentType.JSON).body(Map.of("keys", List.of("nope"))).post("/api/v1/games/" + t.game() + "/settings/reset")
                .then().statusCode(400).body("code", equalTo("INVALID_SETTING"));
    }

    @Test
    void settingsOfAnArchivedGameAreFrozen() {
        Table t = table();
        given().header("Authorization", t.owner().bearer()).delete("/api/v1/games/" + t.game()).then().statusCode(200);
        patch(t.owner(), t.game(), Map.of("combat.ai.level", "hard")).then().statusCode(409).body("code", equalTo("GAME_ARCHIVED"));
    }

    // ---------------------------------------------------------------- presets and undo

    @Test
    void presetsAreListedForTheGameAdmin() {
        Table t = table();
        given().header("Authorization", t.owner().bearer()).get("/api/v1/games/" + t.game() + "/presets").then().statusCode(200).body("enabled", equalTo(true))
                .body("presets.id", equalTo(List.of("builtin:defaults", "builtin:casual", "builtin:hardcore", "builtin:atTheTable", "builtin:remote")))
                .body("presets.find { it.id == 'builtin:hardcore' }.settings.'combat.ai.level'", equalTo("hard"));
        patch(t.owner(), t.game(), Map.of("game.presets.enabled", false)).then().statusCode(200);
        given().header("Authorization", t.owner().bearer()).get("/api/v1/games/" + t.game() + "/presets").then().statusCode(200).body("enabled", equalTo(false));
    }

    @Test
    void aPresetShowsWhatWouldChangeBeforeItIsApplied() {
        Table t = table();
        applyRuleSet(t.owner(), t.game(), "builtin:hardcore", true).then().statusCode(200).body("applied", equalTo(false)).body("ruleSet.name", equalTo("Hardcore"))
                .body("changes.find { it.key == 'combat.ai.level' }.from", equalTo("normal")).body("changes.find { it.key == 'combat.ai.level' }.to", equalTo("hard"))
                .body("changes.find { it.key == 'combat.ai.level' }.fromSource", equalTo("default"))
                .body("changes.find { it.key == 'combat.ai.level' }.toSource", equalTo("ruleSet"))
                // a value that is already the same does not count as a change
                .body("changes.find { it.key == 'dice.approval.required' }", nullValue());
        assertEquals("normal", valueOf(t.owner(), t.game(), "combat.ai.level"));
        settingsOf(t.owner(), t.game()).then().body("ruleSet.id", equalTo("builtin:defaults"));
        assertTrue(audit.forGame(UUID.fromString(t.game()), "settings.").isEmpty(), "a preview is not a change");
    }

    @Test
    void aPresetAppliesAndCanBeUndone() {
        Table t = table();
        applyRuleSet(t.owner(), t.game(), "builtin:hardcore", false).then().statusCode(200).body("applied", equalTo(true)).body("changes.size()", equalTo(8));
        assertEquals("hard", valueOf(t.owner(), t.game(), "combat.ai.level"));
        assertEquals("deathSaves", valueOf(t.owner(), t.game(), "trainer.ko.mode"));
        assertEquals(60, valueOf(t.owner(), t.game(), "combat.turnTimer.seconds"));
        assertEquals("builtin:hardcore", get(t.owner(), "games/" + t.game()).path("ruleSet"));

        post(t.owner(), t.game(), "settings/undo").then().statusCode(200).body("changed.find { it.key == 'combat.ai.level' }.value", equalTo("normal"));
        assertEquals("normal", valueOf(t.owner(), t.game(), "combat.ai.level"));
        assertEquals("default", sourceOf(t.owner(), t.game(), "trainer.ko.mode"));
        settingsOf(t.owner(), t.game()).then().body("ruleSet.id", equalTo("builtin:defaults"));
        assertEquals("builtin:defaults", get(t.owner(), "games/" + t.game()).path("ruleSet"));
        post(t.owner(), t.game(), "settings/undo").then().statusCode(409).body("code", equalTo("NOTHING_TO_UNDO"));
    }

    @Test
    void undoGoesBackOneChangeAtATime() {
        Table t = table();
        patch(t.owner(), t.game(), Map.of("combat.ai.level", "easy")).then().statusCode(200);
        applyRuleSet(t.owner(), t.game(), "builtin:casual", false).then().statusCode(200);
        patch(t.owner(), t.game(), Map.of("combat.ai.level", "hard", "dice.style", "classic")).then().statusCode(200);
        given().header("Authorization", t.owner().bearer()).contentType(ContentType.JSON).body(Map.of("keys", List.of("dice.style"))).post("/api/v1/games/" + t.game() + "/settings/reset")
                .then().statusCode(200);
        assertEquals("smart", valueOf(t.owner(), t.game(), "dice.style"));

        post(t.owner(), t.game(), "settings/undo").then().statusCode(200); // takes the reset back
        assertEquals("classic", valueOf(t.owner(), t.game(), "dice.style"));
        post(t.owner(), t.game(), "settings/undo").then().statusCode(200); // takes the second patch back
        assertEquals("smart", valueOf(t.owner(), t.game(), "dice.style"));
        assertEquals("easy", valueOf(t.owner(), t.game(), "combat.ai.level"));
        assertEquals("game", sourceOf(t.owner(), t.game(), "combat.ai.level"), "the game's own easy from the first patch is still there");
        settingsOf(t.owner(), t.game()).then().body("ruleSet.id", equalTo("builtin:casual"));
        post(t.owner(), t.game(), "settings/undo").then().statusCode(200); // takes the rule set back
        settingsOf(t.owner(), t.game()).then().body("ruleSet.id", equalTo("builtin:defaults"));
        assertEquals("game", sourceOf(t.owner(), t.game(), "combat.ai.level"));
        post(t.owner(), t.game(), "settings/undo").then().statusCode(200); // takes the first patch back
        assertEquals("default", sourceOf(t.owner(), t.game(), "combat.ai.level"));
        post(t.owner(), t.game(), "settings/undo").then().statusCode(409);
    }

    @Test
    void loadingARuleSetKeepsOrDropsTheGamesOwnValues() {
        Table t = table();
        patch(t.owner(), t.game(), Map.of("combat.ai.level", "hard")).then().statusCode(200);
        applyRuleSet(t.owner(), t.game(), "builtin:casual", false).then().statusCode(200).body("changes.find { it.key == 'combat.ai.level' }", nullValue());
        assertEquals("hard", valueOf(t.owner(), t.game(), "combat.ai.level"));
        given().header("Authorization", t.owner().bearer()).contentType(ContentType.JSON)
                .body(Map.of("ruleSetId", "builtin:casual", "resetOverrides", true)).post("/api/v1/games/" + t.game() + "/rulesets/apply").then().statusCode(200)
                .body("changes.find { it.key == 'combat.ai.level' }.to", equalTo("easy"));
        assertEquals("easy", valueOf(t.owner(), t.game(), "combat.ai.level"));
        assertEquals("ruleSet", sourceOf(t.owner(), t.game(), "combat.ai.level"));
    }

    @Test
    void anUnknownOrForeignRuleSetCannotBeLoaded() {
        Table t = table();
        applyRuleSet(t.owner(), t.game(), "builtin:nothing", false).then().statusCode(404);
        applyRuleSet(t.owner(), t.game(), "not-an-id", false).then().statusCode(404);
        applyRuleSet(t.owner(), t.game(), UUID.randomUUID().toString(), false).then().statusCode(404);
        // another person's private rule set looks like one that does not exist
        Login other = TestAccounts.newUser();
        String theirs = given().header("Authorization", other.bearer()).contentType(ContentType.JSON).body(Map.of("name", "Mine")).post("/api/v1/rulesets").then().statusCode(201)
                .extract().path("id");
        applyRuleSet(t.owner(), t.game(), theirs, false).then().statusCode(404);
        given().header("Authorization", t.owner().bearer()).contentType(ContentType.JSON).body("{}").post("/api/v1/games/" + t.game() + "/rulesets/apply").then().statusCode(400);
    }

    // ---------------------------------------------------------------- audit

    @Test
    void everyChangeGoesToTheAuditTrailWithBeforeAndAfter() {
        Table t = table();
        UUID game = UUID.fromString(t.game());
        patch(t.owner(), t.game(), Map.of("combat.ai.level", "hard")).then().statusCode(200);
        applyRuleSet(t.owner(), t.game(), "builtin:casual", false).then().statusCode(200);
        given().header("Authorization", t.owner().bearer()).contentType(ContentType.JSON).body(Map.of("all", true)).post("/api/v1/games/" + t.game() + "/settings/reset").then()
                .statusCode(200);
        post(t.owner(), t.game(), "settings/undo").then().statusCode(200);

        List<AuditRepository.AuditEntry> entries = audit.forGame(game, "settings.");
        assertEquals(List.of("settings.undo", "settings.reset", "settings.ruleset", "settings.update"), entries.stream().map(AuditRepository.AuditEntry::action).toList());
        AuditRepository.AuditEntry update = entries.get(3);
        assertEquals(UUID.fromString(t.owner().id()), update.actorUserId());
        assertEquals("hard", update.after().get("overrides").get("combat.ai.level").asText());
        assertTrue(update.before().get("overrides").isEmpty());
        assertEquals("builtin:casual", entries.get(2).target());
        assertEquals("builtin:casual", entries.get(2).after().get("ruleSet").asText());
        assertEquals(entries.get(1).id().toString(), entries.get(0).target(), "an undo names the entry it takes back");
    }

    @Test
    void eachChangeRaisesTheSettingsVersionByOneAndLeavesOneEntry() {
        Table t = table();
        UUID game = UUID.fromString(t.game());
        long before = games.find(game).orElseThrow().settingsVersion();
        patch(t.owner(), t.game(), Map.of("combat.ai.level", "hard")).then().statusCode(200);
        assertEquals(before + 1, games.find(game).orElseThrow().settingsVersion());
        assertEquals(1, audit.forGame(game, "settings.").size());
    }

    // ---------------------------------------------------------------- the settings the games module reads

    @Test
    void theJoinCodeLengthFollowsTheSetting() {
        Table t = table();
        patch(t.owner(), t.game(), Map.of("game.lobby.codeLength", 9)).then().statusCode(200);
        post(t.owner(), t.game(), "lobby/open").then().statusCode(200);
        String code = get(t.owner(), "games/" + t.game()).path("joinCode");
        assertEquals(9, code.length());
        patch(t.owner(), t.game(), Map.of("game.lobby.codeLength", 3)).then().statusCode(400).body("message", containsString("at least 4"));
    }

    @Test
    void theDefaultJoinCodeStillHasSixCharacters() {
        Table t = table();
        post(t.owner(), t.game(), "lobby/open").then().statusCode(200);
        String code = get(t.owner(), "games/" + t.game()).path("joinCode");
        assertEquals(6, code.length());
    }

    @Test
    void otherModulesReadSettingsThroughTheService() {
        Table t = table();
        GameRepository.Game g = games.find(UUID.fromString(t.game())).orElseThrow();
        assertEquals(false, settings.bool(g, "game.admin.plays"));
        patch(t.owner(), t.game(), Map.of("game.admin.plays", true)).then().statusCode(200);
        g = games.find(UUID.fromString(t.game())).orElseThrow();
        assertEquals(true, settings.bool(g, "game.admin.plays"));
        assertEquals(6, settings.integer(g, "game.lobby.codeLength"));
    }

    @Test
    void aGameCanStartWithAPreset() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        Response r = given().header("Authorization", owner.bearer()).contentType(ContentType.JSON)
                .body(Map.of("serverId", server.id(), "name", "Quick start", "ruleSetId", "builtin:casual")).post("/api/v1/games");
        r.then().statusCode(201).body("ruleSet", equalTo("builtin:casual"));
        String game = r.path("id");
        assertEquals("easy", valueOf(owner, game, "combat.ai.level"));
        given().header("Authorization", owner.bearer()).contentType(ContentType.JSON).body(Map.of("serverId", server.id(), "name", "Nope", "ruleSetId", "builtin:nothing"))
                .post("/api/v1/games").then().statusCode(404);
        // the plain defaults can be named too
        given().header("Authorization", owner.bearer()).contentType(ContentType.JSON).body(Map.of("serverId", server.id(), "name", "Plain", "ruleSetId", "builtin:defaults"))
                .post("/api/v1/games").then().statusCode(201).body("ruleSet", equalTo("builtin:defaults"));
        get(owner, "games").then().statusCode(200);
    }

    // ---------------------------------------------------------------- two admins at once

    @Test
    void twoChangesAtTheSameMomentNeverLoseEachOther() throws Exception {
        Table t = table();
        List<String> keys = List.of("world.shiny.oneIn", "world.boss.oneIn", "world.boss.levelBonus", "economy.tutor.price", "economy.healer.price", "trainer.start.money");
        ExecutorService pool = Executors.newFixedThreadPool(keys.size());
        try {
            List<Callable<Integer>> jobs = new ArrayList<>();
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                int value = 10 + i;
                jobs.add(() -> patch(t.owner(), t.game(), Map.of(key, value)).statusCode());
            }
            int kept = 0;
            for (Future<Integer> f : pool.invokeAll(jobs)) {
                int status = f.get();
                assertTrue(status == 200 || status == 409, "a change is kept or told to retry, never lost silently: " + status);
                if (status == 200) {
                    kept++;
                }
            }
            assertTrue(kept >= 1);
            List<Map<String, Object>> all = settingsOf(t.owner(), t.game()).path("settings");
            long overridden = all.stream().filter(s -> "game".equals(s.get("source"))).count();
            assertEquals(kept, overridden, "every change that was answered 200 is in the game, and nothing else");
        } finally {
            pool.shutdownNow();
        }
    }
}
