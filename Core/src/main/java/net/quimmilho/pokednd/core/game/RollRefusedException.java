/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

/**
 * {@link RollDesk} refuses a human action on a roll: the wrong person, a way of answering the game does not allow, a value that does
 * not fit the dice. The message is a plain sentence that can be shown to the person. Nothing has been changed when this is thrown.
 */
public class RollRefusedException extends RuntimeException {

    public RollRefusedException(String message) {
        super(message);
    }
}
