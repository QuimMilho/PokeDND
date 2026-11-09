/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Card keys (chapter 9.3) and the opaque IDs in the QR codes (chapter 9.5). */
public final class CardIds {

    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"; // Crockford base 32: no I, L, O or U

    private CardIds() {
    }

    /** {@code 0006_charizard} for a base form, {@code 0105_marowak_alolan} for any other form. */
    public static String key(int dex, String speciesId, String formId, boolean baseForm) {
        return String.format(Locale.ROOT, "%04d_%s", dex, baseForm ? speciesId : formId);
    }

    /**
     * The ID in the QR code of a species card: {@code PKC1-} and 16 characters. Every copy of the same design has the same code, and
     * the same key always gives the same code. It is opaque: it is not a link and says nothing about the card. <b>Suggested</b>.
     */
    public static String qrId(String key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(("pokednd.species-card.v1:" + key).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("PKC1-");
            long bits = 0;
            int have = 0;
            int produced = 0;
            for (int i = 0; produced < 16; i++) {
                bits = (bits << 8) | (hash[i] & 0xFF);
                have += 8;
                while (have >= 5 && produced < 16) {
                    sb.append(ALPHABET.charAt((int) ((bits >> (have - 5)) & 31)));
                    have -= 5;
                    produced++;
                }
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
