/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.quimmilho.pokednd.api.InMemoryReferenceRepository;
import net.quimmilho.pokednd.api.cards.CardDataFactory.CardRef;
import net.quimmilho.pokednd.api.reference.BundleReader;

/** The real data bundle (build/bundle) and resources (resources/) for tests; they skip when these are not built. */
final class RealData {

    static final Path BUNDLE = Path.of(System.getProperty("pokednd.bundle", "../build/bundle"));
    static final Path RESOURCES = Path.of(System.getProperty("pokednd.resources", "../resources"));
    private static InMemoryReferenceRepository repository;

    private RealData() {
    }

    /** Skips the test unless the bundle (with forms.json), the card art and the language files are there. */
    static synchronized InMemoryReferenceRepository repository() {
        boolean there = Files.isRegularFile(BUNDLE.resolve("MANIFEST.json")) && Files.isRegularFile(BUNDLE.resolve("forms.json"))
                && Files.isDirectory(RESOURCES.resolve("card-art")) && Files.isRegularFile(RESOURCES.resolve("assets/cobblemon/lang/en_us.json"));
        if (!there && "true".equals(System.getenv("POKEDND_REQUIRE_RESOURCES"))) {
            fail("build/bundle or resources/ is missing but POKEDND_REQUIRE_RESOURCES=true. Run the extractor and the importer: see README.md");
        }
        assumeTrue(there, "build/bundle or resources/ is not built, so the tests on real cards are skipped");
        if (repository == null) {
            repository = new InMemoryReferenceRepository(new BundleReader(new ObjectMapper()).read(BUNDLE));
        }
        return repository;
    }

    static CardDataFactory.Builder builder() {
        return new CardDataFactory.Builder(repository(), new GameText(RESOURCES, new ObjectMapper()), RESOURCES,
                net.quimmilho.pokednd.core.combat.CombatSettings.defaults());
    }

    static CardData card(String species, String form, String language) {
        return builder().build(List.of(new CardRef(species, form)), language).get(0);
    }
}
