/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import java.util.List;

/**
 * One skill (chapter 4.1). The list is data and can be edited per game ({@code trainer.skills.set}).
 *
 * @param id      the identifier, for example {@code pokemon_handling}
 * @param name    the name shown to players
 * @param ability the ability the skill is tied to
 */
public record SkillDef(String id, String name, Ability ability) {

    /** The id of the skill that gives the trainer's bonuses to their Pokémon (chapter 4.2). */
    public static final String POKEMON_HANDLING = "pokemon_handling";

    public SkillDef {
        if (id == null || !id.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("A skill id uses lowercase letters, digits and underscores, not " + id);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("The skill " + id + " needs a name");
        }
    }

    /** The seventeen skills of chapter 4.1. */
    public static List<SkillDef> defaults() {
        return List.of(
                new SkillDef("athletics", "Athletics", Ability.STR),
                new SkillDef("acrobatics", "Acrobatics", Ability.DEX),
                new SkillDef("sleight_of_hand", "Sleight of Hand", Ability.DEX),
                new SkillDef("stealth", "Stealth", Ability.DEX),
                new SkillDef("investigation", "Investigation", Ability.INT),
                new SkillDef("nature", "Nature", Ability.INT),
                new SkillDef("pokemon_lore", "Pokémon Lore", Ability.INT),
                new SkillDef("appraisal", "Appraisal", Ability.INT),
                new SkillDef("insight", "Insight", Ability.WIS),
                new SkillDef("medicine", "Medicine", Ability.WIS),
                new SkillDef("perception", "Perception", Ability.WIS),
                new SkillDef("survival", "Survival", Ability.WIS),
                new SkillDef(POKEMON_HANDLING, "Pokémon Handling", Ability.WIS),
                new SkillDef("deception", "Deception", Ability.CHA),
                new SkillDef("intimidation", "Intimidation", Ability.CHA),
                new SkillDef("performance", "Performance", Ability.CHA),
                new SkillDef("persuasion", "Persuasion", Ability.CHA));
    }
}
