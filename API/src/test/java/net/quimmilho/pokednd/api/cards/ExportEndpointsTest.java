/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.quimmilho.pokednd.api.TestAccounts;
import net.quimmilho.pokednd.api.TestAccounts.Login;
import net.quimmilho.pokednd.api.auth.RateLimiter;
import net.quimmilho.pokednd.api.domain.cards.ExportJobRepository;
import net.quimmilho.pokednd.api.domain.cards.ExportJobRepository.ExportJob;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.Page;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.SpeciesFilter;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.SpeciesRecord;
import net.quimmilho.pokednd.api.reference.BundleLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The export endpoints on a real PostgreSQL with the real data bundle and card art. Skipped when {@code build/bundle} or
 * {@code resources/} is not built, unless {@code POKEDND_REQUIRE_RESOURCES=true}.
 */
@QuarkusTest
@Tag("database")
class ExportEndpointsTest {

    @Inject
    RateLimiter limiter;

    @Inject
    BundleLoader loader;

    @Inject
    ReferenceRepository reference;

    @Inject
    ExportJobRepository jobs;

    @BeforeEach
    void ready() {
        RealData.repository(); // skips (or fails when resources are required) if the bundle or resources are missing
        limiter.reset();
        loader.load();
    }

    private static Response post(Login who, Map<String, ?> body) {
        return given().header("Authorization", who.bearer()).contentType(ContentType.JSON).body(body).post("/api/v1/cards/exports");
    }

    /** Polls the job until it is no longer queued or running. */
    private static Response finished(Login who, String id) throws InterruptedException {
        long end = System.nanoTime() + java.time.Duration.ofMinutes(3).toNanos();
        Response r;
        do {
            r = given().header("Authorization", who.bearer()).get("/api/v1/cards/exports/" + id);
            String state = r.path("state");
            if (!state.equals("queued") && !state.equals("running")) {
                return r;
            }
            Thread.sleep(200);
        } while (System.nanoTime() < end);
        throw new AssertionError("the export did not finish in time: " + r.asString());
    }

    // ---------------------------------------------------------------- Generation 1

    @Test
    void generationOneGivesAValidPdfWithTheRightNumberOfPages() throws Exception {
        Login me = TestAccounts.newUser();
        int expected = generationOneCards();
        assertTrue(expected >= 151 && expected <= 220, "Generation 1 has 151 species and their alternate forms, not " + expected);

        Response started = post(me, Map.of("generations", List.of(1), "backs", true));
        started.then().statusCode(202).header("Location", containsString("/api/v1/cards/exports/")).body("state", equalTo("queued"))
                .body("total", equalTo(expected)).body("id", notNullValue()).body("downloadUrl", nullValue());
        String id = started.path("id");

        Response done = finished(me, id);
        done.then().statusCode(200).body("state", equalTo("done")).body("progress", equalTo(expected)).body("total", equalTo(expected))
                .body("downloadUrl", equalTo("/api/v1/cards/exports/" + id + "/download")).body("expiresAt", notNullValue());

        Response pdf = given().header("Authorization", me.bearer()).get("/api/v1/cards/exports/" + id + "/download");
        pdf.then().statusCode(200).contentType("application/pdf").header("Content-Disposition", containsString("attachment"));
        byte[] bytes = pdf.asByteArray();
        assertTrue(new String(bytes, 0, 5, StandardCharsets.ISO_8859_1).equals("%PDF-"), "not a PDF");
        int sheets = (expected + 8) / 9; // 3 x 3 cards on an A4 sheet
        assertEquals(2 * sheets, PdfTools.pageCount(bytes), "a front and a back page for every sheet");
        assertEquals((int) done.path("pages"), PdfTools.pageCount(bytes));
        assertTrue(bytes.length < 40_000_000, "a whole generation should stay well below 40 MB, but is " + bytes.length);

        // the manifest lists every card on its front and on its back
        String csv = given().header("Authorization", me.bearer()).get("/api/v1/cards/exports/" + id + "/manifest").then().statusCode(200)
                .contentType("text/csv").extract().asString();
        assertEquals(1 + 2 * expected, csv.lines().count());
        assertTrue(csv.contains("0001_bulbasaur"));
        assertTrue(csv.contains("0151_mew"));

        // it shows in the person's list
        given().header("Authorization", me.bearer()).get("/api/v1/cards/exports").then().statusCode(200).body("[0].id", equalTo(id))
                .body("[0].state", equalTo("done"));
    }

