/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

/** How hard a skill check is: the suggested DC guide of chapter 4.1. */
public enum Difficulty {
    VERY_EASY(5),
    EASY(10),
    MEDIUM(15),
    HARD(20),
    VERY_HARD(25),
    NEARLY_IMPOSSIBLE(30);

    private final int dc;

    Difficulty(int dc) {
        this.dc = dc;
    }

    /** The number to beat or equal. */
    public int dc() {
        return dc;
    }
}
