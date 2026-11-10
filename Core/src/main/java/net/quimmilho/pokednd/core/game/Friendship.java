/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

/**
 * How friendship changes (chapter 5.8): up with every level, a little for each stretch travelled as the lead Pokémon, down when it faints.
 * The numbers are the settings {@code pokemon.friendship.*}; a class feature (Breeder, Coordinator) multiplies the gain. Friendship stays
 * between 0 and 255.
 */
public final class Friendship {

    private Friendship() {
    }

    /** What one level-up gives at this friendship before any multiplier: the first number below 100, the second from 100 to 199, the third from 200. */
    public static int gainPerLevel(ProgressSettings settings, int friendship) {
        return settings.levelUpGain().get(friendship < 100 ? 0 : friendship < 200 ? 1 : 2);
    }

    /**
     * The friendship after gaining {@code levels} levels. Each level counts on its own, so a Pokémon that crosses 100 or 200 on the way
     * gets the smaller gain from there on. {@code multiplier} is the class feature ({@code friendship.gainMultiplier}); each gain is rounded down.
     */
    public static int afterLevelUps(ProgressSettings settings, int friendship, int levels, double multiplier) {
        int now = friendship;
        for (int i = 0; i < levels; i++) {
            now = Math.min(255, now + (int) Math.floor(gainPerLevel(settings, now) * multiplier + 1e-9));
        }
        return now;
    }

    /** What a vitamin gives: +5 below 100, +3 from 100 to 199, +2 from 200 (the main games). */
    public static final int[] VITAMIN = {5, 3, 2};

    /** What a friendship berry (Pomeg and the like) gives: +10 below 100, +5 from 100 to 199, +2 from 200 (the main games). */
    public static final int[] FRIENDSHIP_BERRY = {10, 5, 2};

    /**
     * The friendship after an item gave its band of {@code gains} (below 100, 100 to 199, 200 and more), multiplied by the class feature
     * {@code friendship.gainMultiplier} and rounded down; at most 255 (chapter 5.8: "using a friendship berry or vitamin: as in the main games").
     */
    public static int afterItem(int friendship, int[] gains, double multiplier) {
        int gain = gains[friendship < 100 ? 0 : friendship < 200 ? 1 : 2];
        return Math.min(255, friendship + (int) Math.floor(gain * multiplier + 1e-9));
    }

    /** The friendship after fainting: {@code pokemon.friendship.faintLoss} less, never below 0. */
    public static int afterFainting(ProgressSettings settings, int friendship) {
        return Math.max(0, friendship - settings.faintLoss());
    }

    /**
     * The friendship after travelling {@code points} movement points as the lead Pokémon: +1 for every {@code pokemon.friendship.pointsPerGain}
     * points. {@code carry} are the points left over from before; the result is the new friendship and the points left over.
     */
    public static int[] afterTravel(ProgressSettings settings, int friendship, int carry, int points) {
        int total = carry + points;
        int gain = total / settings.pointsPerGain();
        return new int[] {Math.min(255, friendship + gain), total % settings.pointsPerGain()};
    }
}
