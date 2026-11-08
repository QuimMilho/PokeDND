/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.rules;

/** Effort values (EVs): 0 to 252 in each stat and 510 in total (chapters 5.1, 5.3). */
public final class Evs {

    private Evs() {
    }

    /**
     * The EVs after a defeated Pokémon gives its EV yield. Each stat stops at 252 and the total stops at 510; what does
     * not fit is lost. Stats are filled in the order HP, Attack, Defense, Sp. Atk, Sp. Def, Speed.
     */
    public static StatBlock gain(StatBlock current, StatBlock yield) {
        StatBlock result = current;
        int total = current.total();
        for (Stat s : Stat.values()) {
            int room = Math.min(Stats.MAX_EV - result.get(s), Stats.MAX_EV_TOTAL - total);
            int add = Math.max(0, Math.min(yield.get(s), room));
            result = result.with(s, result.get(s) + add);
            total += add;
        }
        return result;
    }
}
