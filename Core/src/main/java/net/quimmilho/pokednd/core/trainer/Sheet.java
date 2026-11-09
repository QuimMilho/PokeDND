/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.quimmilho.pokednd.core.combat.CatchCalculator;
import net.quimmilho.pokednd.core.combat.FleeCalculator;

/**
 * Everything on a trainer's sheet, worked out from the stored {@link Trainer} (chapter 4.1).
 *
 * @param trainer          the stored trainer
 * @param trainerClass     the trainer's class
 * @param proficiencyBonus +2 to +6
 * @param modifiers        the modifier of each ability
 * @param armorClass       {@code 10 + DEX modifier + class bonus}
 * @param savingThrows     the bonus of each saving throw (modifier, plus proficiency for the class's two saves)
 * @param proficient       skills the trainer is proficient in (the picked ones and the expertise skills)
 * @param skillBonuses     the bonus of every skill: ability modifier, plus proficiency, plus proficiency again for expertise
 * @param handlingModifier the Pokémon Handling bonus (chapter 4.2)
 * @param features         the class features the trainer has
 * @param effects          what the features add up to
 */
public record Sheet(Trainer trainer, TrainerClass trainerClass, int proficiencyBonus, Map<Ability, Integer> modifiers, int armorClass,
                    Map<Ability, Integer> savingThrows, Set<String> proficient, Map<String, Integer> skillBonuses,
                    int handlingModifier, List<ClassFeature> features, EffectTotals effects) {

    /** The bonus of one skill. */
    public int skillBonus(String skillId) {
        Integer b = skillBonuses.get(skillId);
        if (b == null) {
            throw new IllegalArgumentException("There is no skill " + skillId + " in this game");
        }
        return b;
    }

    /** True if the trainer has advantage on the skill from a feature. */
    public boolean hasAdvantage(String skillId) {
        return effects.advantage().contains(skillId);
    }

    /** True if the trainer may re-roll a failed check of the skill. */
    public boolean canReroll(String skillId) {
        return effects.reroll().contains(skillId);
    }

    /** The bonus the trainer's Pokémon get to accuracy from the command rule, in percentage points. */
    public double commandAccuracyPp(TrainerBonuses.CommandSettings settings) {
        return TrainerBonuses.commandAccuracyPp(handlingModifier, settings);
    }

    /** The accuracy bonus from class features (like Commanding Presence), in percentage points. */
    public double featureAccuracyPp() {
        return effects.value("accuracy.bonusPp");
    }

    /** The whole catch multiplier of this trainer: the Handling bonus and the class features. */
    public double catchMultiplier(CatchCalculator.Settings settings) {
        return TrainerBonuses.catchMultiplier(handlingModifier, effects.value("catch.multiplierBonus"), settings);
    }

    /** The flee bonus in percentage points: DEX conversion plus class features. */
    public double fleeBonusPp(FleeCalculator.Settings settings) {
        return TrainerBonuses.fleeBonusPp(modifiers.get(Ability.DEX), effects.value("flee.bonusPp"), settings);
    }

    /** Bonus points added to the party's movement roll by the class. */
    public int movementBonus() {
        return effects.intValue("movement.bonus");
    }

    /** Damage taken is reduced by this much when the trainer is hit (class features). */
    public int damageReduction() {
        return effects.intValue("damage.trainerReductionFlat");
    }
}
