/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.rules;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static net.quimmilho.pokednd.core.rules.Stat.ATTACK;
import static net.quimmilho.pokednd.core.rules.Stat.DEFENSE;
import static net.quimmilho.pokednd.core.rules.Stat.SPECIAL_ATTACK;
import static net.quimmilho.pokednd.core.rules.Stat.SPECIAL_DEFENSE;
import static net.quimmilho.pokednd.core.rules.Stat.SPEED;

/**
 * One of the 25 natures (chapter 5.2): it raises one stat by 10 % and lowers another by 10 %. Five natures are neutral.
 * HP is never changed by a nature.
 *
 * @param id    lower-case id, for example {@code adamant}
 * @param name  display name
 * @param plus  the stat raised by 10 %; empty for a neutral nature
 * @param minus the stat lowered by 10 %; empty for a neutral nature
 */
public record Nature(String id, String name, Optional<Stat> plus, Optional<Stat> minus) {

    /** The multiplier this nature applies to a stat: 1.1, 0.9 or 1.0. HP always gives 1.0. */
    public double multiplier(Stat stat) {
        if (plus.isPresent() && plus.get() == stat) {
            return 1.1;
        }
        if (minus.isPresent() && minus.get() == stat) {
            return 0.9;
        }
        return 1.0;
    }

    /** True for the five neutral natures. */
    public boolean neutral() {
        return plus.isEmpty();
    }

    private static Nature n(String name, Stat plus, Stat minus) {
        return new Nature(name.toLowerCase(Locale.ROOT), name, Optional.ofNullable(plus), Optional.ofNullable(minus));
    }

    private static final List<Nature> ALL = List.of(
            n("Hardy", null, null), n("Docile", null, null), n("Serious", null, null), n("Bashful", null, null),
            n("Quirky", null, null),
            n("Lonely", ATTACK, DEFENSE), n("Brave", ATTACK, SPEED), n("Adamant", ATTACK, SPECIAL_ATTACK),
            n("Naughty", ATTACK, SPECIAL_DEFENSE),
            n("Bold", DEFENSE, ATTACK), n("Relaxed", DEFENSE, SPEED), n("Impish", DEFENSE, SPECIAL_ATTACK),
            n("Lax", DEFENSE, SPECIAL_DEFENSE),
            n("Timid", SPEED, ATTACK), n("Hasty", SPEED, DEFENSE), n("Jolly", SPEED, SPECIAL_ATTACK),
            n("Naive", SPEED, SPECIAL_DEFENSE),
            n("Modest", SPECIAL_ATTACK, ATTACK), n("Mild", SPECIAL_ATTACK, DEFENSE), n("Quiet", SPECIAL_ATTACK, SPEED),
            n("Rash", SPECIAL_ATTACK, SPECIAL_DEFENSE),
            n("Calm", SPECIAL_DEFENSE, ATTACK), n("Gentle", SPECIAL_DEFENSE, DEFENSE), n("Sassy", SPECIAL_DEFENSE, SPEED),
            n("Careful", SPECIAL_DEFENSE, SPECIAL_ATTACK));

    /** All 25 natures, neutral ones first. */
    public static List<Nature> all() {
        return ALL;
    }

    /** Finds a nature by id or name, ignoring case. */
    public static Optional<Nature> find(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String id = text.strip().toLowerCase(Locale.ROOT);
        return ALL.stream().filter(x -> x.id().equals(id)).findFirst();
    }
}
