/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.rules;

/**
 * The type of Hidden Power, worked out from the six IVs with the official formula: take the lowest bit of each IV (HP, Attack, Defense,
 * Speed, Sp. Atk, Sp. Def, in that order, as {@code a} to {@code f}), then {@code floor((a + 2b + 4c + 8d + 16e + 32f) × 15 / 63)} picks
 * the type from Fighting, Flying, Poison, Ground, Rock, Bug, Ghost, Steel, Fire, Water, Grass, Electric, Psychic, Ice, Dragon, Dark. Normal
 * and Fairy cannot come out. Hidden Power always has power 60 (chapter 19.6).
 */
public final class HiddenPower {

    private static final PokemonType[] TYPES = {PokemonType.FIGHTING, PokemonType.FLYING, PokemonType.POISON, PokemonType.GROUND,
            PokemonType.ROCK, PokemonType.BUG, PokemonType.GHOST, PokemonType.STEEL, PokemonType.FIRE, PokemonType.WATER,
            PokemonType.GRASS, PokemonType.ELECTRIC, PokemonType.PSYCHIC, PokemonType.ICE, PokemonType.DRAGON, PokemonType.DARK};

    private HiddenPower() {
    }

    /** The type of Hidden Power for these IVs. */
    public static PokemonType type(StatBlock ivs) {
        int sum = (ivs.hp() & 1) + 2 * (ivs.attack() & 1) + 4 * (ivs.defense() & 1) + 8 * (ivs.speed() & 1)
                + 16 * (ivs.specialAttack() & 1) + 32 * (ivs.specialDefense() & 1);
        return TYPES[sum * 15 / 63];
    }
}
