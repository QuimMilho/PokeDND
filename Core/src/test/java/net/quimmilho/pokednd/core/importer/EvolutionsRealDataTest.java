/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import net.quimmilho.pokednd.core.importer.EvolutionParser.Handling;
import net.quimmilho.pokednd.core.importer.EvolutionParser.Method;
import net.quimmilho.pokednd.core.importer.EvolutionParser.Type;
import net.quimmilho.pokednd.core.importer.Evolutions.Built;
import net.quimmilho.pokednd.core.importer.Evolutions.Rule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The evolution rules built from the real {@code resources/}. Skipped when it is missing (CI sets the require flag). */
class EvolutionsRealDataTest {

    private static Built built;
    private static Path resources;

    @BeforeAll
    static void load() {
        resources = Path.of(System.getProperty("pokednd.resources", "../resources"));
        boolean there = Files.isRegularFile(resources.resolve("pokedex.csv"));
        if (!there && "true".equals(System.getenv("POKEDND_REQUIRE_RESOURCES"))) {
            fail("resources/ is missing but POKEDND_REQUIRE_RESOURCES=true. Run: node tools/extract.mjs");
        }
        assumeTrue(there, "resources/ is not built");
        built = Evolutions.build(RawReader.read(resources));
    }

    private static List<Rule> from(String species) {
        return built.rules().stream().filter(r -> r.from().equals(species)).toList();
    }

    @Test
    void all450StringsParseAndTheReviewedUnparseableListIsEmpty() {
        assertEquals(450, built.stringsRead());
        assertEquals(619, built.rules().size());
        // Reviewed on 2026-11-08: every condition in the data is understood, so nothing needs the unknown fallback.
        assertEquals(List.of(), built.unparseable());
        assertTrue(built.rules().stream().noneMatch(r -> r.handling() == Handling.UNKNOWN));
    }

    @Test
    void theCrossCheckAgainstTheSpeciesJsonHasNoUnexplainedMismatch() {
        assertEquals(List.of(), built.mismatches());
        assertEquals(List.of(), built.unresolved(), "every target, item and move name matches something");
    }

    @Test
    void eeveeHasAllEightEvolutions() {
        List<Rule> eevee = from("eevee");
        assertEquals(List.of("vaporeon", "jolteon", "flareon", "espeon", "umbreon", "leafeon", "glaceon", "sylveon"),
                eevee.stream().map(Rule::to).toList());
        assertEquals("water_stone", eevee.get(0).item());
        assertEquals(Method.USE_ITEM, eevee.get(0).method());
        Rule espeon = eevee.get(3);
        assertEquals(Method.LEVEL_UP, espeon.method());
        assertEquals(List.of(Type.FRIENDSHIP, Type.TIME_OF_DAY), espeon.conditions().stream().map(c -> c.type()).toList());
        assertEquals(160, espeon.conditions().get(0).amount());
        assertEquals(Type.KNOWS_MOVE_TYPE, eevee.get(7).conditions().get(1).type());
    }

    @Test
    void pikachuEvolvesByThunderStoneAndRaichuHasAnAlolanVariantInAnAlolanBiome() {
        List<Rule> pikachu = from("pikachu");
        assertEquals(2, pikachu.size());
        assertEquals("thunder_stone", pikachu.get(0).item());
        assertEquals("", pikachu.get(0).toForm());
        assertEquals("alolan", pikachu.get(1).toForm());
        assertEquals(Handling.REGION_TAG, pikachu.get(1).handling());
    }

    @Test
    void wurmpleBecomesSilcoonOrCascoonByAHiddenValue() {
        List<Rule> wurmple = from("wurmple");
        assertEquals(List.of("silcoon", "cascoon"), wurmple.stream().map(Rule::to).toList());
        assertEquals(7, wurmple.get(0).conditions().get(0).amount());
        assertEquals("cocoon_species", wurmple.get(0).conditions().get(1).value());
        assertEquals("cascoon", wurmple.get(1).conditions().get(1).other());
    }

    @Test
    void slowpokeEvolvesByLevelAndByItem() {
        List<Rule> slowpoke = from("slowpoke");
        assertTrue(slowpoke.size() >= 2, slowpoke.toString());
        assertTrue(slowpoke.stream().anyMatch(r -> r.to().equals("slowbro") && r.method() == Method.LEVEL_UP));
        assertTrue(slowpoke.stream().anyMatch(r -> r.to().equals("slowking")));
    }

    @Test
    void aTradeEvolutionAndATradeWithAHeldItem() {
        Rule kadabra = from("kadabra").get(0);
        assertEquals("alakazam", kadabra.to());
        assertEquals(Method.TRADE, kadabra.method());
        assertEquals(List.of(), kadabra.conditions());
        Rule poliwhirl = from("poliwhirl").stream().filter(r -> r.to().equals("politoed")).findFirst().orElseThrow();
        assertEquals(Method.TRADE, poliwhirl.method());
        assertEquals("kings_rock", poliwhirl.conditions().get(0).value());
    }

    @Test
    void aLevelPlusTimeOfDayEvolution() {
        Rule rockruff = from("rockruff").stream()
                .filter(r -> r.conditions().stream().anyMatch(c -> c.type() == Type.TIME_OF_DAY)).findFirst().orElseThrow();
        assertEquals(Method.LEVEL_UP, rockruff.method());
        assertEquals(Type.LEVEL, rockruff.conditions().get(0).type());
    }

    @Test
    void theAdminConfirmsTheMoonAndGimmighoulCoins() {
        assertEquals(Handling.ADMIN, from("ursaring").get(0).handling());
        assertTrue(from("gimmighoul").stream().allMatch(r -> r.handling() == Handling.ADMIN));
    }

    @Test
    void rulesGroupedByWhoChecksThemAddUp() {
        var perHandling = built.rules().stream().collect(Collectors.groupingBy(Rule::handling, Collectors.counting()));
        assertEquals(571L, perHandling.get(Handling.AUTOMATIC));
        assertEquals(37L, perHandling.get(Handling.REGION_TAG));
        assertEquals(11L, perHandling.get(Handling.ADMIN));
    }

    @Test
    void theReportIsTheSameTextEveryTime() {
        assertEquals(Evolutions.report(built), Evolutions.report(Evolutions.build(RawReader.read(resources))));
    }
}
