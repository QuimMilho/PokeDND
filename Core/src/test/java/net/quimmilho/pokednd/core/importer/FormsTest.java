/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import net.quimmilho.pokednd.core.importer.Forms.FormRef;
import net.quimmilho.pokednd.core.importer.Forms.Kind;
import org.junit.jupiter.api.Test;

class FormsTest {

    private static final Roster EMPTY = new Roster("test", new TreeMap<>());

    @Test
    void thereAreSixtyThreeReviewedValuesWithDistinctSlugs() {
        assertEquals(63, Forms.known().size());
        assertEquals(63, Forms.known().stream().map(Forms.Known::text).distinct().count());
        assertEquals(63, Forms.known().stream().map(Forms.Known::slug).distinct().count(), "slugs must not collide");
    }

    @Test
    void regionalGenderAndTypeValuesHaveTheRightKind() {
        assertEquals(Kind.BASE, Forms.lookup("").orElseThrow().kind());
        assertEquals("alolan", Forms.lookup("Alola").orElseThrow().slug());
        assertEquals(Kind.REGIONAL, Forms.lookup("Hisui").orElseThrow().kind());
        assertEquals(Kind.GENDER, Forms.lookup("F").orElseThrow().kind());
        assertEquals(Kind.TYPE, Forms.lookup("Fairy").orElseThrow().kind());
        assertEquals(Kind.UNSUPPORTED, Forms.lookup("Low-Key-Gmax").orElseThrow().kind());
    }

    @Test
    void anUnknownValueIsNeverGuessed() {
        assertEquals(Optional.empty(), Forms.map("pikachu", 25, "Cosplay", EMPTY));
        assertEquals(List.of("Cosplay", "Low-Key-Gmax"), Forms.unmapped(List.of("", "Alola", "Cosplay", "Low-Key-Gmax", "Cosplay")));
    }

    @Test
    void aSpeciesOutsideTheRosterGetsASyntheticNonPlayableId() {
        FormRef f = Forms.map("arceus", 493, "Fire", EMPTY).orElseThrow();
        assertEquals("arceus_fire", f.id());
        assertFalse(f.playable());
        assertEquals("arceus_base", Forms.map("arceus", 493, "", EMPTY).orElseThrow().id());
    }

    @Test
    void aFormWithAResolverFileUsesTheRostersIdAndIsPlayable() {
        TreeMap<Integer, Roster.Entry> map = new TreeMap<>();
        map.put(745, new Roster.Entry(745, "Lycanroc", "lycanroc", List.of(
                new Roster.Form("lycanroc_base", List.of()),
                new Roster.Form("lycanroc_midnight", List.of("midnight-form")),
                new Roster.Form("lycanroc_dusk", List.of("dusk-form")))));
        map.put(80, new Roster.Entry(80, "Sneasel", "sneasel", List.of(
                new Roster.Form("sneasel_base", List.of()),
                new Roster.Form("sneasel_hisuian-female", List.of("female", "hisuian")))));
        Roster roster = new Roster("test", map);

        assertEquals("lycanroc_midnight", Forms.map("lycanroc", 745, "Midnight", roster).orElseThrow().id());
        assertTrue(Forms.map("lycanroc", 745, "Dusk", roster).orElseThrow().playable());
        assertEquals("lycanroc_base", Forms.map("lycanroc", 745, "", roster).orElseThrow().id());
        assertFalse(Forms.map("lycanroc", 745, "Alola", roster).orElseThrow().playable());
        // A female form must not be confused with the Hisuian female form.
        assertFalse(Forms.map("sneasel", 80, "F", roster).orElseThrow().playable());
    }
}