    private int generationOneCards() {
        int n = 0;
        String cursor = null;
        do {
            Page<SpeciesRecord> page = reference.findSpecies(new SpeciesFilter(java.util.Set.of(1), java.util.Set.of(), false, null, true), cursor, 500);
            for (SpeciesRecord s : page.items()) {
                n += CardDataFactory.cardsOf(s, true).size();
            }
            cursor = page.next();
        } while (cursor != null);
        return n;
    }

    // ---------------------------------------------------------------- limits and checks

    @Test
    void aJobOverTheLimitIsRefusedWithAClearMessage() {
        Login me = TestAccounts.newUser();
        // all nine generations twice is far above 1,500 cards
        Response r = post(me, Map.of("generations", List.of(1, 2, 3, 4, 5, 6, 7, 8, 9), "copies", 2));
        r.then().statusCode(422).body("code", equalTo("EXPORT_TOO_LARGE")).body("message", containsString("at most 1500"))
                .body("details.limit", equalTo(1500));
        int printed = r.path("details.printed");
        assertTrue(printed > 1500, "printed " + printed);
        // nothing was queued
        given().header("Authorization", me.bearer()).get("/api/v1/cards/exports").then().statusCode(200).body("size()", equalTo(0));
    }

    @Test
    void badRequestsAreRefusedWithTheirOwnCode() {
        Login me = TestAccounts.newUser();
        post(me, Map.of()).then().statusCode(422).body("code", equalTo("EMPTY_SELECTION"));
        post(me, Map.of("generations", List.of(12))).then().statusCode(422).body("code", equalTo("BAD_GENERATION"));
        post(me, Map.of("cards", List.of("9999_nope", "0001_bulbasaur"))).then().statusCode(422).body("code", equalTo("UNKNOWN_CARD"))
                .body("message", containsString("9999_nope"));
        post(me, Map.of("cards", List.of("0001_bulbasaur"), "paper", "B5")).then().statusCode(422).body("code", equalTo("INVALID_EXPORT_OPTIONS"));
        post(me, Map.of("cards", List.of("0001_bulbasaur"), "copies", 0)).then().statusCode(422).body("code", equalTo("INVALID_EXPORT_OPTIONS"));
        post(me, Map.of("cards", List.of("0001_bulbasaur"), "size", "10x10")).then().statusCode(422).body("code", equalTo("INVALID_EXPORT_OPTIONS"));
        post(me, Map.of("cards", List.of("0001_bulbasaur"), "colour", "neon")).then().statusCode(422).body("code", equalTo("INVALID_EXPORT_OPTIONS"));
        post(me, Map.of("cards", List.of("0001_bulbasaur"), "language", "fr")).then().statusCode(422).body("code", equalTo("INVALID_EXPORT_OPTIONS"));
        post(me, Map.of("cards", List.of("0001_bulbasaur"), "rulesetId", "custom")).then().statusCode(422).body("code", equalTo("RULESET_UNSUPPORTED"));
    }

    @Test
    void bleedAndGapCanBeChosenAndAreChecked() throws Exception {
        Login me = TestAccounts.newUser();
        Response started = post(me, Map.of("cards", List.of("0001_bulbasaur"), "bleedMm", 0, "gapMm", 5.5));
        started.then().statusCode(202).body("options.bleedMm", equalTo(0.0f)).body("options.gapMm", equalTo(5.5f));
        finished(me, started.path("id"));
        // without them the settings decide (3 mm and 2 mm)
        post(me, Map.of("cards", List.of("0001_bulbasaur"))).then().statusCode(202).body("options.bleedMm", equalTo(3.0f)).body("options.gapMm", equalTo(2.0f));
        post(me, Map.of("cards", List.of("0001_bulbasaur"), "bleedMm", 11)).then().statusCode(422).body("code", equalTo("INVALID_EXPORT_OPTIONS"));
        post(me, Map.of("cards", List.of("0001_bulbasaur"), "gapMm", -1)).then().statusCode(422).body("code", equalTo("INVALID_EXPORT_OPTIONS"));
    }

