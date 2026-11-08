/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.quimmilho.pokednd.core.importer.ImportException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SignatureOverridesTest {

    @Test
    void theExampleOfChapter9_3IsReadAndTheMoveNamesAreNormalised() {
        SignatureOverrides o = SignatureOverrides.parse(
                "{ \"0006_charizard\": [\"flamethrower\", \"air-slash\", \"Dragon Claw\", \"roost\"] }", "test");
        assertEquals(List.of("flamethrower", "airslash", "dragonclaw", "roost"), o.get("0006_charizard").orElseThrow());
        assertTrue(o.get("0001_bulbasaur").isEmpty());
    }

    @Test
    void keysFollowTheDexNumberAndTheSpeciesOrFormId() {
        assertEquals("0006_charizard", SignatureOverrides.cardKey(6, "charizard", "charizard_base", true));
        assertEquals("0105_marowak_alolan", SignatureOverrides.cardKey(105, "marowak", "marowak_alolan", false));
        assertEquals("1025_pecharunt", SignatureOverrides.cardKey(1025, "pecharunt", "pecharunt_base", true));
    }

    @Test
    void keysThatStartWithAnUnderscoreAreComments() {
        SignatureOverrides o = SignatureOverrides.parse("{ \"_note\": \"hello\", \"0001_bulbasaur\": [\"tackle\"] }", "test");
        assertEquals(1, o.moves().size());
    }

    @Test
    void badFilesAreRefusedWithAPlainSentence() {
        assertThrows(ImportException.class, () -> SignatureOverrides.parse("not json", "t"));
        assertThrows(ImportException.class, () -> SignatureOverrides.parse("[]", "t"));
        assertThrows(ImportException.class, () -> SignatureOverrides.parse("{\"charizard\": [\"a\"]}", "t"), "no Dex number");
        assertThrows(ImportException.class, () -> SignatureOverrides.parse("{\"0006_charizard\": \"flamethrower\"}", "t"));
        assertThrows(ImportException.class, () -> SignatureOverrides.parse("{\"0006_charizard\": []}", "t"));
        assertThrows(ImportException.class, () -> SignatureOverrides.parse("{\"0006_charizard\": [\"a\",\"b\",\"c\",\"d\",\"e\"]}", "t"));
        assertThrows(ImportException.class, () -> SignatureOverrides.parse("{\"0006_charizard\": [\"air-slash\",\"airslash\"]}", "t"));
        assertThrows(ImportException.class, () -> SignatureOverrides.parse("{\"0006_charizard\": [3]}", "t"));
    }

    @Test
    void aFileIsReadFromDiskAndAMissingFileIsAnError(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("o.json");
        Files.writeString(file, "{ \"0025_pikachu\": [\"thunderbolt\"] }");
        assertEquals(List.of("thunderbolt"), SignatureOverrides.read(file).get("0025_pikachu").orElseThrow());
        assertThrows(ImportException.class, () -> SignatureOverrides.read(dir.resolve("nope.json")));
        assertTrue(SignatureOverrides.none().moves().isEmpty());
    }

    @Test
    void withAddsAnOverrideWithoutChangingTheOriginal() {
        SignatureOverrides none = SignatureOverrides.none();
        SignatureOverrides one = none.with("0025_pikachu", List.of("Thunder Bolt"));
        assertTrue(none.moves().isEmpty());
        assertEquals(List.of("thunderbolt"), one.get("0025_pikachu").orElseThrow());
    }
}
