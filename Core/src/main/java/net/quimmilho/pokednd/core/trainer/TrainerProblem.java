/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

/**
 * One reason a trainer is refused, in plain words.
 *
 * @param code    a short stable code (for example {@code too_many_points}) that the app can translate
 * @param field   what it is about: {@code name}, {@code class}, {@code scores}, {@code skills}, {@code improvement}, ...
 * @param message a readable English sentence
 */
public record TrainerProblem(String code, String field, String message) {

    @Override
    public String toString() {
        return message;
    }
}
