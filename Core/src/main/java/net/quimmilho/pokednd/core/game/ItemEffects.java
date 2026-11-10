/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

import net.quimmilho.pokednd.core.game.Battle.Status;

/** Small pure rules about what items do, shared by the bag ({@link BagDesk}) and the battle ({@link MoveResolver}). */
final class ItemEffects {

    private ItemEffects() {
    }

    /**
     * True if a status heal with this {@code cure_status} detail ({@code poison}, {@code burn}, {@code freeze}, {@code paralysis},
     * {@code sleep} or {@code all}) cures this status.
     */
    static boolean cures(String detail, Status status) {
        if (status == Status.NONE) {
            return false;
        }
        return switch (detail) {
            case "all" -> true;
            case "poison" -> status == Status.POISON || status == Status.TOXIC;
            case "burn" -> status == Status.BURN;
            case "freeze" -> status == Status.FREEZE;
            case "paralysis" -> status == Status.PARALYSIS;
            case "sleep" -> status == Status.SLEEP;
            default -> false;
        };
    }

    /** The id of a stat stage as the battle writes it ({@code special_attack} → {@code spa}). */
    static String stageId(String stat) {
        return switch (stat) {
            case "attack" -> "atk";
            case "defense" -> "def";
            case "special_attack" -> "spa";
            case "special_defense" -> "spd";
            case "speed" -> "spe";
            case "accuracy" -> "accuracy";
            case "evasion" -> "evasion";
            default -> stat;
        };
    }
}
