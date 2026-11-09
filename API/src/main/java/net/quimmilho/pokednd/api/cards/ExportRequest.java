/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.cards;

import java.util.List;

/**
 * What a person asks for when exporting species cards (chapter 9.6). Everything is optional except that the selection must pick
 * at least one card.
 *
 * <p><b>The selection</b> (<i>Suggested</i> rule): the cards that match the generation filter <i>and</i> the type filter (a filter that is
 * left out does not restrict), plus every card named in {@code cards}.
 *
 * @param generations   National Dex generations, 1 to 9
 * @param types         type ids; a card matches if either of its Pokémon's types is listed
 * @param primaryTypeOnly match only the first type
 * @param cards         card keys such as {@code 0006_charizard} or {@code 0105_marowak_alolan}: the manual selection
 * @param includeForms  include alternate forms (default true)
 * @param backs         also produce the backs, mirrored for double-sided printing
 * @param size          card size such as {@code 63x88} (default from the settings)
 * @param paper         {@code A4} or {@code LETTER}
 * @param language      {@code en} or {@code pt}
 * @param colour        {@code full} (default) or {@code ink-saving}
 * @param copies        copies of each card (1 to 20)
 * @param showBackId    print the card ID under the QR code on the back
 * @param rulesetId     the rule set to take dice from; only the default rule set exists until phase 33
 * @param bleedMm       bleed in millimetres, 0 to 10 (default from the settings, 3). <b>Suggested</b>: the export screen offers it (phase 27)
 * @param gapMm         gap between the cards in millimetres, 0 to 20 (default from the settings, 2). <b>Suggested</b>, like {@code bleedMm}
 */
public record ExportRequest(List<Integer> generations, List<String> types, Boolean primaryTypeOnly, List<String> cards, Boolean includeForms,
                            Boolean backs, String size, String paper, String language, String colour, Integer copies, Boolean showBackId,
                            String rulesetId, Double bleedMm, Double gapMm) {
}
