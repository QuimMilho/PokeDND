/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import net.quimmilho.pokednd.core.importer.Catalog.AbilityEntry;
import net.quimmilho.pokednd.core.importer.Catalog.Built;
import net.quimmilho.pokednd.core.importer.Catalog.EffectClass;
import net.quimmilho.pokednd.core.importer.Catalog.ItemEntry;
import net.quimmilho.pokednd.core.importer.Catalog.MoveEntry;
import net.quimmilho.pokednd.core.importer.SpeciesMerger.Merged;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Moves, abilities, items and TMs built from the real {@code resources/}. Skipped when {@code resources/} or
 * {@code build/roster.json} are missing, unless {@code POKEDND_REQUIRE_RESOURCES=true}.
 */
class CatalogRealDataTest {

    private static Raw raw;
    private static Merged merged;
    private static Built built;
    private static Path coverageJson;

    @BeforeAll
    static void load() {
        Path resources = Path.of(System.getProperty("pokednd.resources", "../resources"));
        Path rosterFile = Path.of(System.getProperty("pokednd.roster", "../build/roster.json"));
        boolean there = Files.isRegularFile(resources.resolve("pokedex.csv")) && Files.isRegularFile(rosterFile);
        if (!there && "true".equals(System.getenv("POKEDND_REQUIRE_RESOURCES"))) {
            fail("resources/ or build/roster.json is missing but POKEDND_REQUIRE_RESOURCES=true. "
                    + "Run: node tools/extract.mjs && node tools/check-assets.mjs");
        }
        assumeTrue(there, "resources/ or build/roster.json is not built");
        raw = RawReader.read(resources);
        merged = SpeciesMerger.merge(raw, Roster.read(rosterFile));
        built = Catalog.build(raw, merged.species(), Showdown.load(), Catalog.english(resources));
        coverageJson = rosterFile.resolveSibling("reports/coverage.json");
    }

    private static JsonNode coverage(String part) throws IOException {
        assumeTrue(Files.isRegularFile(coverageJson), "build/reports/coverage.json is not built (node tools/effect-coverage.mjs)");
        return new ObjectMapper().readTree(Files.readString(coverageJson)).get(part);
    }

    private static ItemEntry item(String id) {
        return built.items().stream().filter(i -> i.id().equals(id)).findFirst().orElseThrow(() -> new AssertionError("no item " + id));
    }

    @Test
    void theMoveClassCountsEqualTheCoverageReport() {
        // 445 automatic and 397 scripted; 87 Z/Max moves are manual. The 1 move without data (nihillight) has no stats and is
        // left out of the table, so 929 remain.
        assertEquals(929, built.moves().size());
        assertEquals(445, built.moves().stream().filter(m -> m.effectClass() == EffectClass.AUTOMATIC).count());
        assertEquals(397, built.moves().stream().filter(m -> m.effectClass() == EffectClass.SCRIPTED).count());
        assertEquals(87, built.moves().stream().filter(m -> m.effectClass() == EffectClass.MANUAL && m.reason().contains("z_max")).count());
        assertEquals(87, built.moves().stream().filter(m -> m.effectClass() == EffectClass.MANUAL).count());
        assertEquals(1, built.moveRowsSkipped().size());
        assertTrue(built.moveRowsSkipped().get(0).startsWith("nihillight"));
    }

    @Test
    void everyMoveHasTheSameClassAsInTheCoverageReport() throws IOException {
        JsonNode cov = coverage("moves");
        for (MoveEntry m : built.moves()) {
            String want = cov.get(m.id()).asText();
            String got = switch (m.effectClass()) {
                case AUTOMATIC -> "automatic";
                case SCRIPTED -> "scripted";
                case MANUAL -> "z/max";
            };
            assertEquals(want, got, m.id());
        }
    }

    @Test
    void abilitiesUsedByModelledSpeciesMatchTheCoverageReport() throws IOException {
        JsonNode cov = coverage("abilities");
        Set<String> used = new TreeSet<>();
        merged.species().stream().filter(s -> s.implemented()).forEach(s -> { // the coverage report counts the 905 modelled species
            used.addAll(s.abilities());
            s.hiddenAbility().ifPresent(used::add);
        });
        assertEquals(252, used.size());
        Map<String, AbilityEntry> byId = built.abilities().stream().collect(Collectors.toMap(AbilityEntry::id, a -> a));
        long automatic = 0;
        for (String id : used) {
            AbilityEntry a = byId.get(id);
            assertNotNull(a, id);
            boolean noScript = cov.get(id).asText().equals("no script");
            assertEquals(noScript ? EffectClass.AUTOMATIC : EffectClass.SCRIPTED, a.effectClass(), id);
            automatic += a.effectClass() == EffectClass.AUTOMATIC ? 1 : 0;
        }
        assertEquals(9, automatic);
        assertEquals(243, used.size() - automatic);
    }

    @Test
    void everyItemMatchesTheShowdownStatusOfTheCoverageReport() throws IOException {
        JsonNode cov = coverage("items");
        for (ItemEntry i : built.items()) {
            if (i.category().equals("tm")) {
                continue;
            }
            String status = cov.get(i.id()).asText();
            assertEquals(status.replace(' ', '-'), i.showdown(), i.id());
        }
    }

