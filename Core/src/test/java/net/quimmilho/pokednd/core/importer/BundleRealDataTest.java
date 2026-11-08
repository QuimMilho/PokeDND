/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import net.quimmilho.pokednd.core.importer.Catalog.ItemEntry;
import net.quimmilho.pokednd.core.importer.Derived.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whole importer on the real {@code resources/} and {@code build/roster.json}. Skipped when they are missing, unless
 * {@code POKEDND_REQUIRE_RESOURCES=true}.
 */
class BundleRealDataTest {

    private static Path resources;
    private static Path rosterFile;
    private static Bundle bundle;
    private static Raw raw;

    @BeforeAll
    static void load() {
        resources = Path.of(System.getProperty("pokednd.resources", "../resources"));
        rosterFile = Path.of(System.getProperty("pokednd.roster", "../build/roster.json"));
        boolean there = Files.isRegularFile(resources.resolve("pokedex.csv")) && Files.isRegularFile(rosterFile);
        if (!there && "true".equals(System.getenv("POKEDND_REQUIRE_RESOURCES"))) {
            fail("resources/ or build/roster.json is missing but POKEDND_REQUIRE_RESOURCES=true. "
                    + "Run: node tools/extract.mjs && node tools/check-assets.mjs");
        }
        assumeTrue(there, "resources/ or build/roster.json is not built");
        raw = RawReader.read(resources);
        bundle = Bundle.build(resources, rosterFile);
    }

    @Test
    void runningTheImporterTwiceGivesTheSameOutputHashAndTheSameBytes() {
        Bundle again = Bundle.build(resources, rosterFile);
        assertEquals(bundle.outputHash(), again.outputHash());
        Map<String, byte[]> a = bundle.files();
        Map<String, byte[]> b = again.files();
        assertEquals(a.keySet(), b.keySet());
        for (String name : a.keySet()) {
            assertArrayEquals(a.get(name), b.get(name), name);
        }
        assertEquals(64, bundle.outputHash().length());
    }

    @Test
    void theBundleHasNoDanglingIds() {
        assertEquals(List.of(), bundle.validate());
    }

    @Test
    void removingOneMoveMakesTheBuildFail(@TempDir Path out) {
        assertTrue(bundle.learnsets().allMoves().contains("tackle"));
        Bundle broken = bundle.withoutMove("tackle");
        List<String> problems = broken.validate();
        assertFalse(problems.isEmpty());
        assertTrue(problems.contains("a learnset move does not exist: tackle"), problems.toString());
        ImportException e = assertThrows(ImportException.class, () -> broken.write(out));
        assertTrue(e.getMessage().contains("tackle"), e.getMessage());
        assertFalse(Files.exists(out.resolve("MANIFEST.json")), "nothing is written when the bundle is unsound");
    }

    @Test
    void removingOneAbilityMakesTheBuildFail() {
        List<String> problems = bundle.withoutAbility("overgrow").validate();
        assertTrue(problems.stream().anyMatch(p -> p.contains("bulbasaur") && p.contains("overgrow")), problems.toString());
    }

    @Test
    void theRosterEqualsTheOutputOfCheckAssets() throws IOException {
        JsonNode expected = new ObjectMapper().readTree(Files.readString(rosterFile));
        assertEquals(903, bundle.roster().species().size(), "905 modelled species minus Bounsweet and Pyukumuku");
        assertEquals(expected.get("speciesCount").asInt(), bundle.roster().species().size());
        int forms = bundle.roster().species().values().stream().mapToInt(e -> e.forms().size()).sum();
        assertEquals(expected.get("formCount").asInt(), forms);

        Set<String> want = new TreeSet<>();
        for (JsonNode s : expected.get("species")) {
            for (JsonNode f : s.get("forms")) {
                want.add(s.get("species").asText() + "/" + f.get("id").asText());
            }
        }
        Set<String> got = new TreeSet<>();
        bundle.roster().species().values().forEach(s -> s.forms().forEach(f -> got.add(s.id() + "/" + f.id())));
        assertEquals(want, got);
        // And the file that is written says the same.
        JsonNode written = new ObjectMapper().readTree(bundle.files().get("roster.json"));
        assertEquals(903, written.get("species").size());
        long playable = bundle.merged().species().stream().filter(s -> s.playable()).count();
        assertEquals(903, playable);
    }

