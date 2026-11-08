/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.rules;

import net.quimmilho.pokednd.core.rules.PokemonSettings.XpShare;

/**
 * The experience a Pokémon earns for defeating another one (chapter 5.3), and what a gain does to its level.
 *
 * <pre>
 * XP = floor((b × Lf / (5 × s)) × ((2 × Lf + 10) / (Lf + Lp + 10)) ^ 2.5 + 1) × t × e
 * </pre>
 */
public final class Experience {

    private Experience() {
    }

    /**
     * The experience one Pokémon gets for a defeated one.
     *
     * @param baseExperience the defeated species' Experience Value ({@code b})
     * @param defeatedLevel  its level ({@code Lf})
     * @param receiverLevel  the level of the Pokémon that gets the XP ({@code Lp})
     * @param participant    true if the receiver took part in the battle ({@code s} = 1), false for the rest of the team
     * @param trainerOwned   true if the defeated Pokémon belonged to an NPC trainer ({@code t} = 1.5)
     * @param luckyEgg       true if the receiver holds a Lucky Egg ({@code e} = 1.5)
     * @param settings       {@code pokemon.xp.share} and {@code pokemon.xp.multiplier}
     * @return the XP, rounded down; 0 if the share rule gives this Pokémon nothing
     */
    public static int reward(int baseExperience, int defeatedLevel, int receiverLevel, boolean participant, boolean trainerOwned,
                             boolean luckyEgg, PokemonSettings settings) {
        if (baseExperience < 0 || defeatedLevel < 1 || receiverLevel < 1) {
            throw new IllegalArgumentException("Experience value and levels must be positive");
        }
        int s;
        if (participant || settings.xpShare() == XpShare.ALL) {
            s = 1;
        } else if (settings.xpShare() == XpShare.GEN6) {
            s = 2;
        } else {
            return 0; // participants only
        }
        double lf = defeatedLevel;
        double ratio = (2.0 * lf + 10.0) / (lf + receiverLevel + 10.0);
        double plain = Math.floor(baseExperience * lf / (5.0 * s) * Math.pow(ratio, 2.5) + 1.0);
        double xp = plain * (trainerOwned ? 1.5 : 1.0) * (luckyEgg ? 1.5 : 1.0) * settings.xpMultiplier();
        return (int) Math.floor(xp + 1e-9);
    }

    /**
     * What a gain of XP does to a Pokémon.
     *
     * @param totalXp      the new total XP (never above what the level cap allows)
     * @param level        the new level
     * @param levelsGained how many levels it went up
     */
    public record Gain(int totalXp, int level, int levelsGained) {
    }

    /**
     * Adds XP to a Pokémon. At the level cap the XP stops at the total for that level.
     *
     * @param growth     the species' growth group
     * @param currentXp  total XP now
     * @param gained     XP to add
     * @param settings   {@code pokemon.level.cap}
     */
    public static Gain add(Growth growth, int currentXp, int gained, PokemonSettings settings) {
        int cap = settings.levelCap();
        int before = growth.levelFor(currentXp, cap);
        long sum = (long) currentXp + Math.max(0, gained);
        int ceiling = growth.totalXp(cap);
        int total = (int) Math.min(sum, ceiling);
        int after = growth.levelFor(total, cap);
        return new Gain(total, after, after - before);
    }
}
