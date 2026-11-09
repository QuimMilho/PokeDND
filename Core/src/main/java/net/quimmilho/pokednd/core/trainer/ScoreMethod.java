/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import java.util.EnumSet;
import java.util.Set;

/** How ability scores are made (chapter 4.3). */
public enum ScoreMethod {
    /** 27 points, scores start at 8, at most 15. The default. */
    POINT_BUY("pointBuy"),
    /** 15, 14, 13, 12, 10, 8 assigned as the player likes. */
    STANDARD_ARRAY("standardArray"),
    /** Roll four d6 and drop the lowest, six times. */
    ROLL_4D6_DROP_LOWEST("roll4d6DropLowest");

    private final String id;

    ScoreMethod(String id) {
        this.id = id;
    }

    /** The value written in the settings and the API. */
    public String id() {
        return id;
    }

    /** Reads one method, ignoring case. */
    public static ScoreMethod parse(String text) {
        for (ScoreMethod m : values()) {
            if (m.id.equalsIgnoreCase(text.strip())) {
                return m;
            }
        }
        throw new IllegalArgumentException("An ability score method is pointBuy, standardArray or roll4d6DropLowest, not " + text);
    }

    /** Reads {@code trainer.abilityScores.method}: one method, or {@code any} for all three. */
    public static Set<ScoreMethod> parseAllowed(String text) {
        if (text.strip().equalsIgnoreCase("any")) {
            return EnumSet.allOf(ScoreMethod.class);
        }
        return EnumSet.of(parse(text));
    }

    /** The value of {@code trainer.abilityScores.method} for a set of allowed methods. */
    public static String allowedText(Set<ScoreMethod> allowed) {
        if (allowed.size() == values().length) {
            return "any";
        }
        if (allowed.size() == 1) {
            return allowed.iterator().next().id;
        }
        throw new IllegalArgumentException("trainer.abilityScores.method is one method or any");
    }
}
