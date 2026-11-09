/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** The rules for a trainer's name (chapter 3.6): the fake player's name must be a valid Minecraft username. */
public final class TrainerNames {

    /** The shortest name. */
    public static final int MIN_LENGTH = 3;
    /** The longest name. */
    public static final int MAX_LENGTH = 16;
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_]+");

    private TrainerNames() {
    }

    /** Checks the shape of a name: 3 to 16 characters, letters, digits and underscores only. */
    public static List<TrainerProblem> check(String name) {
        List<TrainerProblem> problems = new ArrayList<>();
        if (name == null || name.isEmpty()) {
            problems.add(new TrainerProblem("name_missing", "name", "The trainer needs a name."));
            return problems;
        }
        if (name.length() < MIN_LENGTH || name.length() > MAX_LENGTH) {
            problems.add(new TrainerProblem("name_length", "name",
                    "The name must be " + MIN_LENGTH + " to " + MAX_LENGTH + " characters long, not " + name.length() + "."));
        }
        if (!ALLOWED.matcher(name).matches()) {
            problems.add(new TrainerProblem("name_characters", "name",
                    "The name may only use letters, digits and underscores (like a Minecraft username)."));
        }
        return problems;
    }

    /** Checks the shape and that no other trainer of the game has the same name (ignoring upper and lower case). */
    public static List<TrainerProblem> check(String name, Collection<String> namesInGame) {
        List<TrainerProblem> problems = check(name);
        if (problems.isEmpty() && namesInGame.stream().anyMatch(n -> n.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)))) {
            problems.add(new TrainerProblem("name_taken", "name", "Another trainer in this game is already called " + name + "."));
        }
        return problems;
    }
}
