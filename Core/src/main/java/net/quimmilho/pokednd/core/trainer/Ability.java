/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** The six ability scores of a trainer (chapter 4.1). */
public enum Ability {
    /** Athletics; carrying; Hiker features. */
    STR,
    /** Movement roll, Armor Class, Acrobatics, Stealth. */
    DEX,
    /** Trainer HP. */
    CON,
    /** Pokémon Lore, Nature, Investigation, Appraisal. */
    INT,
    /** Pokémon Handling, Medicine, Perception, Survival, Insight. */
    WIS,
    /** Persuasion, Performance, Intimidation, Deception; shop prices. */
    CHA;

    /** The short name written in data files, for example {@code dex}. */
    @JsonValue
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The full name for messages, for example {@code Dexterity}. */
    public String label() {
        return switch (this) {
            case STR -> "Strength";
            case DEX -> "Dexterity";
            case CON -> "Constitution";
            case INT -> "Intelligence";
            case WIS -> "Wisdom";
            case CHA -> "Charisma";
        };
    }

    /** Reads {@code dex}, {@code DEX} or {@code Dex}. */
    @JsonCreator
    public static Ability parse(String text) {
        String t = text.strip().toUpperCase(Locale.ROOT);
        for (Ability a : values()) {
            if (a.name().equals(t)) {
                return a;
            }
        }
        throw new IllegalArgumentException("Unknown ability " + text + " (use str, dex, con, int, wis or cha)");
    }

    /** The modifier of a score: {@code floor((score − 10) / 2)}. */
    public static int modifier(int score) {
        return Math.floorDiv(score - 10, 2);
    }
}
