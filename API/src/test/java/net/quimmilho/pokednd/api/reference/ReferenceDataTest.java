/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.reference;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository;
import net.quimmilho.pokednd.api.reference.BundleLoader.Outcome;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The reference data on a real PostgreSQL (Quarkus Dev Services starts it with Testcontainers) and the real data bundle in
 * {@code build/bundle}. Skipped when the bundle is not built, unless {@code POKEDND_REQUIRE_RESOURCES=true}.
 */
@QuarkusTest
@Tag("database")
class ReferenceDataTest {

    @Inject
    BundleLoader loader;

    @Inject
    ReferenceRepository repository;

    @ConfigProperty(name = "pokednd.bundle.dir")
    String bundleDir;

    @BeforeEach
    void bundleIsThere() {
        boolean there = loader.bundleExists();
        if (!there && "true".equals(System.getenv("POKEDND_REQUIRE_RESOURCES"))) {
            fail("The data bundle is missing but POKEDND_REQUIRE_RESOURCES=true. Run the importer: see README.md");
        }
        assumeTrue(there, "The data bundle (build/bundle) is not built, so the reference-data tests are skipped");
        loader.load();
    }

    // ---------------------------------------------------------------- loader

    @Test
    void loadingTheSameBundleTwiceDoesNotDuplicateRows() {
        var before = repository.counts();
        assertEquals(BundleLoader.Outcome.UNCHANGED, loader.load().outcome());
        assertEquals(before, repository.counts());
        // even a forced reload of the same files leaves the same number of rows
        assertEquals(Outcome.LOADED, loader.load(Path.of(bundleDir), true).outcome());
        assertEquals(before, repository.counts());
        assertEquals(1025, before.species());
        assertEquals(929, before.moves());
        assertTrue(before.items() > 700, "items incl. TMs: " + before.items());
        assertEquals(284, before.abilities());
    }

    @Test
    void aChangedHashReloads(@TempDir Path tmp) throws IOException {
        Path copy = tmp.resolve("bundle");
        copyFolder(Path.of(bundleDir), copy);
        String original = Files.readString(copy.resolve("MANIFEST.json"));
        String hash = loader.currentHash().orElseThrow();
        assertTrue(original.contains(hash));
        Files.writeString(copy.resolve("MANIFEST.json"), original.replace(hash, "0".repeat(64)));

        var result = loader.load(copy, false);
        assertEquals(Outcome.LOADED, result.outcome());
        assertEquals("0".repeat(64), repository.loadedBundle().orElseThrow().hash());
        assertEquals(1025, repository.counts().species());
        // the same copy again changes nothing
        assertEquals(Outcome.UNCHANGED, loader.load(copy, false).outcome());
        // going back to the real bundle is a change again
        assertEquals(Outcome.LOADED, loader.load().outcome());
        assertEquals(hash, repository.loadedBundle().orElseThrow().hash());
    }

    @Test
    void aBrokenBundleIsRefusedAndTheOldDataStays(@TempDir Path tmp) throws IOException {
        Path copy = tmp.resolve("bundle");
        copyFolder(Path.of(bundleDir), copy);
        Files.delete(copy.resolve("moves.json"));
        Files.writeString(copy.resolve("MANIFEST.json"), Files.readString(copy.resolve("MANIFEST.json"))
                .replace(loader.currentHash().orElseThrow(), "1".repeat(64)));
        var ex = org.junit.jupiter.api.Assertions.assertThrows(net.quimmilho.pokednd.api.error.ApiException.class,
                () -> loader.load(copy, false));
        assertEquals("BUNDLE_INVALID", ex.code());
        assertEquals(1025, repository.counts().species());
    }

    // ---------------------------------------------------------------- endpoints

    @Test
    void oneSpeciesHasStatsDerivedValuesAndEvolutions() {
        given().when().get("/api/v1/data/species/bulbasaur").then().statusCode(200)
                .body("dex", equalTo(1))
                .body("name", equalTo("Bulbasaur"))
                .body("types", equalTo(java.util.List.of("grass", "poison")))
                .body("baseStats.hp", equalTo(45))
                .body("baseStats.speed", equalTo(45))
                .body("generation", equalTo(1))
                .body("derived.stage", equalTo("BASIC"))
                .body("derived.signature.bulbasaur_base", hasSize(4))
                .body("evolutions[0].to", equalTo("ivysaur"))
                .body("forms.id", hasItem("bulbasaur_base"));
        given().when().get("/api/v1/data/species/marowak").then().statusCode(200)
                .body("forms.id", hasItem("marowak_alolan"))
                .body("derived.stage", equalTo("STAGE_1"));
    }

    @Test
    void formsCarryTheirOwnTypesAbilitiesAndWeaknesses() {
        given().when().get("/api/v1/data/species/marowak").then().statusCode(200)
                .body("types", equalTo(java.util.List.of("ground")))
                .body("forms.find { it.id == 'marowak_alolan' }.types", equalTo(java.util.List.of("fire", "ghost")))
                .body("forms.find { it.id == 'marowak_alolan' }.hiddenAbility", equalTo("rockhead"))
                .body("forms.find { it.id == 'marowak_alolan' }.weaknesses.water", equalTo(2.0f))
                .body("forms.find { it.id == 'marowak_base' }.types", equalTo(java.util.List.of("ground")));
    }

