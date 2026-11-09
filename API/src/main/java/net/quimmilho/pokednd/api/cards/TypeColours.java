/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import java.awt.Color;
import java.util.Map;

/** The type colours of chapter 9.7 (<b>Suggested</b>), and ways to make lighter and darker versions of them. */
public final class TypeColours {

    private static final Map<String, Color> COLOURS = Map.ofEntries(
            Map.entry("normal", Color.decode("#A8A77A")), Map.entry("fire", Color.decode("#EE8130")),
            Map.entry("water", Color.decode("#6390F0")), Map.entry("electric", Color.decode("#F7D02C")),
            Map.entry("grass", Color.decode("#7AC74C")), Map.entry("ice", Color.decode("#96D9D6")),
            Map.entry("fighting", Color.decode("#C22E28")), Map.entry("poison", Color.decode("#A33EA1")),
            Map.entry("ground", Color.decode("#E2BF65")), Map.entry("flying", Color.decode("#A98FF3")),
            Map.entry("psychic", Color.decode("#F95587")), Map.entry("bug", Color.decode("#A6B91A")),
            Map.entry("rock", Color.decode("#B6A136")), Map.entry("ghost", Color.decode("#735797")),
            Map.entry("dragon", Color.decode("#6F35FC")), Map.entry("dark", Color.decode("#705746")),
            Map.entry("steel", Color.decode("#B7B7CE")), Map.entry("fairy", Color.decode("#D685AD")));

    private TypeColours() {
    }

    public static Color of(String typeId) {
        return COLOURS.getOrDefault(typeId, COLOURS.get("normal"));
    }

    /** Mixes {@code c} with white: {@code amount} 0 gives {@code c}, 1 gives white. */
    public static Color lighten(Color c, double amount) {
        return mix(c, Color.WHITE, amount);
    }

    /** Mixes {@code c} with black: {@code amount} 0 gives {@code c}, 1 gives black. */
    public static Color darken(Color c, double amount) {
        return mix(c, Color.BLACK, amount);
    }

    private static Color mix(Color a, Color b, double t) {
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