    @Test
    void theItemFilterRemovedExactlyThePokedexRows() {
        Set<String> kept = bundle.catalog().items().stream().filter(i -> !i.category().equals("tm")).map(ItemEntry::id)
                .collect(Collectors.toSet());
        Set<String> dropped = new TreeSet<>();
        for (Raw.Item i : raw.items()) {
            if (!kept.contains(i.id())) {
                dropped.add(i.id());
                assertEquals("pokedexes", i.category(), i.id() + " was dropped but is not a Pokédex row");
            }
        }
        assertEquals(7, dropped.size());
        assertTrue(dropped.stream().allMatch(id -> id.startsWith("pokedex_")));
        assertEquals(raw.items().size() - 7 - 2, kept.size(), "390 rows, 7 Pokédex rows, 2 ids that appear twice");
    }

    @Test
    void everyLearnsetRowIsInTheBundleExceptTheUnsupportedGigantamaxForms() {
        Learnsets l = bundle.learnsets();
        assertEquals(130_704, l.rows());
        assertEquals(114 + 105, l.skipped());
        long entries = l.bySpecies().values().stream().flatMap(m -> m.values().stream())
                .mapToLong(x -> x.level().size() + x.egg().size() + x.tm().size() + x.tutor().size() + x.legacy().size()
                        + x.special().size() + x.formChange().size()).sum();
        assertEquals(l.rows() - l.skipped(), entries, "no row is lost or invented (the CSV has no repeated rows)");
        var pikachu = l.bySpecies().get("pikachu").get("pikachu_base");
        assertTrue(pikachu.tm().contains("thunderbolt"));
        assertEquals(1, pikachu.level().get(0).level());
        assertEquals("thundershock", pikachu.level().get(0).move());
        assertTrue(l.bySpecies().get("raichu").containsKey("raichu_alolan"));
        assertEquals(1025, l.bySpecies().size());
    }

    @Test
    void stageBadgesAndWeaknessesAreDerived() {
        var d = bundle.derived().species();
        assertEquals(Stage.BASIC, d.get("pichu").stage());
        assertEquals(Stage.STAGE_1, d.get("pikachu").stage());
        assertEquals(Stage.STAGE_2, d.get("raichu").stage());
        assertEquals(Stage.BASIC, d.get("ditto").stage());
        assertEquals(Stage.STAGE_1, d.get("vaporeon").stage());
        assertEquals(List.of("pichu"), d.get("pikachu").evolvesFrom());
        assertEquals(8, d.get("eevee").evolvesInto().size());
        assertEquals(Map.of("fire", 2.0, "ice", 2.0, "flying", 2.0, "psychic", 2.0), d.get("bulbasaur").weaknesses());
        assertEquals(0.25, d.get("bulbasaur").resistances().get("grass"));
        assertEquals(List.of("normal", "fighting"), d.get("gengar").immunities());
        assertEquals(4.0, d.get("scizor").weaknesses().get("fire"));
    }

    @Test
    void theManifestListsEveryInputAndOutputWithItsHash() throws IOException {
        JsonNode m = new ObjectMapper().readTree(bundle.files().get("MANIFEST.json"));
        assertEquals(Bundle.FORMAT, m.get("bundleFormat").asInt());
        assertEquals("1.8.4+1.21.1", m.get("cobblemon").asText());
        assertEquals(bundle.outputHash(), m.get("outputHash").asText());
        for (String file : List.of("species.json", "evolutions.json", "learnsets.json", "moves.json", "abilities.json", "items.json",
                "natures.json", "typechart.json", "roster.json", "derived.json", "reports/conflicts.md", "reports/forms.md",
                "reports/evolutions.md", "reports/coverage.md")) {
            assertTrue(m.get("outputs").has(file), file);
            assertTrue(bundle.files().containsKey(file), file);
        }
        for (String input : List.of("resources/pokedex.csv", "resources/attack.csv", "resources/item.csv", "resources/tm.csv",
                "resources/pokemon-attacks.csv", "resources/data/cobblemon/species/**", "roster.json", "showdown/moves.json")) {
            assertTrue(m.get("inputs").has(input), input);
        }
    }

    @Test
    void theBundleIsWrittenToAFolder(@TempDir Path out) throws IOException {
        bundle.write(out);
        assertTrue(Files.isRegularFile(out.resolve("species.json")));
        assertTrue(Files.isRegularFile(out.resolve("reports/conflicts.md")));
        assertEquals(new ObjectMapper().readTree(bundle.files().get("MANIFEST.json")),
                new ObjectMapper().readTree(Files.readString(out.resolve("MANIFEST.json"))));
        JsonNode species = new ObjectMapper().readTree(Files.readString(out.resolve("species.json"))).get("species");
        assertEquals(1025, species.size());
        JsonNode tc = new ObjectMapper().readTree(Files.readString(out.resolve("typechart.json")));
        assertEquals(2, tc.get("chart").get("fire").get("grass").asInt());
        assertEquals(25, new ObjectMapper().readTree(Files.readString(out.resolve("natures.json"))).get("natures").size());
    }
}