    @Test
    void thereAre383UsableItemRowsAnd391TmsAndNoAprijuiceOrPokedex() {
        assertEquals(383, built.itemRowsUsed());
        assertEquals(7, built.itemRowsDropped());
        assertEquals(List.of("kings_rock", "shell_helmet"), built.mergedItemIds());
        List<ItemEntry> plain = built.items().stream().filter(i -> !i.category().equals("tm")).toList();
        assertEquals(381, plain.size(), "383 rows; King's Rock and Shell Helmet each appear in two categories");
        assertEquals(391, built.items().stream().filter(i -> i.category().equals("tm")).count());
        assertEquals(772, built.items().size());
        assertTrue(built.items().stream().noneMatch(i -> i.category().equals("pokedexes") || i.id().startsWith("pokedex_")));
        for (ItemEntry i : built.items()) {
            String all = (i.id() + i.name() + i.icon()).toLowerCase();
            assertFalse(all.contains("aprijuice"), i.id());
        }
        assertEquals(List.of("evolution"), item("kings_rock").alsoIn());
        assertEquals("held_items", item("kings_rock").category());
        assertEquals(built.items().size(), built.items().stream().map(ItemEntry::id).distinct().count(), "ids are unique");
    }

    @Test
    void theTmItemsCover391MovesFrom334CobblemonAnd57GeneratedOnes() {
        List<ItemEntry> tms = built.items().stream().filter(i -> i.category().equals("tm")).toList();
        assertEquals(334, tms.stream().filter(t -> t.tmSource().orElse("").equals("cobblemon")).count());
        assertEquals(57, tms.stream().filter(t -> t.tmSource().orElse("").equals("generated")).count());
        assertEquals(List.of(), built.tmMissingMoves());
        ItemEntry tm = item("tm_thunderbolt");
        assertEquals("thunderbolt", tm.move().orElseThrow());
        assertEquals(3000, tm.buyPrice().orElseThrow());
        assertEquals(1500, tm.sellPrice().orElseThrow());
    }

    @Test
    void noEntryRefersToAnIdThatDoesNotExist() {
        Set<String> moves = built.moves().stream().map(MoveEntry::id).collect(Collectors.toSet());
        Set<String> abilities = built.abilities().stream().map(AbilityEntry::id).collect(Collectors.toSet());
        // every ability of every species exists, and Showdown knows it
        assertEquals(List.of(), built.missingAbilities());
        for (var s : merged.species()) {
            assertTrue(abilities.containsAll(s.abilities()), s.id());
            s.hiddenAbility().ifPresent(h -> assertTrue(abilities.contains(h), s.id() + " " + h));
        }
        // every TM teaches a move that exists
        for (ItemEntry i : built.items()) {
            i.move().ifPresent(m -> assertTrue(moves.contains(m), i.id()));
            // nature mints name a nature that exists; type gems name a type that exists
            i.handler().ifPresent(h -> {
                if (h.name().equals("nature_mint")) {
                    assertTrue(Showdown.load().natures().containsKey(h.detail().orElseThrow()), i.id());
                }
            });
        }
        // every move a species learns exists
        Set<String> unknown = new TreeSet<>();
        for (var row : raw.learnsets()) {
            if (!moves.contains(row.moveId())) {
                unknown.add(row.moveId());
            }
        }
        assertEquals(Set.of(), unknown, "learnset moves that are not in the moves table");
    }

    @Test
    void defaultEffectsAndPricesFollowChapter88() {
        assertEquals("heal_hp", item("potion").handler().orElseThrow().name());
        assertEquals(20, item("potion").handler().orElseThrow().amount().orElseThrow());
        assertEquals(200, item("potion").buyPrice().orElseThrow());
        assertEquals(100, item("potion").sellPrice().orElseThrow());
        assertEquals(1500, item("hyper_potion").buyPrice().orElseThrow());
        assertEquals(4000, item("max_revive").buyPrice().orElseThrow());
        assertEquals(200, item("poke_ball").buyPrice().orElseThrow());
        assertEquals(800, item("ultra_ball").buyPrice().orElseThrow());
        assertEquals(1000, item("quick_ball").buyPrice().orElseThrow());
        assertEquals(500, item("x_attack").buyPrice().orElseThrow());
        assertEquals(2100, item("moon_stone").buyPrice().orElseThrow());
        assertEquals(100, item("oran_berry").buyPrice().orElseThrow());
        assertEquals(300, item("lum_berry").buyPrice().orElseThrow());
        assertEquals(2000, item("magnet").buyPrice().orElseThrow());
        assertEquals(5000, item("leftovers").buyPrice().orElseThrow());
        assertEquals(2000, item("kings_rock").buyPrice().orElseThrow(), "King's Rock is a held item");
        assertEquals(5000, item("adamant_mint").buyPrice().orElseThrow());
        assertEquals(2000, item("fire_gem").buyPrice().orElseThrow());
        for (String notForSale : List.of("master_ball", "ancient_ultra_ball", "rare_candy", "exp_candy_xl", "health_candy")) {
            assertFalse(item(notForSale).forSale(), notForSale);
            assertTrue(item(notForSale).buyPrice().isEmpty(), notForSale);
        }
    }

    @Test
    void ivCandiesChangeOneIvByTheDecidedStep() {
        assertEquals(1, ItemRules.IV_CANDY_STEP);
        var up = item("mighty_candy").handler().orElseThrow();
        assertEquals("iv_candy", up.name());
        assertEquals(1, up.amount().orElseThrow());
        assertEquals("attack", up.detail().orElseThrow());
        assertEquals(-1, item("sickly_candy").handler().orElseThrow().amount().orElseThrow());
        assertEquals(12, built.items().stream().filter(i -> i.handler().map(h -> h.name().equals("iv_candy")).orElse(false)).count());
    }

    @Test
    void theCoverageReportIsTheSameTextEveryTime() {
        Path resources = Path.of(System.getProperty("pokednd.resources", "../resources"));
        Built again = Catalog.build(raw, merged.species(), Showdown.load(), Catalog.english(resources));
        assertEquals(Catalog.coverageReport(built), Catalog.coverageReport(again));
    }
}
