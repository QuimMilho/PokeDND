/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

/**
 * Makes a new RSA key pair for every test run and hands it to the API as {@code POKEDND_JWT_PRIVATE_KEY} and
 * {@code POKEDND_JWT_PUBLIC_KEY} (written the way the .env file holds them: one line, line breaks as {@code \n}). No key is stored in git.
 */
public class TestKeys implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();
            return Map.of("pokednd.auth.jwt-private-key", oneLine("PRIVATE KEY", pair.getPrivate().getEncoded()),
                    "pokednd.auth.jwt-public-key", oneLine("PUBLIC KEY", pair.getPublic().getEncoded()));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String oneLine(String label, byte[] der) {
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der);
        return ("-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n").replace("\n", "\\n");
    }

    @Override
    public void stop() {
    }
}
