/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import java.util.Locale;
import java.util.Map;
import net.quimmilho.pokednd.api.error.ApiException;

/**
 * How a set of cards is laid out and drawn (chapter 9.6).
 *
 * @param size        the size of one card (trim size, without bleed)
 * @param paper       the paper
 * @param bleedMm     bleed: the art runs this far past the cut line (default 3)
 * @param gapMm       the gap between the cut lines of two neighbouring cards (default 2)
 * @param backs       also produce the backs, mirrored for double-sided printing (long-edge flip)
 * @param language    {@code en} or {@code pt}
 * @param inkSaving   frame outline only, no coloured fills
 * @param copies      copies of each card
 * @param showBackId  print the card ID text under the QR code on the back ({@code cards.back.showId})
 * @param cropMarks   draw crop marks outside the grid
 */
public record CardOptions(CardSize size, Paper paper, double bleedMm, double gapMm, boolean backs, String language, boolean inkSaving,
                          int copies, boolean showBackId, boolean cropMarks) {

    /** The paper sizes of chapter 9.6. */
    public enum Paper {
        A4(210.0, 297.0), LETTER(215.9, 279.4);

        public final double widthMm;
        public final double heightMm;

        Paper(double widthMm, double heightMm) {
            this.widthMm = widthMm;
            this.heightMm = heightMm;
        }

        public static Paper parse(String text) {
            try {
                return valueOf(text.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw invalid("The paper must be A4 or LETTER, not \"" + text + "\".");
            }
        }
    }

    /** A card size in millimetres: 63x88 (default), 70x120 (tarot), 44x63 (mini) or your own. */
    public record CardSize(double widthMm, double heightMm) {

        public static final CardSize TRADING = new CardSize(63, 88);

        public CardSize {
            if (widthMm < 30 || widthMm > 150 || heightMm < 40 || heightMm > 220) {
                throw invalid("A card must be 30 to 150 mm wide and 40 to 220 mm high.");
            }
        }

        /** Reads {@code 63x88}. */
        public static CardSize parse(String text) {
            try {
                String[] p = text.strip().toLowerCase(Locale.ROOT).replace(',', '.').split("x");
                return new CardSize(Double.parseDouble(p[0]), Double.parseDouble(p[1]));
            } catch (RuntimeException e) {
                if (e instanceof ApiException a) {
                    throw a;
                }
                throw invalid("The card size must look like 63x88 (millimetres), not \"" + text + "\".");
            }
        }

        @Override
        public String toString() {
            return trim(widthMm) + "x" + trim(heightMm);
        }

        private static String trim(double v) {
            return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
        }
    }

    public CardOptions {
        if (bleedMm < 0 || bleedMm > 10 || gapMm < 0 || gapMm > 20) {
            throw invalid("The bleed must be 0 to 10 mm and the gap 0 to 20 mm.");
        }
        if (copies < 1 || copies > 20) {
            throw invalid("Copies must be 1 to 20.");
        }
        language = language == null ? "en" : language.strip().toLowerCase(Locale.ROOT);
        if (!language.equals("en") && !language.equals("pt")) {
            throw invalid("The language must be en or pt.");
        }
    }

    /** The defaults of chapter 9.10: 63x88 mm, A4, 3 mm bleed, 2 mm gap, crop marks, one copy, no backs. */
    public static CardOptions defaults() {
        return new CardOptions(CardSize.TRADING, Paper.A4, 3, 2, false, "en", false, 1, false, true);
    }

    public CardOptions withBacks(boolean value) {
        return new CardOptions(size, paper, bleedMm, gapMm, value, language, inkSaving, copies, showBackId, cropMarks);
    }

    public CardOptions withSize(CardSize value) {
        return new CardOptions(value, paper, bleedMm, gapMm, backs, language, inkSaving, copies, showBackId, cropMarks);
    }

    public CardOptions withPaper(Paper value) {
        return new CardOptions(size, value, bleedMm, gapMm, backs, language, inkSaving, copies, showBackId, cropMarks);
    }

    public CardOptions withLanguage(String value) {
        return new CardOptions(size, paper, bleedMm, gapMm, backs, value, inkSaving, copies, showBackId, cropMarks);
    }

    public CardOptions withCopies(int value) {
        return new CardOptions(size, paper, bleedMm, gapMm, backs, language, inkSaving, value, showBackId, cropMarks);
    }

    public CardOptions withInkSaving(boolean value) {
        return new CardOptions(size, paper, bleedMm, gapMm, backs, language, value, copies, showBackId, cropMarks);
    }

    public CardOptions withShowBackId(boolean value) {
        return new CardOptions(size, paper, bleedMm, gapMm, backs, language, inkSaving, copies, value, cropMarks);
    }

    private static ApiException invalid(String message) {
        return new ApiException(422, "INVALID_EXPORT_OPTIONS", message, Map.of());
    }
}
