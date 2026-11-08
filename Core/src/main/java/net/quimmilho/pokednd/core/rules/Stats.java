/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.rules;

/**
 * The official stat formulas (chapter 5.2), using whole-number arithmetic only so the result never depends on rounding errors:
 *
 * <pre>
 * HP    = floor((2 × Base + IV + floor(EV / 4)) × Level / 100) + Level + 10
 * Other = floor((floor((2 × Base + IV + floor(EV / 4)) × Level / 100) + 5) × NatureMultiplier)
 * </pre>
 *
 * Stat stages (−6 to +6) are in {@link #stageMultiplier(int)} and {@link #accuracyStageMultiplier(int)}.
 */
public final class Stats {

    /** The highest IV. */
    public static final int MAX_IV = 31;
    /** The highest EV in one stat. */
    public static final int MAX_EV = 252;
    /** The highest sum of the six EVs. */
    public static final int MAX_EV_TOTAL = 510;
    /** The highest level that exists (the game's own cap is a setting, {@code pokemon.level.cap}). */
    public static final int MAX_LEVEL = 100;
    /** The largest stat stage in either direction. */
    public static final int MAX_STAGE = 6;

    private Stats() {
    }

    /** The HP stat. */
    public static int hp(int base, int iv, int ev, int level) {
        check(base, iv, ev, level);
        return core(base, iv, ev, level) + level + 10;
    }

    /** Any stat except HP, with the nature multiplier (1.1, 0.9 or 1.0). */
    public static int other(int base, int iv, int ev, int level, double natureMultiplier) {
        check(base, iv, ev, level);
        int tenths = (int) Math.round(natureMultiplier * 10.0); // 11, 9 or 10: whole numbers keep the maths exact
        return (core(base, iv, ev, level) + 5) * tenths / 10;
    }

    /** One stat of a Pokémon, with the nature applied (HP ignores the nature). */
    public static int stat(Stat stat, int base, int iv, int ev, int level, Nature nature) {
        return stat == Stat.HP ? hp(base, iv, ev, level) : other(base, iv, ev, level, nature.multiplier(stat));
    }

    /**
     * All six stats.
     *
     * @throws IllegalArgumentException if an IV is outside 0 to 31, an EV outside 0 to 252, the EVs add up to more than 510,
     *                                  or the level is outside 1 to 100
     */
    public static StatBlock compute(StatBlock base, StatBlock ivs, StatBlock evs, int level, Nature nature) {
        if (evs.total() > MAX_EV_TOTAL) {
            throw new IllegalArgumentException("The EVs add up to " + evs.total() + ", the limit is " + MAX_EV_TOTAL);
        }
        return new StatBlock(
                stat(Stat.HP, base.hp(), ivs.hp(), evs.hp(), level, nature),
                stat(Stat.ATTACK, base.attack(), ivs.attack(), evs.attack(), level, nature),
                stat(Stat.DEFENSE, base.defense(), ivs.defense(), evs.defense(), level, nature),
                stat(Stat.SPECIAL_ATTACK, base.specialAttack(), ivs.specialAttack(), evs.specialAttack(), level, nature),
                stat(Stat.SPECIAL_DEFENSE, base.specialDefense(), ivs.specialDefense(), evs.specialDefense(), level, nature),
                stat(Stat.SPEED, base.speed(), ivs.speed(), evs.speed(), level, nature));
    }

    private static int core(int base, int iv, int ev, int level) {
        return (2 * base + iv + ev / 4) * level / 100;
    }

    private static void check(int base, int iv, int ev, int level) {
        if (base < 0 || base > 255) {
            throw new IllegalArgumentException("A base stat is 0 to 255, not " + base);
        }
        if (iv < 0 || iv > MAX_IV) {
            throw new IllegalArgumentException("An IV is 0 to " + MAX_IV + ", not " + iv);
        }
        if (ev < 0 || ev > MAX_EV) {
            throw new IllegalArgumentException("An EV is 0 to " + MAX_EV + ", not " + ev);
        }
        if (level < 1 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("A level is 1 to " + MAX_LEVEL + ", not " + level);
        }
    }

    // ---------------------------------------------------------------- stat stages

    /**
     * The multiplier of a stage for Attack, Defense, Sp. Atk, Sp. Def and Speed: {@code (2 + n) / 2} above zero and
     * {@code 2 / (2 − n)} below. The stage is kept between −6 and +6.
     */
    public static double stageMultiplier(int stage) {
        int n = clampStage(stage);
        return n >= 0 ? (2.0 + n) / 2.0 : 2.0 / (2.0 - n);
    }

    /** The multiplier of an accuracy or evasion stage: {@code (3 + n) / 3} above zero and {@code 3 / (3 − n)} below. */
    public static double accuracyStageMultiplier(int stage) {
        int n = clampStage(stage);
        return n >= 0 ? (3.0 + n) / 3.0 : 3.0 / (3.0 - n);
    }

    /** A stat after its stage, rounded down, as in the games. */
    public static int applyStage(int stat, int stage) {
        int n = clampStage(stage);
        return n >= 0 ? stat * (2 + n) / 2 : stat * 2 / (2 - n);
    }

    /** Keeps a stage between −6 and +6. */
    public static int clampStage(int stage) {
        return Math.max(-MAX_STAGE, Math.min(MAX_STAGE, stage));
    }
}
