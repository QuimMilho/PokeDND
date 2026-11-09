/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import java.nio.file.Path;
import java.util.List;

/**
 * Everything printed on the front of one species card (chapter 9.2), already in the card's language.
 *
 * @param key         the card key: four-digit Dex number, underscore, species id (base form) or form id (other forms)
 * @param qrId        the opaque ID in the QR code on the back
 * @param name        display name, with the form in brackets for alternate forms
 * @param types       type ids, primary first (the primary one colours the frame)
 * @param typeNames   the same, in the card's language
 * @param stage       the stage badge text (Basic, Stage 1, Stage 2)
 * @param artFile     the card art, or the icon when there is no art; null when there is neither
 * @param artIsIcon   true if {@code artFile} is the 128 px icon
 * @param warnings    problems found while collecting the data (missing art, fewer than four moves…)
 */
public record CardData(String key, String qrId, int dex, String speciesId, String formId, String name, List<String> types,
                       List<String> typeNames, String stage, int hp, Stats stats, List<MoveLine> moves, List<String> abilities,
                       String hiddenAbility, List<Multiplier> weaknesses, List<Multiplier> resistances, List<String> immunities,
                       String flavour, Path artFile, boolean artIsIcon, List<String> warnings) {

    /** The five stats shown under HP. */
    public record Stats(int attack, int defense, int specialAttack, int specialDefense, int speed) {
    }

    /**
     * One of the four moves.
     *
     * @param typeId   for the colour of the type dot
     * @param category {@code physical}, {@code special} or {@code status}
     * @param dice     the damage dice ({@code 2d8}) or a word for moves that do not use dice, or a dash
     * @param hit      the hit roll for a normal case ({@code d20 · hit 5–19 · crit 20})
     * @param effect   a short effect line
     */
    public record MoveLine(String name, String typeId, String category, String dice, String hit, String effect, int pp) {
    }

    /** A type and how much damage it does to this card's Pokémon ({@code 2} is double, {@code 0.5} half). */
    public record Multiplier(String typeId, String typeName, double factor) {
    }

    public String primaryType() {
        return types.isEmpty() ? "normal" : types.get(0);
    }
}
