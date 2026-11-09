/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.trainers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import net.quimmilho.pokednd.api.TestAccounts;
import net.quimmilho.pokednd.api.TestAccounts.Login;
import net.quimmilho.pokednd.api.TestWorld;
import net.quimmilho.pokednd.api.TestWorld.Server;
import net.quimmilho.pokednd.api.auth.RateLimiter;
import net.quimmilho.pokednd.api.reference.BundleLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Trainers and their skins (phase 38): the wizard's endpoints, the rules of Core behind them, and the skin checks. Runs against a real database. */
@QuarkusTest
@Tag("database")
class TrainersTest {

    @Inject
    RateLimiter limiter;

    @Inject
    BundleLoader loader;

    @BeforeEach
    void freshLimits() {
        limiter.reset();
    }

    /** A game with its owner, in the lobby, and the people who joined. */
    private record Table(Server server, Login owner, String game) {

        Login player() {
            Login p = TestAccounts.newUser();
            String code = TestWorld.get(owner, "games/" + game).path("joinCode");
            given().header("Authorization", p.bearer()).contentType(ContentType.JSON).body(Map.of("code", code)).post("/api/v1/games/join").then().statusCode(200);
            List<Map<String, Object>> requests = TestWorld.get(owner, "games/" + game + "/requests").jsonPath().getList("$");
            String rid = requests.stream().filter(r -> p.id().equals(r.get("userId"))).map(r -> (String) r.get("id")).findFirst().orElseThrow();
            given().header("Authorization", owner.bearer()).post("/api/v1/games/" + game + "/requests/" + rid + "/accept").then().statusCode(200);
            return p;
        }

        Login spectator() {
            Login p = TestAccounts.newUser();
            String code = TestWorld.get(owner, "games/" + game).path("joinCode");
            given().header("Authorization", p.bearer()).contentType(ContentType.JSON).body(Map.of("code", code, "role", "spectator")).post("/api/v1/games/join")
                    .then().statusCode(200);
            List<Map<String, Object>> requests = TestWorld.get(owner, "games/" + game + "/requests").jsonPath().getList("$");
            String rid = requests.stream().filter(r -> p.id().equals(r.get("userId"))).map(r -> (String) r.get("id")).findFirst().orElseThrow();
            given().header("Authorization", owner.bearer()).post("/api/v1/games/" + game + "/requests/" + rid + "/accept").then().statusCode(200);
            return p;
        }

        void setting(String key, Object value) {
            given().header("Authorization", owner.bearer()).contentType(ContentType.JSON).body(Map.of("settings", Map.of(key, value)))
                    .patch("/api/v1/games/" + game + "/settings").then().statusCode(200);
        }
    }

    /** A lobby whose starters are assigned by the admin, so that most tests need no Pokémon data. */
    private static Table table() {
        Server server = TestWorld.newServer();
        Login owner = TestWorld.newServerAdmin(server);
        String game = TestWorld.newGame(owner, server);
        TestWorld.post(owner, game, "lobby/open").then().statusCode(200);
        Table t = new Table(server, owner, game);
        t.setting("pokemon.starter.method", "assign");
        return t;
    }

