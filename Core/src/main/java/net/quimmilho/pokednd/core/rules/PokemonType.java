/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.rules;

import java.util.Locale;
import java.util.Optional;

/** The 18 types of the current official chart (chapter 7.7). */
public enum PokemonType {
    NORMAL, FIRE, WATER, ELECTRIC, GRASS, ICE, FIGHTING, POISON, GROUND, FLYING, PSYCHIC, BUG, ROCK, GHOST, DRAGON, DARK, STEEL,
    FAIRY;

    /** The lower-case id used in the data, for example {@code fire}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Finds a type by id or name, ignoring case ({@code Fire}, {@code fire} and {@code FIRE} all work). */
    public static Optional<PokemonType> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(text.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
