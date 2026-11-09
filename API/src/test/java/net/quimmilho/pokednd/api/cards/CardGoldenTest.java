/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import net.quimmilho.pokednd.api.cards.CardDataFactory.CardRef;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Golden files: the text and the picture of a few real cards must equal the stored references (src/test/resources/golden/cards).
 * The picture may differ a little, because fonts are anti-aliased slightly differently on each system.
 *
 * <p>After a change to the card design on purpose, make new references with {@code POKEDND_UPDATE_GOLDEN=true ./gradlew test --tests
 * '*CardGoldenTest'} and look at the pictures before keeping them.
 */
class CardGoldenTest {

    private static final Path GOLDEN = Path.of("src/test/resources/golden/cards");
    private static final float DPI = 110;
    /** The average colour difference per pixel (0 to 255) that still counts as the same picture. */
    private static final double MAX_MEAN_DIFF = 1.5;
    /** The share of pixels that may differ by more than 64 (the edges of letters). */
    private static final double MAX_BAD_SHARE = 0.01;

    static List<Arguments> cases() {
        return List.of(
                Arguments.of("bulbasaur", "bulbasaur", "bulbasaur_base", "en"),
                Arguments.of("charizard-pt", "charizard", "charizard_base", "pt"),
                Arguments.of("marowak-alolan", "marowak", "marowak_alolan", "en"),
                Arguments.of("caterpie", "caterpie", "caterpie_base", "en"),
                Arguments.of("pikachu", "pikachu", "pikachu_base", "en"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void theCardMatchesItsGoldenTextAndPicture(String name, String species, String form, String language) throws IOException {
        CardData card = RealData.card(species, form, language);
        CardOptions options = CardOptions.defaults().withLanguage(language);
        byte[] pdf = CardPdf.write(List.of(card), options).pdf();

        String text = PdfTools.text(pdf, 1);
        BufferedImage image = PdfTools.render(pdf, 1, DPI);
        Path textFile = GOLDEN.resolve(name + ".txt");
        Path imageFile = GOLDEN.resolve(name + ".png");

        if ("true".equals(System.getenv("POKEDND_UPDATE_GOLDEN"))) {
            Files.createDirectories(GOLDEN);
            Files.writeString(textFile, text, StandardCharsets.UTF_8);
            ImageIO.write(image, "png", imageFile.toFile());
            return;
        }
        if (!Files.isRegularFile(textFile) || !Files.isRegularFile(imageFile)) {
            fail("No golden files for " + name + ". Make them with POKEDND_UPDATE_GOLDEN=true and check the pictures.");
        }
        assertEquals(Files.readString(textFile, StandardCharsets.UTF_8), text, "the text of " + name);
        compare(ImageIO.read(imageFile.toFile()), image, name);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void everyCardShowsItsFourMovesAndItsQrCodeDecodes(String name, String species, String form, String language) throws IOException {
        CardData card = RealData.card(species, form, language);
        byte[] pdf = CardPdf.write(List.of(card), CardOptions.defaults().withLanguage(language).withBacks(true)).pdf();
        String text = PdfTools.text(pdf, 1);
        for (CardData.MoveLine m : card.moves()) {
            assertTrue(text.contains(m.name()), m.name() + " is missing from the card of " + name);
        }
        List<PdfTools.Qr> qrs = PdfTools.qrCodes(PdfTools.render(pdf, 2, 150));
        assertEquals(List.of(card.qrId()), qrs.stream().map(PdfTools.Qr::text).toList());
    }

    private static void compare(BufferedImage expected, BufferedImage actual, String name) {
        assertEquals(expected.getWidth(), actual.getWidth(), name + ": width");
        assertEquals(expected.getHeight(), actual.getHeight(), name + ": height");
        long total = 0;
        long bad = 0;
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                int a = expected.getRGB(x, y);
                int b = actual.getRGB(x, y);
                int d = Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)) + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)) + Math.abs((a & 255) - (b & 255));
                total += d / 3;
                if (d / 3 > 64) {
                    bad++;
                }
            }
        }
        double pixels = (double) expected.getWidth() * expected.getHeight();
        assertTrue(total / pixels <= MAX_MEAN_DIFF, name + ": the picture differs on average by " + total / pixels);
        assertTrue(bad / pixels <= MAX_BAD_SHARE, name + ": " + bad + " pixels differ strongly (" + 100.0 * bad / pixels + " %)");
    }
}
