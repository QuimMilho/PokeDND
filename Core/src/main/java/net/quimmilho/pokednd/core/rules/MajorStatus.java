/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.rules;

import java.util.Locale;

/** The five major status conditions (chapter 5.1, 5.6). A Pokémon has at most one. */
public enum MajorStatus {
    BURN, POISON, PARALYSIS, SLEEP, FREEZE;

    /** The lower-case id used in the data, for example {@code paralysis}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
