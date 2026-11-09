/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.quimmilho.pokednd.core.dice.Dice;
import net.quimmilho.pokednd.core.dice.DiceChoice;
import net.quimmilho.pokednd.core.game.GameEvent.RollRequested;
import net.quimmilho.pokednd.core.game.GameState.Band;
import net.quimmilho.pokednd.core.game.GameState.RollClass;
import net.quimmilho.pokednd.core.game.GameState.Roller;

/**
 * Builds the {@link RollRequested} events that the rules ask for (chapter 6.7) and the sentence that goes with them, so that the app and
 * the Minecraft screen show the same text: {@code Roll 1d20 for Scratch · 1–19 hit · 20 crit · chance 80 % (+4 pp command bonus,
 * −1 accuracy stage)}.
 *
 * <p>These are the <em>asking</em> half. {@link RollDesk} decides who answers (settings) and turns the human actions into events.
 */
public final class RollRequests {

    private RollRequests() {
    }

    /**
     * A smart-dice roll (chapter 6.4): the die and the band table come from the {@link DiceChoice}.
     *
     * @param trueChance the exact chance to show in the prompt, 0 to 1 (null for none)
     * @param modifiers  what was counted in the chance, for the explanation
     * @param showChance false hides the chance in the text ({@code dice.prompt.showTrueChance})
     * @throws IllegalArgumentException if the choice needs no roll (a 100 % or 0 % chance)
     */
    public static RollRequested smart(String requestId, String trainerId, String reason, DiceChoice choice, Double trueChance,
                                      List<RollModifier> modifiers, RollClass rollClass, String rollerUserId, boolean showChance) {
        if (!choice.rolled()) {
            throw new IllegalArgumentException("Nothing is rolled for a certain outcome: " + reason);
        }
        // a d100 that the table rolls as two d10 is still one value from 1 to 100: the player types the combined number
        Dice dice = Dice.d(choice.die());
        List<Band> bands = new ArrayList<>();
        for (DiceChoice.Band b : choice.bands()) {
            if (b.faces() > 0) {
                bands.add(new Band(b.label(), b.from(), b.to()));
            }
        }
        String text = explain(dice, reason, bands, trueChance, modifiers, choice.percentileAsTwoD10(), showChance);
        return new RollRequested(requestId, trainerId, dice, reason, Roller.PLAYER, rollerUserId, rollClass, bands, trueChance, modifiers, text, 0);
    }

    /**
     * A classic roll: one d20 plus a modifier against a difficulty (chapter 6.3: skill checks and saving throws). The bands split the die at
     * the number that is needed; with a modifier of +3 against 15, a 12 or more succeeds.
     *
     * @param modifier   what the player adds to the die (ability modifier and proficiency)
     * @param difficulty the number to reach, or null for a roll without a target (initiative)
     */
    public static RollRequested classic(String requestId, String trainerId, String reason, int modifier, Integer difficulty,
                                        List<RollModifier> modifiers, RollClass rollClass, String rollerUserId) {
        Dice dice = Dice.d(20);
        List<Band> bands = new ArrayList<>();
        Double chance = null;
        if (difficulty != null) {
            int need = Math.max(1, Math.min(21, difficulty - modifier));
            if (need > 1) {
                bands.add(new Band("fail", 1, need - 1));
            }
            if (need <= 20) {
                bands.add(new Band("success", need, 20));
            }
            chance = (21 - need) / 20.0;
        }
        List<RollModifier> all = new ArrayList<>(modifiers);
        String text = explain(dice, reason, bands, chance, all, false, true)
                + (modifier == 0 ? "" : " · add " + (modifier > 0 ? "+" : "−") + Math.abs(modifier) + " to the die")
                + (difficulty == null ? "" : " · needs " + difficulty);
        return new RollRequested(requestId, trainerId, dice, reason, Roller.PLAYER, rollerUserId, rollClass, bands, chance, all, text, 0);
    }

    /** The prompt: {@code Roll 1d20 for Scratch · 1–19 hit · 20 crit · chance 80 % (+4 pp command bonus)}. */
    public static String explain(Dice dice, String reason, List<Band> bands, Double trueChance, List<RollModifier> modifiers,
                                 boolean percentileAsTwoD10, boolean showChance) {
        StringBuilder sb = new StringBuilder("Roll ").append(dice.count()).append('d').append(dice.sides());
        if (reason != null && !reason.isBlank()) {
            sb.append(" for ").append(reason.strip());
        }
        if (!bands.isEmpty()) {
            sb.append(" · ").append(bands.stream().map(Band::text).collect(Collectors.joining(" · ")));
        }
        if (showChance && trueChance != null) {
            sb.append(" · chance ").append(DiceChoice.percent(trueChance));
        }
        if (!modifiers.isEmpty()) {
            sb.append(" (").append(modifiers.stream().map(RollModifier::text).collect(Collectors.joining(", "))).append(')');
        }
        if (percentileAsTwoD10) {
            sb.append(" · two d10 can stand in for the d100: tens first, then units, and 00 is 100");
        }
        return sb.toString();
    }
}