    @Test
    void theSelectionIsTheGenerationAndTypeFiltersPlusTheCardsNamedByHand() throws Exception {
        Login me = TestAccounts.newUser();
        // only Fire species of generation 1, no alternate forms: Charmander, Charmeleon, Charizard, Vulpix, Ninetales, Growlithe, Arcanine, Ponyta, Rapidash, Magmar, Flareon, Moltres
        Response fire = post(me, Map.of("generations", List.of(1), "types", List.of("Fire"), "includeForms", false));
        fire.then().statusCode(202).body("total", equalTo(12));
        // the same plus two cards by hand, one of them an alternate form of another generation
        Response plus = post(me, Map.of("generations", List.of(1), "types", List.of("fire"), "includeForms", false, "cards",
                List.of("0105_marowak_alolan", "0001_bulbasaur", "0004_charmander")));
        plus.then().statusCode(202).body("total", equalTo(14));
        // only by hand
        post(me, Map.of("cards", List.of("0150_mewtwo"))).then().statusCode(202).body("total", equalTo(1));
        finished(me, fire.path("id"));
        finished(me, plus.path("id"));
    }

    @Test
    void optionsReachThePdf() throws Exception {
        Login me = TestAccounts.newUser();
        // 3 cards x 2 copies = 6 printed cards, tarot size (2 x 2 per sheet) = 2 sheets, with backs = 4 pages
        Response started = post(me, Map.of("cards", List.of("0001_bulbasaur", "0004_charmander", "0007_squirtle"), "copies", 2, "size", "70x120",
                "backs", true, "language", "pt", "colour", "ink-saving"));
        started.then().statusCode(202).body("options.size", equalTo("70x120")).body("options.language", equalTo("pt"))
                .body("options.inkSaving", equalTo(true));
        Response done = finished(me, started.path("id"));
        done.then().body("state", equalTo("done")).body("pages", equalTo(4));
        byte[] pdf = given().header("Authorization", me.bearer()).get(done.path("downloadUrl").toString()).asByteArray();
        assertEquals(4, PdfTools.pageCount(pdf));
        String text = PdfTools.text(pdf, 1);
        assertTrue(text.contains("Projeto de fãs"), text);
        assertTrue(text.contains("Habilidade:"), text);
    }

    // ---------------------------------------------------------------- who may do what

    @Test
    void anonymousPeopleAreRefusedAndEachPersonSeesOnlyTheirOwnJobs() throws Exception {
        given().contentType(ContentType.JSON).body(Map.of("generations", List.of(1))).post("/api/v1/cards/exports").then().statusCode(401)
                .body("code", equalTo("UNAUTHENTICATED"));
        given().get("/api/v1/cards/exports").then().statusCode(401);
        given().get("/api/v1/cards/exports/" + UUID.randomUUID()).then().statusCode(401);
        given().get("/api/v1/cards/exports/" + UUID.randomUUID() + "/download").then().statusCode(401);
        given().header("Authorization", "Bearer nonsense").get("/api/v1/cards/exports").then().statusCode(401);

        Login anna = TestAccounts.newUser();
        Login bruno = TestAccounts.newUser();
        String id = post(anna, Map.of("cards", List.of("0025_pikachu"))).path("id");
        finished(anna, id);
        // Bruno cannot see, poll or download Anna's job: it looks as if it did not exist
        given().header("Authorization", bruno.bearer()).get("/api/v1/cards/exports/" + id).then().statusCode(404);
        given().header("Authorization", bruno.bearer()).get("/api/v1/cards/exports/" + id + "/download").then().statusCode(404);
        given().header("Authorization", bruno.bearer()).get("/api/v1/cards/exports").then().statusCode(200).body("size()", equalTo(0));
        // a made-up id is a 404 too, not an error
        given().header("Authorization", anna.bearer()).get("/api/v1/cards/exports/not-an-id").then().statusCode(404);
        // the site admin has no extra right to other people's jobs (they are theirs alone)
        given().header("Authorization", TestAccounts.admin().bearer()).get("/api/v1/cards/exports/" + id).then().statusCode(404);
    }

