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
import java.util.Optional;
import java.util.TreeMap;
import net.quimmilho.pokednd.core.importer.Forms.FormRef;
import net.quimmilho.pokednd.core.importer.SpeciesMerger.Conflict;
import net.quimmilho.pokednd.core.importer.SpeciesMerger.Merged;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The merge on the real {@code resources/} and {@code build/roster.json} (make them with {@code node tools/extract.mjs} and
 * {@code node tools/check-assets.mjs}). Skipped when they are missing, unless {@code POKEDND_REQUIRE_RESOURCES=true}.
 */
class SpeciesMergerRealDataTest {

    private static Raw raw;
    private static Roster roster;
    private static Merged merged;

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
        roster = Roster.read(rosterFile);
        merged = SpeciesMerger.merge(raw, roster);
    }

    @Test
    void theSixtyThreeFormValuesAreAllKnownOrOnTheReviewedUnmappedList() {
        var texts = SpeciesMerger.formTexts(raw);
        assertEquals(63, texts.size(), "63 distinct form values (blank included)");
        // Reviewed: the only values that are not mapped are the two Gigantamax forms, which have no assets (19.4).
        assertEquals(List.of("Low-Key-Gmax", "Rapid-Strike-Gmax"), Forms.unmapped(texts));
        assertEquals(new TreeMap<>(java.util.Map.of("Low-Key-Gmax", 114, "Rapid-Strike-Gmax", 105)), merged.unmapped());
        for (String t : texts) {
            assertTrue(Forms.lookup(t).isPresent(), "form value \"" + t + "\" is not in the reviewed list");
        }
        assertEquals(63, Forms.known().size());
    }

    @Test
    void theMergedListHas1025SpeciesAndTheRosterSubsetIs903() {
        assertEquals(1025, merged.species().size());
        assertEquals(903, merged.species().stream().filter(SpeciesMerger.MergedSpecies::playable).count(),
                "the roster has 903 species: Bounsweet and Pyukumuku wait for their animations (Appendix E #32)");
        assertEquals(roster.species().size(), merged.species().stream().filter(SpeciesMerger.MergedSpecies::playable).count());
        assertTrue(merged.species().stream().filter(s -> s.playable()).allMatch(s -> s.implemented()));
        assertEquals(905, merged.species().stream().filter(SpeciesMerger.MergedSpecies::implemented).count(),
                "Cobblemon marks 905 species as implemented");
    }

    @Test
    void theOnlyDisagreementsAreTheThreeSpellingsOfNames() {
        assertEquals(List.of(
                new Conflict(29, "nidoranf", "name", "Nidoran♀", "Nidoran-F", "json"),
                new Conflict(32, "nidoranm", "name", "Nidoran♂", "Nidoran-M", "json"),
                new Conflict(669, "flabebe", "name", "Flabébé", "Flabebe", "json")), merged.conflicts());
    }

    @Test
    void theConflictsReportIsDeterministic() {
        Merged again = SpeciesMerger.merge(RawReader.read(Path.of(System.getProperty("pokednd.resources", "../resources"))), roster);
        assertEquals(Reports.conflicts(merged.conflicts()), Reports.conflicts(again.conflicts()));
        assertEquals(Reports.forms(merged.formUses(), merged.unmapped()), Reports.forms(again.formUses(), again.unmapped()));
    }

    private static Optional<FormRef> form(String speciesId, String id) {
        return merged.species().stream().filter(s -> s.id().equals(speciesId)).findFirst().orElseThrow()
                .forms().stream().filter(f -> f.id().equals(id)).findFirst();
    }

    @Test
    void formsAreMappedAndOnlyRosterFormsArePlayable() {
        assertTrue(form("raichu", "raichu_alolan").orElseThrow().playable());
        assertTrue(form("meowth", "meowth_galarian").orElseThrow().playable());
        assertTrue(form("tauros", "tauros_paldean_blaze").orElseThrow().playable());
        assertTrue(form("lycanroc", "lycanroc_midnight").orElseThrow().playable());
        assertTrue(form("toxtricity", "toxtricity_lowkey").orElseThrow().playable());
        // Arceus has no model at all: its type forms exist as data but nobody can play them.
        assertEquals(false, form("arceus", "arceus_fire").orElseThrow().playable());
        assertEquals(false, form("giratina", "giratina_origin").orElseThrow().playable());
        assertTrue(form("pikachu", "pikachu_base").isPresent());
    }

    @Test
    void noMegaGigantamaxPrimalOrTerastalFormExistsInTheMergedList() {
        for (var s : merged.species()) {
            for (FormRef f : s.forms()) {
                String id = f.slug().toLowerCase(); // the form part only: "meganium" is a species, not a Mega form
                assertTrue(!id.contains("mega") && !id.contains("gmax") && !id.contains("primal") && !id.contains("tera"),
                        "unexpected form " + f.id());
            }
        }
    }
}
