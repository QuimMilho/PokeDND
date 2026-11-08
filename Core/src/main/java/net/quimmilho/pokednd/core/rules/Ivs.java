/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.rules;

import java.util.random.RandomGenerator;

/** Individual values (IVs, 0 to 31) of a new Pokémon, and how IV candies change them (chapters 5.1, 5.12, 8.8). */
public final class Ivs {

    private Ivs() {
    }

    /**
     * IVs for a new Pokémon: each stat is drawn from 0 to 31 ({@code random}), or all are the fixed value ({@code fixed}).
     */
    public static StatBlock generate(PokemonSettings settings, RandomGenerator random) {
        if (settings.ivMode() == PokemonSettings.IvMode.FIXED) {
            return StatBlock.all(settings.ivFixedValue());
        }
        return new StatBlock(draw(random), draw(random), draw(random), draw(random), draw(random), draw(random));
    }

    private static int draw(RandomGenerator random) {
        return random.nextInt(Stats.MAX_IV + 1);
    }

    /** One IV after an IV candy: raised or lowered by the step, always kept between 0 and 31 (works in every IV mode). */
    public static int adjust(int iv, int step) {
        return Math.max(0, Math.min(Stats.MAX_IV, iv + step));
    }
}
