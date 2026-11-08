/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.rules;

import java.util.Locale;

/**
 * The Pokémon settings that stats and experience use (chapter 5.12), with the defaults of the specification.
 *
 * @param levelCap       {@code pokemon.level.cap}: the highest Pokémon level (default 100)
 * @param xpShare        {@code pokemon.xp.share}: who gets experience (default {@link XpShare#GEN6})
 * @param xpMultiplier   {@code pokemon.xp.multiplier}: scales all Pokémon XP (default 1.0)
 * @param ivMode         {@code pokemon.ivs.mode}: random or fixed IVs (default random)
 * @param ivFixedValue   {@code pokemon.ivs.fixedValue}: the IV of every stat when the mode is fixed (default 15)
 * @param naturesEnabled {@code pokemon.natures.enabled}: when off, every Pokémon counts as neutral (default on)
 */
public record PokemonSettings(int levelCap, XpShare xpShare, double xpMultiplier, IvMode ivMode, int ivFixedValue,
                              boolean naturesEnabled) {

    /** {@code pokemon.xp.share}. */
    public enum XpShare {
        /** Participants get the full amount and the rest of the team half (Gen 6 and later; default). */
        GEN6("gen6"),
        /** Only the Pokémon that took part get experience. */
        PARTICIPANTS_ONLY("participantsOnly"),
        /** The whole team gets the full amount. */
        ALL("all");

        private final String id;

        XpShare(String id) {
            this.id = id;
        }

        /** The value written in the settings. */
        public String id() {
            return id;
        }

        /** Reads a setting value, ignoring case. */
        public static XpShare parse(String text) {
            for (XpShare s : values()) {
                if (s.id.equalsIgnoreCase(text.strip())) {
                    return s;
                }
            }
            throw new IllegalArgumentException("pokemon.xp.share must be gen6, participantsOnly or all, not " + text);
        }
    }

    /** {@code pokemon.ivs.mode}. */
    public enum IvMode {
        /** Each IV is drawn from 0 to 31 (default). */
        RANDOM,
        /** Every IV is {@code pokemon.ivs.fixedValue}. */
        FIXED;

        /** The value written in the settings. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Reads a setting value, ignoring case. */
        public static IvMode parse(String text) {
            for (IvMode m : values()) {
                if (m.id().equalsIgnoreCase(text.strip())) {
                    return m;
                }
            }
            throw new IllegalArgumentException("pokemon.ivs.mode must be random or fixed, not " + text);
        }
    }

    public PokemonSettings {
        if (levelCap < 1 || levelCap > Stats.MAX_LEVEL) {
            throw new IllegalArgumentException("pokemon.level.cap must be 1 to " + Stats.MAX_LEVEL + ", not " + levelCap);
        }
        if (xpMultiplier < 0) {
            throw new IllegalArgumentException("pokemon.xp.multiplier cannot be negative");
        }
        if (ivFixedValue < 0 || ivFixedValue > Stats.MAX_IV) {
            throw new IllegalArgumentException("pokemon.ivs.fixedValue must be 0 to " + Stats.MAX_IV + ", not " + ivFixedValue);
        }
    }

    /** The defaults of chapter 5.12. */
    public static PokemonSettings defaults() {
        return new PokemonSettings(100, XpShare.GEN6, 1.0, IvMode.RANDOM, 15, true);
    }

    /** A copy with another level cap. */
    public PokemonSettings withLevelCap(int cap) {
        return new PokemonSettings(cap, xpShare, xpMultiplier, ivMode, ivFixedValue, naturesEnabled);
    }

    /** A copy with another experience share rule. */
    public PokemonSettings withXpShare(XpShare share) {
        return new PokemonSettings(levelCap, share, xpMultiplier, ivMode, ivFixedValue, naturesEnabled);
    }

    /** A copy with another experience multiplier. */
    public PokemonSettings withXpMultiplier(double multiplier) {
        return new PokemonSettings(levelCap, xpShare, multiplier, ivMode, ivFixedValue, naturesEnabled);
    }

    /** A copy with fixed IVs. */
    public PokemonSettings withFixedIvs(int value) {
        return new PokemonSettings(levelCap, xpShare, xpMultiplier, IvMode.FIXED, value, naturesEnabled);
    }

    /** A copy with natures switched on or off. */
    public PokemonSettings withNaturesEnabled(boolean enabled) {
        return new PokemonSettings(levelCap, xpShare, xpMultiplier, ivMode, ivFixedValue, enabled);
    }

    /** The nature to use in the stat formula: the Pokémon's own, or a neutral one when natures are switched off. */
    public Nature natureToUse(Nature nature) {
        return naturesEnabled ? nature : Nature.find("hardy").orElseThrow();
    }
}
