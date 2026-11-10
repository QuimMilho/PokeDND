/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * One bonus or penalty that was already counted in a roll request (chapter 6.6). Modifiers change the <em>chance</em> first and the die
 * is chosen afterwards, so a modifier on a request is information for the player ("why is it a d20 and not a d10?"); it is not applied
 * to the faces again.
 *
 * @param kind  how the modifier works (see {@link Kind})
 * @param label what it is, in a few words: {@code command bonus}, {@code accuracy stage}, {@code Dexterity}
 * @param value the amount: a number of stages, percentage points, a multiplier or a number added to the roll
 */
public record RollModifier(Kind kind, String label, double value) {

    /** The ways a modifier is applied (the table in chapter 6.6). */
    public enum Kind {
        /** Accuracy or evasion stages: multiply the move's accuracy ({@code −1 accuracy stage}). */
        STAGE("stage"),
        /** Percentage points added to a chance: command bonus, class features, flee bonuses ({@code +4 pp command bonus}). */
        POINTS("points"),
        /** A multiplier of a chance: catch bonuses ({@code ×1.25 ball}). */
        MULTIPLIER("multiplier"),
        /** A number added to a classic d20 roll ({@code +3 Dexterity}). */
        ADD("add");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        @JsonValue
        public String id() {
            return id;
        }

        @JsonCreator
        public static Kind fromId(String id) {
            for (Kind k : values()) {
                if (k.id.equals(id)) {
                    return k;
                }
            }
            throw new IllegalArgumentException("Unknown modifier kind \"" + id + "\"");
        }
    }

    public RollModifier {
        if (kind == null) {
            throw new IllegalArgumentException("A modifier needs a kind");
        }
        label = label == null ? "" : label.strip();
    }

    /** The modifier as one short piece of text for the screen: {@code +4 pp command bonus}, {@code −1 accuracy stage}, {@code ×1.25 ball}. */
    public String text() {
        String amount = switch (kind) {
            case STAGE, ADD -> signed(value);
            case POINTS -> signed(value) + " pp";
            case MULTIPLIER -> "×" + number(value);
        };
        return label.isEmpty() ? amount : amount + " " + label;
    }

    private static String signed(double v) {
        return (v < 0 ? "−" : "+") + number(Math.abs(v));
    }

    private static String number(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : Double.toString(Math.round(v * 100.0) / 100.0);
    }
}
