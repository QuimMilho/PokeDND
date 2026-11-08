/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.rules;

/**
 * One number for each of the six stats: base stats, IVs, EVs or the final stats of a Pokémon (chapter 5.2).
 *
 * @param hp             HP
 * @param attack         Attack
 * @param defense        Defense
 * @param specialAttack  Sp. Atk
 * @param specialDefense Sp. Def
 * @param speed          Speed
 */
public record StatBlock(int hp, int attack, int defense, int specialAttack, int specialDefense, int speed) {

    /** The same number for all six stats. */
    public static StatBlock all(int value) {
        return new StatBlock(value, value, value, value, value, value);
    }

    /** The number for one stat. */
    public int get(Stat stat) {
        return switch (stat) {
            case HP -> hp;
            case ATTACK -> attack;
            case DEFENSE -> defense;
            case SPECIAL_ATTACK -> specialAttack;
            case SPECIAL_DEFENSE -> specialDefense;
            case SPEED -> speed;
        };
    }

    /** A copy with one stat changed. */
    public StatBlock with(Stat stat, int value) {
        return new StatBlock(stat == Stat.HP ? value : hp, stat == Stat.ATTACK ? value : attack,
                stat == Stat.DEFENSE ? value : defense, stat == Stat.SPECIAL_ATTACK ? value : specialAttack,
                stat == Stat.SPECIAL_DEFENSE ? value : specialDefense, stat == Stat.SPEED ? value : speed);
    }

    /** The six numbers added up (for EVs the limit is 510). */
    public int total() {
        return hp + attack + defense + specialAttack + specialDefense + speed;
    }
}