    @Test
    void aJobThatIsNotFinishedCannotBeDownloadedYet() {
        Login me = TestAccounts.newUser();
        // store a queued job by hand, so that no worker touches it
        ExportJob job = new ExportJob(UUID.randomUUID(), UUID.fromString(me.id()), new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode(),
                ExportJobRepository.QUEUED, 3, 10, null, List.of(), null, null, null, null, Instant.now(), Instant.now());
        jobs.create(job);
        given().header("Authorization", me.bearer()).get("/api/v1/cards/exports/" + job.id() + "/download").then().statusCode(409)
                .body("code", equalTo("EXPORT_NOT_READY")).body("message", containsString("3 of 10"));
    }

    // ---------------------------------------------------------------- expiry

    @Test
    void anExpiredFileIsDeletedByTheSchedulerAndAnswers404() throws Exception {
        Login me = TestAccounts.newUser();
        String id = post(me, Map.of("cards", List.of("0001_bulbasaur", "0004_charmander", "0007_squirtle"))).path("id");
        Response done = finished(me, id);
        done.then().body("state", equalTo("done"));

        ExportJob job = jobs.find(UUID.fromString(id)).orElseThrow();
        Path pdf = Path.of(job.filePath());
        Path manifest = Path.of(job.manifestPath());
        assertTrue(Files.isRegularFile(pdf) && Files.isRegularFile(manifest));
        // it lives for 24 hours (the setting cards.export.fileTtlHours)
        long hours = java.time.Duration.between(job.createdAt(), job.expiresAt()).toHours();
        assertTrue(hours == 23 || hours == 24, "expires in " + hours + " hours");
        given().header("Authorization", me.bearer()).get("/api/v1/cards/exports/" + id + "/download").then().statusCode(200);

        // the time is up
        jobs.update(job.withExpiry(Instant.now().minusSeconds(5)));
        // the scheduler (every second in tests) removes the files and marks the job
        long end = System.nanoTime() + java.time.Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < end && (Files.exists(pdf) || Files.exists(manifest))) {
            Thread.sleep(200);
        }
        assertFalse(Files.exists(pdf), "the PDF should be deleted");
        assertFalse(Files.exists(manifest), "the manifest should be deleted");
        given().header("Authorization", me.bearer()).get("/api/v1/cards/exports/" + id + "/download").then().statusCode(404)
                .body("code", equalTo("EXPORT_EXPIRED")).body("message", containsString("24 hours"));
        given().header("Authorization", me.bearer()).get("/api/v1/cards/exports/" + id + "/manifest").then().statusCode(404);
        given().header("Authorization", me.bearer()).get("/api/v1/cards/exports/" + id).then().statusCode(200).body("state", equalTo("expired"))
                .body("downloadUrl", nullValue());
    }

    @Test
    void aFileThatIsPastItsTimeAnswers404EvenBeforeTheSchedulerRuns() throws IOException {
        Login me = TestAccounts.newUser();
        // a "done" job whose time is up, with a real file on disk
        Path dir = Files.createDirectories(Path.of("build/test-data/exports"));
        Path file = dir.resolve(UUID.randomUUID() + ".pdf");
        Files.writeString(file, "%PDF-1.4 stand-in");
        ExportJob job = new ExportJob(UUID.randomUUID(), UUID.fromString(me.id()), new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode(),
                ExportJobRepository.DONE, 1, 1, null, List.of(), file.toString(), null, 1, Instant.now().minusSeconds(3600), Instant.now(), Instant.now());
        jobs.create(job);
        given().header("Authorization", me.bearer()).get("/api/v1/cards/exports/" + job.id() + "/download").then().statusCode(404)
                .body("code", equalTo("EXPORT_EXPIRED"));
        assertFalse(Files.exists(file), "the file is deleted when somebody asks for it too late");
    }

    @Test
    void personsAreLimitedInHowManyJobsTheyStartPerHour() {
        Login me = TestAccounts.newUser();
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            statuses.add(post(me, Map.of("cards", List.of("0001_bulbasaur"))).statusCode());
        }
        assertEquals(20, statuses.stream().filter(s -> s == 202).count());
        assertEquals(429, statuses.get(20));
    }
}