    @Test
    void speciesListFiltersByGenerationAndTypeAndPages() {
        given().queryParam("generation", 1).queryParam("limit", 500).when().get("/api/v1/data/species").then().statusCode(200)
                .body("items", hasSize(151))
                .body("items[0].id", equalTo("bulbasaur"))
                .body("items[150].id", equalTo("mew"))
                .body("next", nullValue());

        Response first = given().queryParam("generation", 1).queryParam("limit", 100).when().get("/api/v1/data/species");
        first.then().statusCode(200).body("items", hasSize(100)).body("next", notNullValue());
        String next = first.path("next");
        given().queryParam("generation", 1).queryParam("limit", 100).queryParam("cursor", next).when().get("/api/v1/data/species").then()
                .statusCode(200).body("items", hasSize(51)).body("items[0].dex", equalTo(101)).body("next", nullValue());

        given().queryParam("generation", 1).queryParam("type", "fire").when().get("/api/v1/data/species").then().statusCode(200)
                .body("items.id", hasItem("charmander"))
                .body("items.id", hasItem("moltres"))
                .body("items.id", not(hasItem("bulbasaur")));
        // Pidgey is Normal/Flying: it is found by 'flying' only when either type counts
        given().queryParam("generation", 1).queryParam("type", "flying").when().get("/api/v1/data/species").then().statusCode(200)
                .body("items.id", hasItem("pidgey"));
        given().queryParam("generation", 1).queryParam("type", "flying").queryParam("primaryTypeOnly", true).when()
                .get("/api/v1/data/species").then().statusCode(200).body("items.id", not(hasItem("pidgey")));
        given().queryParam("generation", 1).queryParam("type", "normal").queryParam("primaryTypeOnly", true).when()
                .get("/api/v1/data/species").then().statusCode(200).body("items.id", hasItem("pidgey"));
        given().queryParam("q", "chari").when().get("/api/v1/data/species").then().statusCode(200).body("items.id", hasItem("charizard"));
    }

    @Test
    void movesItemsAndAbilities() {
        given().when().get("/api/v1/data/moves/tackle").then().statusCode(200)
                .body("name", equalTo("Tackle")).body("type", equalTo("Normal")).body("power", equalTo(40)).body("pp", equalTo(35));
        given().queryParam("type", "fire").queryParam("limit", 500).when().get("/api/v1/data/moves").then().statusCode(200)
                .body("items.id", hasItem("flamethrower")).body("items.id", not(hasItem("tackle")));
        given().when().get("/api/v1/data/items/poke_ball").then().statusCode(200).body("name", equalTo("Poké Ball"));
        given().queryParam("q", "potion").when().get("/api/v1/data/items").then().statusCode(200).body("items", not(empty()));
        given().when().get("/api/v1/data/abilities/overgrow").then().statusCode(200).body("name", equalTo("Overgrow"));
        given().queryParam("limit", 10).when().get("/api/v1/data/abilities").then().statusCode(200).body("items", hasSize(10))
                .body("next", notNullValue());
    }

    @Test
    void typesNaturesRosterAndLearnsets() {
        given().when().get("/api/v1/data/types").then().statusCode(200).body("types", hasItem("fire")).body("chart.fire.grass", equalTo(2.0f));
        given().when().get("/api/v1/data/natures").then().statusCode(200).body("natures", hasSize(25));
        given().when().get("/api/v1/data/roster").then().statusCode(200).body("species.'1'.id", equalTo("bulbasaur"));
        given().when().get("/api/v1/data/learnsets/bulbasaur").then().statusCode(200).body("species", equalTo("bulbasaur"))
                .body("forms.bulbasaur_base.level", not(empty()));
        given().queryParam("method", "level").when().get("/api/v1/data/learnsets/bulbasaur").then().statusCode(200)
                .body("forms.bulbasaur_base.tm", nullValue()).body("forms.bulbasaur_base.level.move", hasItem("vinewhip"));
        given().queryParam("form", "nope").when().get("/api/v1/data/learnsets/bulbasaur").then().statusCode(404);
    }

    @Test
    void errorsUseTheSingleShape() {
        given().when().get("/api/v1/data/species/missingno").then().statusCode(404).body("code", equalTo("NOT_FOUND"))
                .body("message", containsString("missingno"));
        given().queryParam("generation", 12).when().get("/api/v1/data/species").then().statusCode(400).body("code", equalTo("BAD_GENERATION"));
        given().queryParam("limit", 0).when().get("/api/v1/data/moves").then().statusCode(400).body("code", equalTo("BAD_LIMIT"));
        given().queryParam("cursor", "garbage").when().get("/api/v1/data/species").then().statusCode(400).body("code", equalTo("BAD_CURSOR"));
    }

    @Test
    void answersCarryAnEtagAndAnUnchangedRequestGets304() {
        Response r = given().when().get("/api/v1/data/moves/tackle");
        String etag = r.header("ETag");
        assertEquals("\"" + loader.currentHash().orElseThrow() + "\"", etag);
        given().header("If-None-Match", etag).when().get("/api/v1/data/moves/tackle").then().statusCode(304);
        given().header("If-None-Match", "\"other\"").when().get("/api/v1/data/moves/tackle").then().statusCode(200);
    }

    private static void copyFolder(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(p, target);
                }
            }
        }
    }
}
