/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

/**
 * A person asked for something in a battle that is not allowed (it is not their Pokémon, the move has no PP, the time is up, a setting
 * forbids it). The message is a plain sentence that can be shown to the person. Nothing has been changed when this is thrown.
 */
public class BattleRefusedException extends RuntimeException {

    public BattleRefusedException(String message) {
        super(message);
    }
}
