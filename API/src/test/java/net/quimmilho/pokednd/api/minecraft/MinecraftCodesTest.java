/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The linking codes themselves: a property test over many random codes. No server needed. */
class MinecraftCodesTest {

    @Test
    void theAlphabetHasNoLookAlikesAndNoRepeats() {
        for (char bad : MinecraftLinkService.LOOK_ALIKES.toCharArray()) {
            assertEquals(-1, MinecraftLinkService.ALPHABET.indexOf(bad), "look-alike " + bad + " is in the alphabet");
        }
        Set<Character> seen = new HashSet<>();
        for (char c : MinecraftLinkService.ALPHABET.toCharArray()) {
            assertTrue(seen.add(c), "repeated character " + c);
        }
        // the look-alike set is the usual one: zero/O/Q, one/I/L, two/Z, five/S, eight/B
        for (char c : "0O1IL".toCharArray()) {
            assertFalse(MinecraftLinkService.ALPHABET.indexOf(c) >= 0);
        }
    }

    @Test
    void everyGeneratedCodeHasEightCharactersAndNoLookAlike() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            String code = MinecraftLinkService.newCode();
            assertEquals(8, code.length(), code);
            for (char c : code.toCharArray()) {
                assertTrue(MinecraftLinkService.ALPHABET.indexOf(c) >= 0, "unexpected character " + c + " in " + code);
                assertEquals(-1, MinecraftLinkService.LOOK_ALIKES.indexOf(c), "look-alike " + c + " in " + code);
            }
            codes.add(code);
        }
        // random enough: no repeats in 20,000 draws out of about 1.1e11 possible codes
        assertEquals(20_000, codes.size());
    }

    @Test
    void everyCharacterOfTheAlphabetTurnsUp() {
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < 2_000; i++) {
            for (char c : MinecraftLinkService.newCode().toCharArray()) {
                seen.add(c);
            }
        }
        assertEquals(MinecraftLinkService.ALPHABET.length(), seen.size());
    }

    @Test
    void typedCodesAreComparedWithoutCapitalsSpacesOrDashes() {
        assertEquals("ABCDEFGH", MinecraftLinkService.normalize("abcd-efgh"));
        assertEquals("ABCDEFGH", MinecraftLinkService.normalize("  ABCD EFGH "));
        assertEquals("", MinecraftLinkService.normalize(null));
    }
}
