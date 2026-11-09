/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.auth;

import java.security.SecureRandom;
import java.util.UUID;

/** Makes the time-ordered UUIDs (version 7) that every row uses as its id (chapter 17.2). */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static UUID newId() {
        return newId(System.currentTimeMillis());
    }

    static UUID newId(long millis) {
        byte[] rnd = new byte[10];
        RANDOM.nextBytes(rnd);
        long msb = (millis & 0xFFFFFFFFFFFFL) << 16 | 0x7000L | ((rnd[0] & 0x0F) << 8) | (rnd[1] & 0xFF);
        long lsb = 0L;
        for (int i = 2; i < 10; i++) {
            lsb = (lsb << 8) | (rnd[i] & 0xFF);
        }
        lsb = (lsb & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }
}
