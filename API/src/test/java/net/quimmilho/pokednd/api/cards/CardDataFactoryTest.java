/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.quimmilho.pokednd.api.cards.CardDataFactory.CardRef;
import net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.SpeciesRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What the factory puts on a card, on the real bundle. */
class CardDataFactoryTest {

    @Test
    void bulbasaurHasEverythingTheCardShows() {
        CardData c = RealData.card("bulbasaur", "bulbasaur_base", "en");
        assertEquals("0001_bulbasaur", c.key());
        assertEquals(1, c.dex());
        assertEquals("Bulbasaur", c.name());
        assertEquals(List.of("grass", "poison"), c.types());
        assertEquals(List.of("Grass", "Poison"), c.typeNames());
        assertEquals("Basic", c.stage());
        assertEquals(45, c.hp());
        assertEquals(49, c.stats().attack());
        assertEquals(65, c.stats().specialAttack());
        assertEquals(4, c.moves().size());
        assertEquals(List.of("Overgrow"), c.abilities());
        assertEquals("Chlorophyll", c.hiddenAbility());
        assertTrue(c.weaknesses().stream().anyMatch(m -> m.typeId().equals("fire") && m.factor() == 2.0));
        assertTrue(c.resistances().stream().anyMatch(m -> m.typeId().equals("water") && m.factor() == 0.5));
        assertTrue(c.flavour().startsWith("Bulbasaur can be seen napping"), c.flavour());
        assertNotNull(c.artFile());
        assertFalse(c.artIsIcon());
        assertTrue(c.qrId().matches("PKC1-[0-9A-Z]{16}"), c.qrId());
    }

    @Test
    void movesCarryDiceAndHitBandsFromCore() {
        CardData c = RealData.card("bulbasaur", "bulbasaur_base", "en");
        for (CardData.MoveLine m : c.moves()) {
            assertFalse(m.name().isBlank());
            assertFalse(m.hit().isBlank());
            assertTrue(m.pp() > 0, m.name());
        }
        // a damaging move shows dice like 2d8; the hit line names the die and the bands
        CardData.MoveLine damaging = c.moves().stream().filter(m -> !m.category().equals("status")).findFirst().orElseThrow();
        assertTrue(damaging.dice().matches("\\d+d\\d+"), damaging.dice());
        assertTrue(damaging.hit().startsWith("d20"), damaging.hit());
        assertTrue(damaging.hit().contains("hit "), damaging.hit());
    }

    @Test
    void anAlternateFormHasItsOwnTypesAbilitiesAndWeaknesses() {
        CardData base = RealData.card("marowak", "marowak_base", "en");
        CardData alolan = RealData.card("marowak", "marowak_alolan", "en");
        assertEquals("0105_marowak", base.key());
        assertEquals("0105_marowak_alolan", alolan.key());
        assertEquals(List.of("ground"), base.types());
        assertEquals(List.of("fire", "ghost"), alolan.types());
        assertEquals("Marowak (Alola)", alolan.name());
        assertNotEquals(base.qrId(), alolan.qrId());
        assertTrue(alolan.weaknesses().stream().anyMatch(m -> m.typeId().equals("water")));
        assertTrue(alolan.immunities().contains("Normal"), alolan.immunities().toString());
        assertEquals("Stage 1", alolan.stage());
    }

    @Test
    void portugueseUsesPtPtThenPtBrThenEnglish() {
        CardData en = RealData.card("bulbasaur", "bulbasaur_base", "en");
        CardData pt = RealData.card("bulbasaur", "bulbasaur_base", "pt");
        // pt_pt has no Pokédex texts, so the Brazilian text is used
        assertTrue(pt.flavour().startsWith("O Bulbasaur pode ser visto"), pt.flavour());
        assertNotEquals(en.flavour(), pt.flavour());
        assertEquals("Básico", pt.stage());
        assertEquals(List.of("Planta", "Veneno"), pt.typeNames());
        // a move that pt_pt translates is taken from pt_pt (Tackle = Investida)
        CardData pikachu = RealData.card("pikachu", "pikachu_base", "pt");
        assertFalse(pikachu.moves().isEmpty());
        assertTrue(pt.moves().stream().noneMatch(m -> m.name().startsWith("cobblemon.")));
    }

    @Test
    void cardsFollowThePlayableFormsExceptCosmeticFemaleVariants() {
        SpeciesRecord bulbasaur = RealData.repository().species("bulbasaur").orElseThrow();
        // bulbasaur_female only changes the picture
        assertEquals(List.of(new CardRef("bulbasaur", "bulbasaur_base")), CardDataFactory.cardsOf(bulbasaur, true));
        SpeciesRecord marowak = RealData.repository().species("marowak").orElseThrow();
        assertEquals(2, CardDataFactory.cardsOf(marowak, true).size());
        assertEquals(1, CardDataFactory.cardsOf(marowak, false).size());
        // a species with no assets has no card
        SpeciesRecord noAssets = RealData.repository().findSpecies(
                new net.quimmilho.pokednd.api.domain.reference.ReferenceRepository.SpeciesFilter(null, null, false, null, false), null, 1).items().get(0);
        assertTrue(CardDataFactory.cardsOf(noAssets, true).isEmpty());
    }

    @Test
    void missingCardArtFallsBackToTheIconWithAWarning(@TempDir Path tmp) throws IOException {
        var repository = RealData.repository(); // skips the test when the bundle and resources are not built
        // the icons are not rendered in CI (extract --no-icons), so this test needs a machine that has them
        Path icon = RealData.RESOURCES.resolve("icons/0001_bulbasaur/bulbasaur.png");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(icon), "the icons are not rendered (node tools/extract.mjs)");
        // a resources folder that has only the 128 px icon of Bulbasaur
        Path icons = Files.createDirectories(tmp.resolve("icons/0001_bulbasaur"));
        Files.copy(icon, icons.resolve("bulbasaur.png"));
        Path lang = Files.createDirectories(tmp.resolve("assets/cobblemon/lang"));
        Files.copy(RealData.RESOURCES.resolve("assets/cobblemon/lang/en_us.json"), lang.resolve("en_us.json"));
        var builder = new CardDataFactory.Builder(repository, new GameText(tmp, new ObjectMapper()), tmp,
                net.quimmilho.pokednd.core.combat.CombatSettings.defaults());

        CardData c = builder.build(List.of(new CardRef("bulbasaur", "bulbasaur_base")), "en").get(0);
        assertTrue(c.artIsIcon());
        assertEquals(icons.resolve("bulbasaur.png"), c.artFile());
        assertTrue(c.warnings().stream().anyMatch(w -> w.contains("0001_bulbasaur") && w.contains("icon")), c.warnings().toString());

        // and with neither art nor icon
        CardData none = builder.build(List.of(new CardRef("ivysaur", "ivysaur_base")), "en").get(0);
        assertEquals(null, none.artFile());
        assertTrue(none.warnings().stream().anyMatch(w -> w.contains("no card art and no icon")), none.warnings().toString());
    }

    @Test
    void aSpeciesWithFewerThanFourMovesIsReportedNotHidden() {
        CardData caterpie = RealData.card("caterpie", "caterpie_base", "en");
        assertTrue(caterpie.moves().size() < 4);
        assertTrue(caterpie.warnings().stream().anyMatch(w -> w.contains("signature move")), caterpie.warnings().toString());
    }
}
