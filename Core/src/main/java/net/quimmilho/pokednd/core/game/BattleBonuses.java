/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.quimmilho.pokednd.core.combat.CatchCalculator;
import net.quimmilho.pokednd.core.combat.FleeCalculator;
import net.quimmilho.pokednd.core.game.Battle.Kind;
import net.quimmilho.pokednd.core.game.Battle.Participant;
import net.quimmilho.pokednd.core.trainer.Sheet;
import net.quimmilho.pokednd.core.trainer.Trainer;
import net.quimmilho.pokednd.core.trainer.TrainerBonuses;
import net.quimmilho.pokednd.core.trainer.TrainerRulebook;

/**
 * What the trainers' sheets add to a battle, by participant id (chapters 4.2, 4.4 and 7.10 to 7.12), and facts about the place that
 * the balls need. The battle itself does not carry sheets: whoever runs the battle builds this from the game ({@link #of}) and hands it
 * to {@link BattleDesk#resolveNext}. A participant without an entry has no bonus.
 *
 * @param accuracyPp      percentage points added to the hit chance of the participant's Pokémon (the Handling bonus and class features)
 * @param defenseRoll     what a trainer adds to the defence roll when an enemy attacks them: {@code AC − 10}
 * @param damageReduction what class features take off the damage a trainer takes
 * @param catchMultiplier the whole catch multiplier of the trainer: the Handling bonus times the class features (1.0 if none)
 * @param fleePp          percentage points added to the chance to flee: the DEX bonus plus class features
 * @param nightOrCave     true at night or in a cave (Dusk Ball)
 * @param caughtBefore    the species that were caught before in this game, by species id (Repeat Ball)
 */
public record BattleBonuses(Map<String, Double> accuracyPp, Map<String, Integer> defenseRoll, Map<String, Integer> damageReduction,
                            Map<String, Double> catchMultiplier, Map<String, Double> fleePp, boolean nightOrCave, Set<String> caughtBefore) {

    public BattleBonuses {
        accuracyPp = frozen(accuracyPp);
        defenseRoll = frozen(defenseRoll);
        damageReduction = frozen(damageReduction);
        catchMultiplier = frozen(catchMultiplier);
        fleePp = frozen(fleePp);
        caughtBefore = caughtBefore == null ? Set.of() : Set.copyOf(caughtBefore);
    }

    private static <V> Map<String, V> frozen(Map<String, V> map) {
        return map == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    /** No bonus for anybody, by day, nothing caught before. */
    public static BattleBonuses none() {
        return new BattleBonuses(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), false, Set.of());
    }

    /** Only the accuracy bonus of each participant (the shape of phase 45). */
    public static BattleBonuses accuracy(Map<String, Double> accuracyPp) {
        return new BattleBonuses(accuracyPp, Map.of(), Map.of(), Map.of(), Map.of(), false, Set.of());
    }

    /** A copy for another place and time. */
    public BattleBonuses at(boolean night, Set<String> species) {
        return new BattleBonuses(accuracyPp, defenseRoll, damageReduction, catchMultiplier, fleePp, night, species);
    }

    /**
     * The bonuses of every trainer in the battle that is going on, from their sheets with the default command, catch and flee numbers.
     * The place and time are those of {@link #at}.
     */
    public static BattleBonuses of(GameState state, TrainerRulebook rulebook) {
        Battle b = state.battle();
        Map<String, Double> accuracy = new LinkedHashMap<>();
        Map<String, Integer> defense = new LinkedHashMap<>();
        Map<String, Integer> reduction = new LinkedHashMap<>();
        Map<String, Double> catches = new LinkedHashMap<>();
        Map<String, Double> flee = new LinkedHashMap<>();
        if (b != null) {
            for (Participant p : b.participants().values()) {
                Trainer trainer = p.kind() == Kind.TRAINER && p.trainerId() != null ? state.trainers().get(p.trainerId()) : null;
                if (trainer == null) {
                    continue;
                }
                Sheet sheet = rulebook.sheet(trainer);
                accuracy.put(p.id(), sheet.commandAccuracyPp(TrainerBonuses.CommandSettings.defaults()) + sheet.featureAccuracyPp());
                defense.put(p.id(), sheet.armorClass() - 10);
                reduction.put(p.id(), sheet.damageReduction());
                catches.put(p.id(), sheet.catchMultiplier(CatchCalculator.Settings.defaults()));
                flee.put(p.id(), sheet.fleeBonusPp(FleeCalculator.Settings.defaults()));
            }
        }
        return new BattleBonuses(accuracy, defense, reduction, catches, flee, false, Set.of());
    }

    double accuracyPp(String participantId) {
        return accuracyPp.getOrDefault(participantId, 0.0);
    }

    int defenseRoll(String participantId) {
        return defenseRoll.getOrDefault(participantId, 0);
    }

    int damageReduction(String participantId) {
        return damageReduction.getOrDefault(participantId, 0);
    }

    double catchMultiplier(String participantId) {
        return catchMultiplier.getOrDefault(participantId, 1.0);
    }

    double fleePp(String participantId) {
        return fleePp.getOrDefault(participantId, 0.0);
    }
}
