/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.trainer;

import java.util.List;
import java.util.stream.Collectors;

/** Thrown when a trainer breaks the rules of chapter 4. The message lists every problem. */
public class InvalidTrainerException extends IllegalArgumentException {

    private final transient List<TrainerProblem> problems;

    public InvalidTrainerException(List<TrainerProblem> problems) {
        super(problems.stream().map(TrainerProblem::message).collect(Collectors.joining(" ")));
        this.problems = List.copyOf(problems);
    }

    /** Every problem found. */
    public List<TrainerProblem> problems() {
        return problems;
    }
}
