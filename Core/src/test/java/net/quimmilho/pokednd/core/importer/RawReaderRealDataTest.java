/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The importer on the real {@code resources/} folder (rebuild it with {@code node tools/extract.mjs}).
 * Skipped when the folder is missing, unless {@code POKEDND_REQUIRE_RESOURCES=true}, which CI sets.
 */
class RawReaderRealDataTest {

    private static Path resources;
    private static Raw raw;

    @BeforeAll
    static void load() {
        resources = Path.of(System.getProperty("pokednd.resources", "../resources"));
        boolean there = Files.isRegularFile(resources.resolve("pokedex.csv"));
        if (!there && "true".equals(System.getenv("POKEDND_REQUIRE_RESOURCES"))) {
            fail("resources/ is missing but POKEDND_REQUIRE_RESOURCES=true. Run: node tools/extract.mjs");
        }
        assumeTrue(there, "resources/ is not built; run: node tools/extract.mjs");
        raw = RawReader.read(resources);
    }

    @Test
    void rowCountsAreTheExpectedOnes() {
        assertEquals(1025, raw.pokedex().size(), "pokedex.csv");
        assertEquals(929, raw.attacks().size(), "attack.csv: 930 rows, 1 skipped");
        assertEquals(130_704, raw.learnsets().size(), "pokemon-attacks.csv");
        assertEquals(390, raw.items().size(), "item.csv");
        assertEquals(391, raw.tms().size(), "tm.csv");
        assertEquals(1025, raw.species().size(), "species JSON files");

        assertEquals(1025, raw.reports().get("pokedex.csv").rowsRead());
        assertEquals(930, raw.reports().get("attack.csv").rowsRead());
        assertEquals(1025, raw.reports().get("data/cobblemon/species").rowsRead());
    }

    @Test
    void theOnlyRowSkippedAnywhereIsNihilLight() {
        for (FileReport report : raw.reports().values()) {
            if (report.file().equals("attack.csv")) {
                assertEquals(1, report.skipped().size(), report.skipped().toString());
                assertTrue(report.skipped().get(0).message().startsWith("nihillight:"), report.skipped().get(0).message());
            } else {
                assertEquals(List.of(), report.skipped(), report.file() + " skipped rows");
            }
        }
    }

    @Test
    void everyKnownQuirkWasRepairedAndReported() {
        for (String csv : List.of("pokedex.csv", "attack.csv", "item.csv", "pokemon-attacks.csv", "tm.csv")) {
            assertTrue(raw.reports().get(csv).fixes().stream().anyMatch(i -> i.message().contains("byte-order mark")), csv + " has a BOM");
        }
        assertTrue(raw.reports().get("pokedex.csv").fixes().stream().anyMatch(i -> i.message().contains("Attcak EVs")));
        assertFalse(raw.reports().get("pokedex.csv").fixes().stream().anyMatch(i -> i.message().contains("differs from Type I/II")),
                "the combined Type text and the Type I/II columns agree for all 1,025 rows");
    }

    @Test
    void pokedexRowsAreCleanAndTyped() {
        Raw.PokedexRow bulbasaur = raw.pokedex().get(0);
        assertEquals(1, bulbasaur.dex());
        assertEquals("Bulbasaur", bulbasaur.name());
        assertEquals("Grass", bulbasaur.type1());
        assertEquals(Optional.of("Poison"), bulbasaur.type2());
        assertEquals(45, bulbasaur.baseStats().hp());
        assertEquals(1, bulbasaur.evYield().specialAttack());
        assertEquals(Optional.of(87.5), bulbasaur.malePercent());
        assertEquals(Optional.of("Ivysaur (Level 16)"), bulbasaur.evolutionText());
        assertEquals(Optional.empty(), raw.pokedex().get(3).type2(), "Charmander has one type");
        // dex numbers are 1..1025, once each, in order
        for (int i = 0; i < raw.pokedex().size(); i++) {
            assertEquals(i + 1, raw.pokedex().get(i).dex());
        }
        assertTrue(raw.pokedex().stream().anyMatch(p -> p.malePercent().isEmpty()), "genderless species exist");
        assertTrue(raw.pokedex().stream().allMatch(p -> p.type1() != null && !p.type1().isBlank()));
    }

    @Test
    void attacksNeverMissWhereTheFileHasADash() {
        long neverMiss = raw.attacks().stream().filter(m -> m.accuracy().isEmpty()).count();
        assertEquals(303, neverMiss, "accuracy — appears in 303 rows of attack.csv");
        assertTrue(raw.attacks().stream().noneMatch(m -> m.id().equals("nihillight")));
        Raw.Move first = raw.attacks().get(0);
        assertEquals("10000000voltthunderbolt", first.id());
        assertEquals("10,000,000 Volt Thunderbolt", first.name(), "a comma inside quotes stays in the name");
        assertEquals(195, first.power());
    }

    @Test
    void learnsetsHaveTheDocumentedMethodsFormsAndFlags() {
        Set<String> methods = raw.learnsets().stream().map(Raw.LearnsetRow::method).collect(Collectors.toSet());
        assertEquals(Set.of("level", "egg", "tm", "tutor", "legacy", "special", "form_change"), methods);
        // 63 distinct values of the form column: the blank one (the base form) and 62 named forms
        assertEquals(63, raw.learnsets().stream().map(Raw.LearnsetRow::form).distinct().count(), "63 distinct form values");
        assertEquals(62, raw.learnsets().stream().flatMap(l -> l.form().stream()).distinct().count(), "62 named forms");
        assertEquals(112_863, raw.learnsets().stream().filter(Raw.LearnsetRow::implemented).count());
        assertEquals(17_841, raw.learnsets().stream().filter(l -> !l.implemented()).count());
        assertTrue(raw.learnsets().stream().filter(l -> l.method().equals("level")).allMatch(l -> l.level().isPresent()),
                "every level-up row has a level");
    }

    @Test
    void tmsAndItemsKeepTheirSourcesAndCategories() {
        assertEquals(334, raw.tms().stream().filter(t -> t.source().equals("cobblemon")).count());
        assertEquals(57, raw.tms().stream().filter(t -> t.source().equals("generated")).count());
        assertTrue(raw.tms().stream().filter(t -> t.source().equals("generated")).allMatch(t -> t.obtain().isEmpty()));
        assertEquals(7, raw.items().stream().filter(i -> i.category().equals("pokedexes")).count(),
                "the 7 Pokédex rows are still here; they are dropped when items are built");
        assertEquals(371, raw.items().stream().filter(i -> i.description().isPresent()).count(), "371 of 390 items have a description");
    }

    @Test
    void speciesJsonIsIndexedByDexAndMatchesCobblemonsFlag() {
        assertEquals(1025, raw.species().size());
        assertEquals(1, raw.species().firstKey());
        assertEquals(1025, raw.species().lastKey());
        assertEquals(905, raw.species().values().stream().filter(Raw.SpeciesFile::implemented).count());
        Raw.SpeciesFile bulbasaur = raw.species().get(1);
        assertEquals("Bulbasaur", bulbasaur.name());
        assertEquals("grass", bulbasaur.primaryType());
        assertEquals(Optional.of("poison"), bulbasaur.secondaryType());
        assertEquals(1, bulbasaur.evolutions().size());
        assertTrue(bulbasaur.moves().contains("1:tackle"));
        assertTrue(bulbasaur.source().startsWith("data/cobblemon/species/generation1/"));
    }

    @Test
    void readingTwiceGivesEqualResults() {
        assertEquals(raw, RawReader.read(resources));
    }
}
