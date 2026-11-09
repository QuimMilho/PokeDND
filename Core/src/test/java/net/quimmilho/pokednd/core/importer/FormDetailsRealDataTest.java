/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.quimmilho.pokednd.core.importer.FormDetails.Detail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The per-form types, abilities and weaknesses on the real data (skipped when {@code resources/} is not built). */
class FormDetailsRealDataTest {

    private static Bundle bundle;

    @BeforeAll
    static void load() {
        Path resources = Path.of(System.getProperty("pokednd.resources", "../resources"));
        Path roster = Path.of(System.getProperty("pokednd.roster", "../build/roster.json"));
        assumeTrue(Files.isRegularFile(resources.resolve("pokedex.csv")) && Files.isRegularFile(roster), "resources/ is not built");
        bundle = Bundle.build(resources, roster);
    }

    @Test
    void aBaseFormHasTheSpeciesTypesAndAbilities() {
        Detail d = bundle.forms().get("bulbasaur", "bulbasaur_base").orElseThrow();
        assertEquals(List.of("grass", "poison"), d.types());
        assertEquals(List.of("overgrow"), d.abilities());
        assertEquals("chlorophyll", d.hiddenAbility());
        assertEquals(2.0, d.weaknesses().get("fire"));
        assertEquals(2.0, d.weaknesses().get("psychic"));
    }

    @Test
    void aRegionalFormHasItsOwnTypesAbilitiesAndWeaknesses() {
        Detail base = bundle.forms().get("marowak", "marowak_base").orElseThrow();
        assertEquals(List.of("ground"), base.types());
        Detail alolan = bundle.forms().get("marowak", "marowak_alolan").orElseThrow();
        assertEquals(List.of("fire", "ghost"), alolan.types());
        assertEquals(List.of("cursedbody", "lightningrod"), alolan.abilities());
        assertEquals("rockhead", alolan.hiddenAbility());
        assertTrue(alolan.weaknesses().containsKey("water"));
        assertTrue(alolan.immunities().contains("normal"));
        assertNull(base.weaknesses().get("fire"));
    }

    @Test
    void everyRosterFormIsListedAndTheFileIsInTheBundle() {
        int forms = bundle.roster().species().values().stream().mapToInt(e -> e.forms().size()).sum();
        int listed = bundle.forms().bySpecies().values().stream().mapToInt(java.util.Map::size).sum();
        assertEquals(forms, listed);
        String file = new String(bundle.files().get("forms.json"), StandardCharsets.UTF_8);
        assertTrue(file.contains("\"marowak_alolan\""));
    }
}
