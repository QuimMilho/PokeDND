/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A trainer class (chapter 4.4). Classes are data: admins can edit or delete the defaults and add their own per game.
 *
 * @param id             the identifier, for example {@code ace_trainer}
 * @param name           the name shown to players
 * @param description    one line of flavour
 * @param hitDie         the sides of the hit die: 4, 6, 8, 10 or 12
 * @param primaryAbility a hint for the creation screen
 * @param saves          the two saving throws the class is proficient in
 * @param skillChoices   how many skills to pick and from which
 * @param acBonus        extra Armor Class
 * @param startingMoney  extra ₽ on top of the common kit
 * @param startingItems  extra items on top of the common kit (item id to count)
 * @param features       what the class gives at each level
 */
public record TrainerClass(String id, String name, String description, int hitDie, Ability primaryAbility, List<Ability> saves,
                           SkillChoices skillChoices, int acBonus, int startingMoney, Map<String, Integer> startingItems,
                           List<ClassFeature> features) {

    /**
     * The skills a trainer can pick from.
     *
     * @param pick how many to pick (2 for the default classes)
     * @param from the skill ids to pick from
     */
    public record SkillChoices(int pick, List<String> from) {
        public SkillChoices {
            from = from == null ? List.of() : List.copyOf(from);
            if (pick < 0 || pick > from.size()) {
                throw new IllegalArgumentException("A class picks 0 to " + from.size() + " skills, not " + pick);
            }
        }
    }

    public TrainerClass {
        if (id == null || !id.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("A class id uses lowercase letters, digits and underscores, not " + id);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("The class " + id + " needs a name");
        }
        description = description == null ? "" : description;
        if (!Set.of(4, 6, 8, 10, 12).contains(hitDie)) {
            throw new IllegalArgumentException("The hit die of " + id + " is d4, d6, d8, d10 or d12, not d" + hitDie);
        }
        if (primaryAbility == null) {
            throw new IllegalArgumentException("The class " + id + " needs a primary ability");
        }
        saves = saves == null ? List.of() : List.copyOf(saves);
        if (saves.size() != 2 || saves.get(0) == saves.get(1)) {
            throw new IllegalArgumentException("The class " + id + " needs two different saving throws");
        }
        if (skillChoices == null) {
            throw new IllegalArgumentException("The class " + id + " needs skillChoices");
        }
        startingItems = startingItems == null ? Map.of() : Map.copyOf(startingItems);
        startingItems.forEach((item, n) -> {
            if (n < 1) {
                throw new IllegalArgumentException("The starting item " + item + " of " + id + " needs a count of at least 1");
            }
        });
        if (startingMoney < 0) {
            throw new IllegalArgumentException("The starting money of " + id + " cannot be negative");
        }
        features = features == null ? List.of() : features.stream().sorted(Comparator.comparingInt(ClassFeature::level)).toList();
    }

    /** The features a trainer of this level has: every feature up to and including the level. */
    public List<ClassFeature> featuresUpTo(int level) {
        return features.stream().filter(f -> f.level() <= level).toList();
    }

    /** The features that arrive exactly at this level. */
    public List<ClassFeature> featuresAt(int level) {
        return features.stream().filter(f -> f.level() == level).toList();
    }

    /** The hit die as text, for example {@code d8}. */
    public String hitDieText() {
        return "d" + hitDie;
    }
}
