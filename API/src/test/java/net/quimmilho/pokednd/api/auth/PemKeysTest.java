/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PemKeysTest {

    private static KeyPair pair;
    private static KeyPair other;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        pair = gen.generateKeyPair();
        other = gen.generateKeyPair();
    }

    @Test
    void readsTheKeysAsTheEnvFileHoldsThem() {
        // one line, line breaks written as the two characters backslash and n
        String oneLine = PemKeys.pem("PRIVATE KEY", pair.getPrivate().getEncoded()).replace("\n", "\\n");
        assertFalse(oneLine.contains("\n"));
        PrivateKey read = PemKeys.privateKey(oneLine);
        assertArrayEquals(pair.getPrivate().getEncoded(), read.getEncoded());
        // and with real line breaks
        assertArrayEquals(pair.getPrivate().getEncoded(), PemKeys.privateKey(PemKeys.pem("PRIVATE KEY", pair.getPrivate().getEncoded())).getEncoded());
        PublicKey pub = PemKeys.publicKey(PemKeys.pem("PUBLIC KEY", pair.getPublic().getEncoded()).replace("\n", "\\n"));
        assertArrayEquals(pair.getPublic().getEncoded(), pub.getEncoded());
    }

    @Test
    void theNeverStoredPublicKeyCanBeDerivedFromThePrivateOne() {
        PublicKey derived = PemKeys.publicFrom(pair.getPrivate());
        assertArrayEquals(pair.getPublic().getEncoded(), derived.getEncoded());
        assertTrue(PemKeys.matches(pair.getPrivate(), pair.getPublic()));
        assertFalse(PemKeys.matches(pair.getPrivate(), other.getPublic()));
    }

    @Test
    void badKeysAreRefusedWithAMessageThatSaysWhatToDo() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> PemKeys.privateKey("CHANGE_ME"));
        assertTrue(e.getMessage().contains("POKEDND_JWT_PRIVATE_KEY"), e.getMessage());
        IllegalArgumentException old = assertThrows(IllegalArgumentException.class,
                () -> PemKeys.privateKey("-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----"));
        assertTrue(old.getMessage().contains("openssl pkcs8 -topk8"), old.getMessage());
        assertThrows(IllegalArgumentException.class, () -> PemKeys.publicKey("not a key"));
    }
}
