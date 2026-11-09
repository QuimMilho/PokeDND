/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void theHashIsArgon2idInTheStandardTextForm() {
        String h = hasher.hash("correct horse battery staple");
        assertTrue(Pattern.matches("\\$argon2id\\$v=19\\$m=19456,t=2,p=1\\$[A-Za-z0-9+/]+\\$[A-Za-z0-9+/]+", h), h);
        assertFalse(h.contains("correct horse"));
    }

    @Test
    void theRightPasswordVerifiesAndOthersDoNot() {
        String h = hasher.hash("correct horse battery staple");
        assertTrue(hasher.verify(h, "correct horse battery staple"));
        assertFalse(hasher.verify(h, "correct horse battery stapl"));
        assertFalse(hasher.verify(h, ""));
        assertFalse(hasher.verify(null, "x"));
        assertFalse(hasher.verify("not a hash", "x"));
        assertFalse(hasher.verifyDummy("anything"));
    }

    @Test
    void everyHashHasItsOwnSalt() {
        assertNotEquals(hasher.hash("same password 123"), hasher.hash("same password 123"));
    }
}
