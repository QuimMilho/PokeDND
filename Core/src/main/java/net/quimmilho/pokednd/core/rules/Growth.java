/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.rules;

import java.util.Locale;
import java.util.Optional;

/**
 * The six growth groups (chapter 5.3): how much total experience a Pokémon needs for each level. All results are rounded down,
 * and a Pokémon at level 1 has 0 XP.
 */
public enum Growth {
    /** {@code 4n³ / 5}. */
    FAST("Fast"),
    /** {@code n³}. */
    MEDIUM_FAST("Medium Fast"),
    /** {@code 6n³/5 − 15n² + 100n − 140}. */
    MEDIUM_SLOW("Medium Slow"),
    /** {@code 5n³ / 4}. */
    SLOW("Slow"),
    /** Four pieces, see {@link #totalXp(int)}. */
    ERRATIC("Erratic"),
    /** Three pieces, see {@link #totalXp(int)}. */
    FLUCTUATING("Fluctuating");

    private final String displayName;

    Growth(String displayName) {
        this.displayName = displayName;
    }

    /** The name on a card, for example {@code Medium Slow}. */
    public String displayName() {
        return displayName;
    }

    /** The id used in the data, for example {@code medium_slow}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Reads {@code medium_slow}, {@code Medium Slow} or {@code medium-slow}, ignoring case. */
    public static Optional<Growth> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String key = text.strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (Growth g : values()) {
            if (g.id().equals(key)) {
                return Optional.of(g);
            }
        }
        return Optional.empty();
    }

    /**
     * The total XP a Pokémon has when it reaches this level (level 1 is 0).
     *
     * @throws IllegalArgumentException if the level is outside 1 to 100
     */
    public int totalXp(int level) {
        if (level < 1 || level > Stats.MAX_LEVEL) {
            throw new IllegalArgumentException("A level is 1 to " + Stats.MAX_LEVEL + ", not " + level);
        }
        if (level == 1) {
            return 0;
        }
        long n = level;
        long cube = n * n * n;
        long xp = switch (this) {
            case FAST -> 4 * cube / 5;
            case MEDIUM_FAST -> cube;
            case MEDIUM_SLOW -> 6 * cube / 5 - 15 * n * n + 100 * n - 140;
            case SLOW -> 5 * cube / 4;
            case ERRATIC -> {
                if (n <= 50) {
                    yield cube * (100 - n) / 50;
                } else if (n <= 68) {
                    yield cube * (150 - n) / 100;
                } else if (n <= 98) {
                    yield cube * ((1911 - 10 * n) / 3) / 500;
                } else {
                    yield cube * (160 - n) / 100;
                }
            }
            case FLUCTUATING -> {
                if (n <= 15) {
                    yield cube * ((n + 1) / 3 + 24) / 50;
                } else if (n <= 36) {
                    yield cube * (n + 14) / 50;
                } else {
                    yield cube * (n / 2 + 32) / 50;
                }
            }
        };
        return (int) xp;
    }

    /** The XP needed to go from this level to the next. */
    public int xpToNext(int level) {
        return totalXp(level + 1) - totalXp(level);
    }

    /** The level a Pokémon with this much total XP has, never above the cap. */
    public int levelFor(long xp, int levelCap) {
        int level = 1;
        while (level < levelCap && level < Stats.MAX_LEVEL && xp >= totalXp(level + 1)) {
            level++;
        }
        return level;
    }
}
