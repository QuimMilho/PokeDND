/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * The fonts of the cards: Noto Sans (regular, bold, italic), SIL Open Font License 1.1, bundled in {@code resources/fonts} together
 * with the licence text. It has every accented letter that English and Portuguese need. Each document embeds only the glyphs it uses.
 */
final class CardFonts {

    final PDType0Font regular;
    final PDType0Font bold;
    final PDType0Font italic;

    private CardFonts(PDType0Font regular, PDType0Font bold, PDType0Font italic) {
        this.regular = regular;
        this.bold = bold;
        this.italic = italic;
    }

    static CardFonts load(PDDocument doc) throws IOException {
        return new CardFonts(load(doc, "NotoSans-Regular.ttf"), load(doc, "NotoSans-Bold.ttf"), load(doc, "NotoSans-Italic.ttf"));
    }

    private static PDType0Font load(PDDocument doc, String file) throws IOException {
        try (InputStream in = CardFonts.class.getResourceAsStream("/fonts/" + file)) {
            if (in == null) {
                throw new IOException("The font " + file + " is missing from the API jar");
            }
            return PDType0Font.load(doc, in, true);
        }
    }

    /** Replaces the characters that the font cannot draw by a question mark (a PDF font fails on them). */
    static String safe(PDFont font, String text) {
        StringBuilder sb = new StringBuilder();
        text.codePoints().forEach(cp -> {
            String ch = new String(Character.toChars(cp));
            if (cp == '\n' || cp == '\t') {
                sb.append(' ');
                return;
            }
            try {
                font.encode(ch);
                sb.append(ch);
            } catch (IOException | IllegalArgumentException e) {
                sb.append('?');
            }
        });
        return sb.toString();
    }
}
