/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.settings;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * One setting of Appendix A: what kind of value it is, what it is when nobody changed it, and what it may be changed to. Read from
 * {@code settings/registry.json}, which {@code tools/gen-settings.mjs} writes from the document.
 *
 * @param defaultValue  the value when no layer sets it; JSON null for the settings whose default is data that ships with the game
 * @param defaultText   the default exactly as Appendix A writes it
 * @param allowed       the values an enum may have, or the tokens of a list; null if anything fits
 */
public record SettingDef(String key, String group, Type type, JsonNode defaultValue, String defaultText, int chapter, String description, List<String> allowed,
                         Double min, Double max, String pattern, String patternHint, String tokenPattern, String tokenHint, Integer minItems,
                         Integer maxItems, Double itemMin, Double itemMax, boolean unique, Integer minLength, Integer maxLength) {

    /** The kinds of value. In JSON a bool is a boolean, a number is a number, and a csv is text such as {@code "a,b,c"}. */
    public enum Type {
        BOOL("bool"),
        INT("int"),
        DECIMAL("decimal"),
        /** One of {@code allowed}. */
        ENUM("enum"),
        STRING("string"),
        /** Text of comma-separated tokens: {@code "playerSubmit,digital"}. */
        CSV("csv"),
        /** A JSON array of whole numbers (a comma-separated text is accepted and turned into one). */
        INT_LIST("intList"),
        /** A JSON object (for example the die forced for each kind of roll). */
        JSON("json"),
        /** Data that comes with the game (tables, class lists): any JSON object, array or text. */
        DATA("data");

        private final String id;

        Type(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Type ofId(String id) {
            for (Type t : values()) {
                if (t.id.equals(id)) {
                    return t;
                }
            }
            throw new IllegalArgumentException("Unknown setting type: " + id);
        }
    }
}
