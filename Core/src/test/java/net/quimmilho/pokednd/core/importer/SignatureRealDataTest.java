/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.quimmilho.pokednd.core.cards.SignatureMoves;
import net.quimmilho.pokednd.core.cards.SignatureOverrides;
import net.quimmilho.pokednd.core.importer.Signature.Card;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The signature moves of chapter 9.3 on the real data. Skipped when {@code resources/} or {@code build/roster.json} are
 * missing, unless {@code POKEDND_REQUIRE_RESOURCES=true}.
 */
class SignatureRealDataTest {

    private static Path resources;
    private static Path rosterFile;
    private static Path overridesFile;
    /** The rule alone, no overrides. */
    private static Bundle rule;
    /** With the repository's own {@code cards/signature-overrides.json}. */
    private static Bundle withFile;

    @BeforeAll
    static void load() {
        resources = Path.of(System.getProperty("pokednd.resources", "../resources"));
        rosterFile = Path.of(System.getProperty("pokednd.roster", "../build/roster.json"));
        overridesFile = Path.of(System.getProperty("pokednd.overrides", "../cards/signature-overrides.json"));
        boolean there = Files.isRegularFile(resources.resolve("pokedex.csv")) && Files.isRegularFile(rosterFile);
        if (!there && "true".equals(System.getenv("POKEDND_REQUIRE_RESOURCES"))) {
            fail("resources/ or build/roster.json is missing but POKEDND_REQUIRE_RESOURCES=true. "
                    + "Run: node tools/extract.mjs && node tools/check-assets.mjs");
        }
        assumeTrue(there, "resources/ or build/roster.json is not built");
        assertTrue(Files.isRegularFile(overridesFile), "cards/signature-overrides.json is in the repository");
        rule = Bundle.build(resources, rosterFile);
        withFile = Bundle.build(resources, rosterFile, overridesFile);
    }

    private static List<String> moves(Bundle bundle, String species, String form) {
        return bundle.derived().species().get(species).signature().get(form);
    }

    // ------------------------------------------------------------------------------ documented examples

    @Test
    void pikachuGetsItsElectricAttacksAndTheTailMoves() {
        // Pikachu is a Stage 1 (it evolves from Pichu), so moves up to level 40 count.
        assertEquals(List.of("thunderbolt", "irontail", "discharge", "slam"), moves(rule, "pikachu", "pikachu_base"));
    }

    @Test
    void charizardGetsFireAndFlyingAttacksFromTheRule() {
        assertEquals(List.of("heatwave", "airslash", "flareblitz", "wingattack"), moves(rule, "charizard", "charizard_base"));
    }

    @Test
    void magikarpHasOnlyTwoUsableLevelUpMovesBecauseSplashIsLeftOut() {
        assertEquals(List.of("flail", "tackle"), moves(rule, "magikarp", "magikarp_base"));
    }

    @Test
    void eeveeGetsCovetBiteQuickAttackAndBabyDollEyes() {
        // Eevee is Basic and evolves, so only moves up to level 25 count.
        assertEquals(List.of("covet", "bite", "quickattack", "babydolleyes"), moves(rule, "eevee", "eevee_base"));
    }

    @Test
    void aRegionalFormUsesItsOwnTypesAndMoves() {
        // Alolan Marowak is Fire/Ghost: both its types count as same-type attacks.
        assertEquals(List.of("shadowbone", "flamewheel", "hex", "doubleedge"), moves(rule, "marowak", "marowak_alolan"));
        assertEquals(List.of("stompingtantrum", "retaliate", "boneclub", "headbutt"), moves(rule, "marowak", "marowak_base"));
    }

    // ------------------------------------------------------------------------------ every roster card

    @Test
    void everyRosterCardHasFourDistinctMovesOrFewerOnlyIfItTrulyHasFewer() {
        Set<String> usable = new HashSet<>();
        rule.catalog().moves().forEach(m -> {
            if (!m.reason().contains("z_max") && !SignatureMoves.USELESS.contains(m.id())) {
                usable.add(m.id());
            }
        });
        int cards = 0;
        int fewer = 0;
        for (Card card : rule.signature().all()) {
            cards++;
            List<String> picked = card.moves();
            assertEquals(picked.size(), new HashSet<>(picked).size(), card.key() + " has repeated moves");
            assertTrue(picked.size() <= 4, card.key());
            // Everything the form could learn by level (its own, or the base form's if it has none).
            int available = card.rule().candidates();
            if (picked.size() < 4) {
                fewer++;
                assertEquals(available, picked.size(), card.key() + " has fewer than four moves but more were available");
            } else {
                assertTrue(available >= 4, card.key());
            }
            for (String m : picked) {
                assertTrue(usable.contains(m), card.key() + ": " + m + " is not a usable move");
            }
        }
        assertEquals(rule.roster().species().values().stream().mapToInt(e -> e.forms().size()).sum(), cards,
                "one card for every species and form of the roster");
        assertTrue(fewer < cards / 20, "only a few cards have fewer than four moves: " + fewer + " of " + cards);
    }

