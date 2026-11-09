/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.List;
import java.util.Map;
import net.quimmilho.pokednd.core.trainer.AbilityScores;
import net.quimmilho.pokednd.core.trainer.Trainer;

/** Small things the game tests share. */
final class Fixtures {

    private Fixtures() {
    }

    static Trainer trainer(String name) {
        return new Trainer(name, "ace_trainer", 1, 0, AbilityScores.of(15, 14, 13, 11, 10, 9), List.of("pokemon_handling", "insight"), 9, 9, 3000,
                Map.of("poke_ball", 5, "potion", 3));
    }

    static Trainer trainerWithHp(Trainer base, int hp) {
        return new Trainer(base.name(), base.classId(), base.level(), base.xp(), base.scores(), base.skills(), base.maxHp(), hp, base.money(), base.items());
    }
}
