/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.rules;

import java.util.Locale;

/** The six stats of a Pokémon (chapter 5.2). */
public enum Stat {
    HP, ATTACK, DEFENSE, SPECIAL_ATTACK, SPECIAL_DEFENSE, SPEED;

    /** The lower-case id used in the data, for example {@code special_attack}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