    @Test
    void theMovesAreWrittenIntoDerivedJsonForEveryRosterForm() throws IOException {
        JsonNode derived = new ObjectMapper().readTree(rule.files().get("derived.json"));
        JsonNode pikachu = derived.path("species").path("pikachu").path("signature").path("pikachu_base");
        assertEquals(4, pikachu.size());
        assertEquals("thunderbolt", pikachu.get(0).asText());
        int forms = 0;
        for (var e : rule.roster().species().values()) {
            for (var f : e.forms()) {
                assertTrue(derived.path("species").path(e.id()).path("signature").has(f.id()), f.id());
                forms++;
            }
        }
        assertEquals(rule.signature().all().size(), forms);
        // Species that are not playable have no cards.
        boolean found = false;
        for (var it = derived.path("species").fields(); it.hasNext();) {
            var e = it.next();
            if (e.getValue().path("signature").size() == 0) {
                found = true;
                break;
            }
        }
        assertTrue(found, "species outside the roster have an empty signature");
    }

    // ------------------------------------------------------------------------------ overrides

    @Test
    void theOverridesFileOfTheRepositoryChangesCharizardAndIsReported() {
        assertEquals(List.of("flamethrower", "airslash", "dragonclaw", "roost"), moves(withFile, "charizard", "charizard_base"));
        assertTrue(withFile.signature().cards().get("charizard").get("charizard_base").overridden());
        // The rule's own pick is kept for the report.
        assertEquals(List.of("heatwave", "airslash", "flareblitz", "wingattack"),
                withFile.signature().cards().get("charizard").get("charizard_base").rule().moves());
        // A species without an override is unchanged.
        assertEquals(moves(rule, "pikachu", "pikachu_base"), moves(withFile, "pikachu", "pikachu_base"));

        String report = new String(withFile.files().get("reports/signature.md"), StandardCharsets.UTF_8);
        assertTrue(report.contains("| 0006_charizard | flamethrower, airslash, dragonclaw, roost | heatwave, airslash, flareblitz, wingattack |"),
                report.substring(0, Math.min(report.length(), 1500)));
        String noOverrides = new String(rule.files().get("reports/signature.md"), StandardCharsets.UTF_8);
        assertTrue(noOverrides.contains("| Cards decided by an override | 0 |"));
        assertTrue(report.contains("| Cards decided by an override | 3 |"));
    }

    @Test
    void anOverrideForAnotherFormAndTheBaseKeyBothWork(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("o.json");
        Files.writeString(file, "{ \"0105_marowak_alolan\": [\"Flare Blitz\", \"shadow-ball\"], \"0105_marowak\": [\"bonemerang\"] }");
        Bundle b = Bundle.build(resources, rosterFile, file);
        assertEquals(List.of("flareblitz", "shadowball"), moves(b, "marowak", "marowak_alolan"));
        assertEquals(List.of("bonemerang"), moves(b, "marowak", "marowak_base"));
        assertEquals(List.of(), b.validate());
    }

    @Test
    void anOverrideThatNamesAMissingMoveOrCardIsReportedAndStopsTheWrite(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("o.json");
        Files.writeString(file, "{ \"0006_charizard\": [\"flamethrower\", \"mega-punchy\"], \"0006_charmander_x\": [\"ember\"] }");
        Bundle b = Bundle.build(resources, rosterFile, file);
        List<String> problems = b.validate();
        assertTrue(problems.stream().anyMatch(p -> p.contains("megapunchy") && p.contains("0006_charizard")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("0006_charmander_x") && p.contains("matches no card")), problems.toString());
        org.junit.jupiter.api.Assertions.assertThrows(ImportException.class, () -> b.write(dir.resolve("out")));
    }

    @Test
    void theOverridesFileIsPartOfTheManifestAndTheResultIsRepeatable() {
        String manifest = new String(withFile.files().get("MANIFEST.json"), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("cards/signature-overrides.json"));
        assertFalse(new String(rule.files().get("MANIFEST.json"), StandardCharsets.UTF_8).contains("cards/signature-overrides.json"));
        assertEquals(withFile.outputHash(), Bundle.build(resources, rosterFile, overridesFile).outputHash());
        assertTrue(!withFile.outputHash().equals(rule.outputHash()), "the overrides change the bundle");
        assertEquals(List.of(), withFile.validate());
    }

    @Test
    void theRepositoryOverridesAllMatchACard() {
        SignatureOverrides o = SignatureOverrides.read(overridesFile);
        assertTrue(o.moves().size() >= 3, "a few examples");
        assertEquals(List.of(), withFile.signature().problems());
    }
}
