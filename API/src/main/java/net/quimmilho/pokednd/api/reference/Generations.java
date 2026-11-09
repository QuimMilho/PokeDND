/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.reference;

/** Generations by National Dex range (chapter 9.6, decided). */
public final class Generations {

    private static final int[] LAST_DEX = {151, 251, 386, 493, 649, 721, 809, 905, 1025};

    private Generations() {
    }

    /** The generation (1 to 9) of a Dex number; numbers beyond the last range count as generation 9. */
    public static int of(int dex) {
        for (int i = 0; i < LAST_DEX.length; i++) {
            if (dex <= LAST_DEX[i]) {
                return i + 1;
            }
        }
        return 9;
    }
}
