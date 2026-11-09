/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import net.quimmilho.pokednd.core.dice.Dice;

/**
 * The dice the system rolls for a player or for itself (chapter 6.7, "Fairness"). Every digital roll comes from one stored
 * <em>seed</em>: the same dice and the same seed always show the same faces, on any computer. The answer event keeps the seed next to
 * the faces, so a replay does not roll again, and anyone can check that the faces really come from the seed ({@link GameReducer}
 * does). The generator is fixed ({@value #GENERATOR}); changing it would change the meaning of old logs.
 */
public final class DigitalRoller {

    /** The generator algorithm of the JDK that turns a seed into faces. */
    public static final String GENERATOR = "L64X128MixRandom";

    private DigitalRoller() {
    }

    /** The faces these dice show for this seed: one value from 1 to the number of sides for every die. */
    public static List<Integer> roll(Dice dice, long seed) {
        RandomGenerator generator = RandomGeneratorFactory.of(GENERATOR).create(seed);
        List<Integer> faces = new ArrayList<>(dice.count());
        for (int i = 0; i < dice.count(); i++) {
            faces.add(generator.nextInt(dice.sides()) + 1);
        }
        return faces;
    }

    /**
     * Where new seeds come from (the setting {@code dice.rng.source}): {@code secure} takes them from the operating system's secure
     * generator; {@code seeded} takes them from a generator that starts at {@code baseSeed}, so a whole game can be repeated.
     */
    public static LongSupplier seeds(RollSettings.RngSource source, long baseSeed) {
        if (source == RollSettings.RngSource.SEEDED) {
            RandomGenerator chain = RandomGeneratorFactory.of(GENERATOR).create(baseSeed);
            return chain::nextLong;
        }
        SecureRandom secure = new SecureRandom();
        return secure::nextLong;
    }
}