    /** Point buy: 15 14 13 11 10 9 costs 27 points. */
    private static Map<String, Object> choices(String name, String classId) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("classId", classId);
        m.put("method", "pointBuy");
        m.put("scores", Map.of("str", 15, "dex", 14, "con", 13, "int", 11, "wis", 10, "cha", 9));
        m.put("skills", List.of("pokemon_handling", "insight"));
        return m;
    }

    private static Response post(Login who, String game, Map<String, Object> body) {
        return given().header("Authorization", who.bearer()).contentType(ContentType.JSON).body(body).post("/api/v1/games/" + game + "/trainers");
    }

    private static Response patch(Login who, String trainer, Map<String, Object> body) {
        return given().header("Authorization", who.bearer()).contentType(ContentType.JSON).body(body).patch("/api/v1/trainers/" + trainer);
    }

    private static String uniqueName() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    // ---------------------------------------------------------------- the rules of the game

    @Test
    void membersSeeTheClassesAndTheOptionsOthersDoNot() {
        Table t = table();
        Login player = t.player();
        given().header("Authorization", player.bearer()).get("/api/v1/games/" + t.game + "/classes").then().statusCode(200).body("$", hasSize(9))
                .body("[0].id", equalTo("ace_trainer")).body("[0].name", equalTo("Ace Trainer")).body("[0].hitDie", equalTo(8))
                .body("[0].features", hasSize(6)).body("[0].features[0].effects[0].key", equalTo("accuracy.bonusPp"))
                .body("[0].skillChoices.pick", equalTo(2)).body("[0].saves", contains("dex", "cha"));
        given().header("Authorization", player.bearer()).get("/api/v1/games/" + t.game + "/trainer-options").then().statusCode(200)
                .body("nameMode", equalTo("perGame")).body("nameMinLength", equalTo(3)).body("nameMaxLength", equalTo(16))
                .body("scoreMethods", contains("pointBuy")).body("pointBuyBudget", equalTo(27)).body("pointBuyCosts.'8'", equalTo(0))
                .body("pointBuyCosts.'15'", equalTo(9)).body("standardArray", contains(15, 14, 13, 12, 10, 8)).body("startLevel", equalTo(1))
                .body("improvementLevels", contains(4, 8, 12, 16, 19)).body("skills", hasSize(17)).body("starters.method", equalTo("assign"))
                .body("starters.level", equalTo(5)).body("skin.allowUpload", equalTo(true)).body("skin.allowCopyFromUsername", equalTo(true))
                .body("skin.size", equalTo(64)).body("hasTrainer", equalTo(false));
        given().header("Authorization", TestAccounts.newUser().bearer()).get("/api/v1/games/" + t.game + "/classes").then().statusCode(404);
        given().get("/api/v1/games/" + t.game + "/classes").then().statusCode(401);
        given().get("/api/v1/games/" + t.game + "/trainer-options").then().statusCode(401);
    }

    @Test
    void theOptionsFollowTheGamesSettings() {
        Table t = table();
        t.setting("trainer.abilityScores.method", "any");
        t.setting("trainer.abilityScores.pointBuyBudget", 30);
        t.setting("game.skins.allowUpload", false);
        Login player = t.player();
        given().header("Authorization", player.bearer()).get("/api/v1/games/" + t.game + "/trainer-options").then().statusCode(200)
                .body("scoreMethods", contains("pointBuy", "standardArray", "roll4d6DropLowest")).body("pointBuyBudget", equalTo(30))
                .body("skin.allowUpload", equalTo(false));
    }

    // ---------------------------------------------------------------- making a trainer

    @Test
    void aPlayerMakesATrainerAndEveryoneInTheGameCanReadIt() {
        Table t = table();
        Login ana = t.player();
        Login bob = t.player();
        String name = uniqueName();
        Response created = post(ana, t.game, choices(name, "ace_trainer"));
        created.then().statusCode(201).body("name", equalTo(name)).body("classId", equalTo("ace_trainer")).body("className", equalTo("Ace Trainer"))
                .body("level", equalTo(1)).body("xp", equalTo(0)).body("xpForNextLevel", equalTo(50)).body("hp", equalTo(9)).body("hpMax", equalTo(9))
                .body("money", equalTo(3000)).body("items.poke_ball", equalTo(5)).body("items.potion", equalTo(3)).body("scoreMethod", equalTo("pointBuy"))
                .body("abilityScores.str", equalTo(15)).body("skills", contains("pokemon_handling", "insight")).body("ownerName", equalTo("Test person"))
                .body("userId", equalTo(ana.id())).body("starter", nullValue()).body("skin", nullValue())
                .body("sheet.proficiencyBonus", equalTo(2)).body("sheet.armorClass", equalTo(12)).body("sheet.modifiers.dex", equalTo(2))
                .body("sheet.savingThrows.dex", equalTo(4)).body("sheet.skillBonuses.pokemon_handling", equalTo(2)).body("sheet.handlingModifier", equalTo(2))
                .body("sheet.features", hasSize(1)).body("sheet.features[0].id", equalTo("commanding_presence"))
                .body("sheet.effects.'accuracy.bonusPp'", equalTo(2.0f));
        String id = created.path("id");

        // another player and the admin read it; a stranger does not even know the game
        given().header("Authorization", bob.bearer()).get("/api/v1/trainers/" + id).then().statusCode(200).body("name", equalTo(name));
        given().header("Authorization", t.owner.bearer()).get("/api/v1/trainers/" + id).then().statusCode(200);
        given().header("Authorization", TestAccounts.newUser().bearer()).get("/api/v1/trainers/" + id).then().statusCode(404);
        given().get("/api/v1/trainers/" + id).then().statusCode(401);
        given().header("Authorization", bob.bearer()).get("/api/v1/games/" + t.game + "/trainers").then().statusCode(200).body("$", hasSize(1))
                .body("[0].id", equalTo(id));
        given().header("Authorization", ana.bearer()).get("/api/v1/games/" + t.game + "/trainer-options").then().body("hasTrainer", equalTo(true));
        given().header("Authorization", bob.bearer()).get("/api/v1/games/" + t.game + "/trainer-options").then().body("hasTrainer", equalTo(false));
    }

    @Test
    void everyClassCanBeCreated() {
        Table t = table();
        for (String id : List.of("ace_trainer", "breeder", "researcher", "ranger", "hiker", "coordinator", "psychic", "collector", "medic")) {
            Login p = t.player();
            List<String> skills = given().header("Authorization", p.bearer()).get("/api/v1/games/" + t.game + "/classes").path("find { it.id == '" + id + "' }.skillChoices.from");
            Map<String, Object> body = choices(uniqueName(), id);
            body.put("skills", skills.subList(0, 2));
            post(p, t.game, body).then().statusCode(201).body("classId", equalTo(id));
        }
    }

    @Test
    void onlyOneTrainerPerPersonPerGame() {
        Table t = table();
        Login ana = t.player();
        post(ana, t.game, choices(uniqueName(), "breeder")).then().statusCode(201);
        post(ana, t.game, choices(uniqueName(), "hiker")).then().statusCode(409).body("code", equalTo("TRAINER_EXISTS"));
    }

    @Test
    void rulesBreakersAreRefusedWithReadableProblems() {
        Table t = table();
        Login ana = t.player();

        Map<String, Object> tooMany = choices(uniqueName(), "ace_trainer");
        tooMany.put("scores", Map.of("str", 15, "dex", 15, "con", 15, "int", 15, "wis", 15, "cha", 15));
        post(ana, t.game, tooMany).then().statusCode(422).body("code", equalTo("INVALID_TRAINER")).body("details.problems.code", contains("too_many_points"))
                .body("message", equalTo("These scores cost 54 points, but you only have 27."));

        post(ana, t.game, choices(uniqueName(), "wizard")).then().statusCode(422).body("details.problems.code", contains("unknown_class"))
                .body("details.problems[0].field", equalTo("class"));

        Map<String, Object> badSkills = choices(uniqueName(), "hiker");
        badSkills.put("skills", List.of("persuasion"));
        post(ana, t.game, badSkills).then().statusCode(422).body("details.problems.code", containsInAnyOrder("skill_count", "skill_not_allowed"));

        post(ana, t.game, choices("ab", "ace_trainer")).then().statusCode(422).body("details.problems.code", contains("name_length"));
        post(ana, t.game, choices("bad name!", "ace_trainer")).then().statusCode(422).body("details.problems.code", contains("name_characters"));

        Map<String, Object> notAllowed = choices(uniqueName(), "ace_trainer");
        notAllowed.put("method", "standardArray");
        notAllowed.put("scores", Map.of("str", 15, "dex", 14, "con", 13, "int", 12, "wis", 10, "cha", 8));
        post(ana, t.game, notAllowed).then().statusCode(422).body("details.problems.code", contains("method_not_allowed"));

        Map<String, Object> missing = choices(uniqueName(), "ace_trainer");
        missing.put("scores", Map.of("str", 15, "dex", 14));
        post(ana, t.game, missing).then().statusCode(422).body("details.problems.code", hasItem("scores_missing"));

        Map<String, Object> unknownMethod = choices(uniqueName(), "ace_trainer");
        unknownMethod.put("method", "dice");
        post(ana, t.game, unknownMethod).then().statusCode(422).body("details.problems.code", hasItem("method_unknown"));

        post(ana, t.game, new HashMap<>()).then().statusCode(422);
        given().header("Authorization", ana.bearer()).contentType(ContentType.JSON).post("/api/v1/games/" + t.game + "/trainers").then().statusCode(400);
        // none of these left a trainer behind
        given().header("Authorization", ana.bearer()).get("/api/v1/games/" + t.game + "/trainers").then().body("$", hasSize(0));
    }

    @Test
    void standardArrayAndRolledScoresWorkWhenTheGameAllowsThem() {
        Table t = table();
        t.setting("trainer.abilityScores.method", "any");
        Login ana = t.player();
        Map<String, Object> array = choices(uniqueName(), "ace_trainer");
        array.put("method", "standardArray");
        array.put("scores", Map.of("str", 8, "dex", 15, "con", 14, "int", 10, "wis", 13, "cha", 12));
        post(ana, t.game, array).then().statusCode(201).body("scoreMethod", equalTo("standardArray")).body("hpMax", equalTo(10));

        Login bob = t.player();
        Map<String, Object> rolled = choices(uniqueName(), "ace_trainer");
        rolled.put("method", "roll4d6DropLowest");
        rolled.put("scores", Map.of("str", 18, "dex", 3, "con", 12, "int", 14, "wis", 9, "cha", 16));
        post(bob, t.game, rolled).then().statusCode(201).body("scoreMethod", equalTo("roll4d6DropLowest")).body("abilityScores.str", equalTo(18));

        Login cy = t.player();
        rolled.put("scores", Map.of("str", 19, "dex", 3, "con", 12, "int", 14, "wis", 9, "cha", 16));
        rolled.put("name", uniqueName());
        post(cy, t.game, rolled).then().statusCode(422).body("details.problems.code", contains("roll_out_of_range"));
    }

    @Test
    void namesAreUniqueInTheGameWhateverTheCase() {
        Table t = table();
        String name = uniqueName();
        post(t.player(), t.game, choices(name, "ace_trainer")).then().statusCode(201);
        post(t.player(), t.game, choices(name.toLowerCase(), "breeder")).then().statusCode(422).body("details.problems.code", contains("name_taken"));
        post(t.player(), t.game, choices(name.toUpperCase(), "breeder")).then().statusCode(422).body("details.problems.code", contains("name_taken"));
        // another game has no say
        Table other = table();
        post(other.player(), other.game, choices(name, "ace_trainer")).then().statusCode(201);
    }

    @Test
    void twoPlayersChoosingTheSameNameAtTheSameMomentCannotBothWin() throws Exception {
        Table t = table();
        List<Login> players = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            players.add(t.player());
        }
        String name = uniqueName();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (Login p : players) {
                Callable<Integer> call = () -> post(p, t.game, choices(name, "ace_trainer")).statusCode();
                results.add(pool.submit(call));
            }
            int created = 0;
            for (Future<Integer> f : results) {
                int status = f.get();
                assertTrue(status == 201 || status == 422, "status " + status);
                if (status == 201) {
                    created++;
                }
            }
            assertEquals(1, created, "exactly one trainer got the name");
        } finally {
            pool.shutdownNow();
        }
        given().header("Authorization", t.owner.bearer()).get("/api/v1/games/" + t.game + "/trainers").then().body("$", hasSize(1));
    }

    @Test
    void theTrainerNameCanBeTheMinecraftNameWhenTheGameSaysSo() {
        Table t = table();
        t.setting("game.trainerNames.mode", "accountName");
        // no Minecraft account is linked, so there is no name to use
        post(t.player(), t.game, choices("Ignored", "ace_trainer")).then().statusCode(409).body("code", equalTo("NO_MINECRAFT_ACCOUNT"));
    }

    @Test
    void onlyPlayersMakeTrainers() {
        Table t = table();
        // a spectator, a stranger and the admin (who does not play by default)
        post(t.spectator(), t.game, choices(uniqueName(), "ace_trainer")).then().statusCode(403);
        post(TestAccounts.newUser(), t.game, choices(uniqueName(), "ace_trainer")).then().statusCode(404);
        post(t.owner, t.game, choices(uniqueName(), "ace_trainer")).then().statusCode(403);
        given().contentType(ContentType.JSON).body(choices(uniqueName(), "ace_trainer")).post("/api/v1/games/" + t.game + "/trainers").then().statusCode(401);
        // with game.admin.plays the admin may
        t.setting("game.admin.plays", true);
        post(t.owner, t.game, choices(uniqueName(), "ace_trainer")).then().statusCode(201);
    }

    @Test
    void noTrainersInAnEndedGame() {
        Table t = table();
        Login ana = t.player();
        TestWorld.post(t.owner, t.game, "start").then().statusCode(200);
        TestWorld.post(t.owner, t.game, "end").then().statusCode(200);
        post(ana, t.game, choices(uniqueName(), "ace_trainer")).then().statusCode(409).body("code", equalTo("INVALID_STATE"));
    }

    @Test
    void aPlayerCanJoinLateAndMakeATrainerInARunningGame() {
        Table t = table();
        TestWorld.post(t.owner, t.game, "start").then().statusCode(200);
        TestWorld.post(t.owner, t.game, "lobby/open").then().statusCode(200);
        post(t.player(), t.game, choices(uniqueName(), "ace_trainer")).then().statusCode(201);
    }

    // ---------------------------------------------------------------- starters

    @Test
    void aStarterIsPickedFromTheListWhenThatIsTheMethod() {
        assumeTrue(loader.bundleExists(), "the data bundle is not built, so there are no Pokémon to pick");
        Table t = table();
        t.setting("pokemon.starter.method", "pick");
        Login ana = t.player();
        given().header("Authorization", ana.bearer()).get("/api/v1/games/" + t.game + "/trainer-options").then().body("starters.species", hasSize(27))
                .body("starters.species.id", hasItem("bulbasaur")).body("starters.species[0].name", equalTo("Bulbasaur"));

        post(ana, t.game, choices(uniqueName(), "ace_trainer")).then().statusCode(422).body("details.problems.code", contains("starter_missing"));
        Map<String, Object> notAStarter = choices(uniqueName(), "ace_trainer");
        notAStarter.put("starter", "mewtwo");
        post(ana, t.game, notAStarter).then().statusCode(422).body("details.problems.code", contains("starter_not_allowed"));
        Map<String, Object> ok = choices(uniqueName(), "ace_trainer");
        ok.put("starter", "charmander");
        post(ana, t.game, ok).then().statusCode(201).body("starter.species", equalTo("charmander")).body("starter.name", equalTo("Charmander"))
                .body("starter.level", equalTo(5));
    }

    @Test
    void aRandomStarterComesFromTheListAndStaysTheSameWhenTheTrainerIsRemade() {
        assumeTrue(loader.bundleExists(), "the data bundle is not built, so there are no Pokémon to draw");
        Table t = table();
        t.setting("pokemon.starter.method", "random");
        t.setting("pokemon.starter.level", 7);
        Login ana = t.player();
        Response r = post(ana, t.game, choices(uniqueName(), "ace_trainer"));
        r.then().statusCode(201).body("starter.level", equalTo(7));
        String species = r.path("starter.species");
        List<String> allowed = given().header("Authorization", ana.bearer()).get("/api/v1/games/" + t.game + "/trainer-options").path("starters.species.id");
        assertTrue(allowed.contains(species), species);
        patch(ana, r.path("id"), choices(uniqueName(), "breeder")).then().statusCode(200).body("starter.species", equalTo(species));
    }

    @Test
    void anAssignedStarterIsLeftEmptyWhateverThePlayerSends() {
        Table t = table();
        Map<String, Object> body = choices(uniqueName(), "ace_trainer");
        body.put("starter", "bulbasaur");
        post(t.player(), t.game, body).then().statusCode(201).body("starter", nullValue());
    }

    // ---------------------------------------------------------------- changing and removing

    @Test
    void aPlayerRemakesTheirTrainerBeforeTheGameStarts() {
        Table t = table();
        Login ana = t.player();
        String first = uniqueName();
        String id = post(ana, t.game, choices(first, "ace_trainer")).path("id");
        String second = uniqueName();
        Map<String, Object> changed = choices(second, "hiker");
        changed.put("skills", List.of("athletics", "survival"));
        patch(ana, id, changed).then().statusCode(200).body("id", equalTo(id)).body("name", equalTo(second)).body("classId", equalTo("hiker"))
                .body("hpMax", equalTo(12)).body("skills", contains("athletics", "survival"));
        // the same name again is fine (it is the trainer's own), another player's is not
        patch(ana, id, changed).then().statusCode(200);
        String bobsName = uniqueName();
        post(t.player(), t.game, choices(bobsName, "breeder")).then().statusCode(201);
        Map<String, Object> clash = choices(bobsName.toLowerCase(), "hiker");
        clash.put("skills", List.of("athletics", "survival"));
        patch(ana, id, clash).then().statusCode(422).body("details.problems.code", contains("name_taken"));
        // a bad change leaves the trainer as it was
        get(ana, id).then().body("name", equalTo(second));
        // somebody else's trainer is not yours to change
        Login cy = t.player();
        patch(cy, id, changed).then().statusCode(403);
        patch(TestAccounts.newUser(), id, changed).then().statusCode(404);
        given().contentType(ContentType.JSON).body(changed).patch("/api/v1/trainers/" + id).then().statusCode(401);
    }

    private static Response get(Login who, String trainer) {
        return given().header("Authorization", who.bearer()).get("/api/v1/trainers/" + trainer);
    }

    @Test
    void afterTheStartOnlyTheAdminCanChangeATrainerThatHasNotPlayed() {
        Table t = table();
        Login ana = t.player();
        String id = post(ana, t.game, choices(uniqueName(), "ace_trainer")).path("id");
        TestWorld.post(t.owner, t.game, "start").then().statusCode(200);
        patch(ana, id, choices(uniqueName(), "breeder")).then().statusCode(409).body("code", equalTo("GAME_STARTED"));
        given().header("Authorization", ana.bearer()).delete("/api/v1/trainers/" + id).then().statusCode(409).body("code", equalTo("GAME_STARTED"));
        patch(t.owner, id, choices(uniqueName(), "breeder")).then().statusCode(200).body("classId", equalTo("breeder"));
    }

    @Test
    void aTrainerThatHasPlayedCannotBeRemade() {
        Table t = table();
        Login ana = t.player();
        String id = post(ana, t.game, choices(uniqueName(), "ace_trainer")).path("id");
        // the game gives it XP in the later phases; here the level of the game's start changes under it
        t.setting("trainer.level.start", 3);
        patch(ana, id, choices(uniqueName(), "breeder")).then().statusCode(409).body("code", equalTo("TRAINER_PROGRESSED"));
    }

    @Test
    void aTrainerCanBeRemovedAndMadeAgain() {
        Table t = table();
        Login ana = t.player();
        String name = uniqueName();
        String id = post(ana, t.game, choices(name, "ace_trainer")).path("id");
        given().header("Authorization", t.player().bearer()).delete("/api/v1/trainers/" + id).then().statusCode(403);
        given().header("Authorization", ana.bearer()).delete("/api/v1/trainers/" + id).then().statusCode(204);
        get(ana, id).then().statusCode(404);
        given().header("Authorization", ana.bearer()).delete("/api/v1/trainers/" + id).then().statusCode(404);
        post(ana, t.game, choices(name, "breeder")).then().statusCode(201);
    }

    // ---------------------------------------------------------------- skins

    private static Response upload(Login who, String trainer, byte[] file, String model) {
        var spec = given().header("Authorization", who.bearer()).multiPart("file", "skin.png", file, "image/png");
        if (model != null) {
            spec = spec.multiPart("model", model);
        }
        return spec.put("/api/v1/trainers/" + trainer + "/skin");
    }

    private static Response copy(Login who, String trainer, String username) {
        return given().header("Authorization", who.bearer()).contentType(ContentType.JSON).body(Map.of("username", username))
                .put("/api/v1/trainers/" + trainer + "/skin");
    }

    @Test
    void anUploadedSkinIsCleanedStoredAndShownToTheGame() throws IOException {
        Table t = table();
        Login ana = t.player();
        Login bob = t.player();
        String id = post(ana, t.game, choices(uniqueName(), "ace_trainer")).path("id");
        given().header("Authorization", bob.bearer()).get("/api/v1/trainers/" + id + "/skin").then().statusCode(404);

        String secret = "private-gps-" + UUID.randomUUID();
        byte[] dirty = Pngs.withMetadata(Pngs.skin(5), secret);
        Response r = upload(ana, id, dirty, "slim");
        r.then().statusCode(200).body("skin.model", equalTo("slim")).body("skin.source", equalTo("upload")).body("skin.sha256", hasLength64());
        String sha = r.path("skin.sha256");

        Response picture = given().header("Authorization", bob.bearer()).get("/api/v1/trainers/" + id + "/skin");
        picture.then().statusCode(200).contentType("image/png").header("ETag", equalTo("\"" + sha + "\""));
        byte[] png = picture.asByteArray();
        assertFalse(new String(png, StandardCharsets.ISO_8859_1).contains(secret), "the metadata did not survive");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
        assertEquals(sha, SkinImages.sha256(png));

        // a stranger, and nobody at all, cannot see it
        given().header("Authorization", TestAccounts.newUser().bearer()).get("/api/v1/trainers/" + id + "/skin").then().statusCode(404);
        given().get("/api/v1/trainers/" + id + "/skin").then().statusCode(401);
        // the trainer shows it
        get(bob, id).then().body("skin.model", equalTo("slim")).body("skin.sha256", equalTo(sha));
        // a new upload replaces it; the default model is classic
        upload(ana, id, Pngs.skin(9), null).then().statusCode(200).body("skin.model", equalTo("classic"));
        // the skin stays when the trainer is remade
        patch(ana, id, choices(uniqueName(), "breeder")).then().statusCode(200).body("skin.model", equalTo("classic"));
        // taking it off
        given().header("Authorization", ana.bearer()).delete("/api/v1/trainers/" + id + "/skin").then().statusCode(200).body("skin", nullValue());
        given().header("Authorization", bob.bearer()).get("/api/v1/trainers/" + id + "/skin").then().statusCode(404);
    }

    private static org.hamcrest.Matcher<String> hasLength64() {
        return org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}");
    }

    @Test
    void badSkinFilesAreRefused() {
        Table t = table();
        Login ana = t.player();
        String id = post(ana, t.game, choices(uniqueName(), "ace_trainer")).path("id");
        upload(ana, id, Pngs.png(64, 32, 0), null).then().statusCode(422).body("code", equalTo("SKIN_WRONG_SIZE"));
        upload(ana, id, Pngs.png(256, 256, 0), null).then().statusCode(422).body("code", equalTo("SKIN_WRONG_SIZE"));
        upload(ana, id, "just some text, long enough to look like a file".getBytes(StandardCharsets.UTF_8), null).then().statusCode(422)
                .body("code", equalTo("SKIN_NOT_PNG"));
        byte[] big = new byte[70_000];
        System.arraycopy(Pngs.skin(1), 0, big, 0, 40);
        upload(ana, id, big, null).then().statusCode(422).body("code", equalTo("SKIN_TOO_LARGE"));
        upload(ana, id, Pngs.skin(1), "wide").then().statusCode(400).body("code", equalTo("INVALID_MODEL"));
        // nothing was kept
        get(ana, id).then().body("skin", nullValue());
    }

    @Test
    void onlyTheOwnerOrTheAdminChangesASkin() {
        Table t = table();
        Login ana = t.player();
        String id = post(ana, t.game, choices(uniqueName(), "ace_trainer")).path("id");
        upload(t.player(), id, Pngs.skin(1), null).then().statusCode(403);
        upload(TestAccounts.newUser(), id, Pngs.skin(1), null).then().statusCode(404);
        given().contentType(ContentType.JSON).body(Map.of("username", "SteveFan")).put("/api/v1/trainers/" + id + "/skin").then().statusCode(401);
        upload(t.owner, id, Pngs.skin(1), null).then().statusCode(200);
    }

    @Test
    void theGameCanSwitchUploadsOffButTheAdminStillCan() {
        Table t = table();
        Login ana = t.player();
        String id = post(ana, t.game, choices(uniqueName(), "ace_trainer")).path("id");
        t.setting("game.skins.allowUpload", false);
        upload(ana, id, Pngs.skin(1), null).then().statusCode(403).body("code", equalTo("SKIN_UPLOAD_OFF"));
        upload(t.owner, id, Pngs.skin(1), null).then().statusCode(200);
        // the other way is still open
        copy(ana, id, "SteveFan").then().statusCode(200);
    }

    @Test
    void aSkinIsCopiedFromAMinecraftName() throws IOException {
        Table t = table();
        Login ana = t.player();
        String id = post(ana, t.game, choices(uniqueName(), "ace_trainer")).path("id");
        copy(ana, id, "SteveFan").then().statusCode(200).body("skin.model", equalTo("classic")).body("skin.source", equalTo("username"))
                .body("skin.username", equalTo("SteveFan"));
        copy(ana, id, "alexfan").then().statusCode(200).body("skin.model", equalTo("slim")).body("skin.username", equalTo("AlexFan"));
        byte[] png = given().header("Authorization", ana.bearer()).get("/api/v1/trainers/" + id + "/skin").asByteArray();
        assertEquals(64, ImageIO.read(new ByteArrayInputStream(png)).getWidth());
    }

    @Test
    void copyingASkinFailsClearly() {
        Table t = table();
        Login ana = t.player();
        String id = post(ana, t.game, choices(uniqueName(), "ace_trainer")).path("id");
        copy(ana, id, "NobodyHere").then().statusCode(404).body("code", equalTo("MINECRAFT_USER_NOT_FOUND"));
        copy(ana, id, "NoSkinFan").then().statusCode(404).body("code", equalTo("MINECRAFT_NO_SKIN"));
        copy(ana, id, "MojangDown").then().statusCode(502).body("code", equalTo("MOJANG_UNAVAILABLE"));
        copy(ana, id, "WeirdFan").then().statusCode(422).body("code", equalTo("SKIN_WRONG_SIZE"));
        copy(ana, id, "not a name!").then().statusCode(400).body("code", equalTo("INVALID_MINECRAFT_NAME"));
        copy(ana, id, "ab").then().statusCode(400).body("code", equalTo("INVALID_MINECRAFT_NAME"));
        given().header("Authorization", ana.bearer()).contentType(ContentType.JSON).body("{}").put("/api/v1/trainers/" + id + "/skin").then().statusCode(400);
        get(ana, id).then().body("skin", nullValue());

        t.setting("game.skins.allowCopyFromUsername", false);
        copy(ana, id, "SteveFan").then().statusCode(403).body("code", equalTo("SKIN_COPY_OFF"));
    }

    @Test
    void lookingUpNamesIsLimitedPerPerson() {
        Table t = table();
        Login ana = t.player();
        String id = post(ana, t.game, choices(uniqueName(), "ace_trainer")).path("id");
        for (int i = 0; i < 10; i++) {
            copy(ana, id, "SteveFan").then().statusCode(200);
        }
        copy(ana, id, "SteveFan").then().statusCode(429);
    }
}
