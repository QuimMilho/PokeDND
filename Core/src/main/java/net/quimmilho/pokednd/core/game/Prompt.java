/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.core.game;

/**
 * Something that waits for an answer after a battle or a level-up (chapter 7.15, step 3): a Pokémon that can learn a move but knows four,
 * a Pokémon that is ready to evolve, an evolution only the admin can confirm, or the news that a trainer whited out. It stays in
 * {@link GameState#prompts()} until {@link ProgressDesk#answer} answers it.
 *
 * @param id        the id of the prompt, unique in a game
 * @param kind      what is asked
 * @param trainerId whose it is (who answers; the admin can always answer)
 * @param pokemonId the Pokémon it is about (null for a whiteout)
 * @param data      the move to learn, or the species it would evolve into
 * @param text      the sentence for the screen
 */
public record Prompt(String id, Kind kind, String trainerId, String pokemonId, String data, String text) {

    /** What is asked. */
    public enum Kind {
        /** "Charmander wants to learn Slash, but it knows four moves. Forget one?" Answer: the place (0 to 3) of the move to forget, or {@code skip}. */
        LEARN_MOVE,
        /** "Your Pokémon is evolving! Evolve / Not now". Answer: {@code yes} or {@code no}. */
        EVOLVE,
        /** An evolution whose condition only the admin can judge (a full moon, Gimmighoul's coins). Answer: {@code yes} or {@code no}, from the admin. */
        ADMIN_EVOLVE,
        /** Every Pokémon of the trainer fainted: they lost some ₽, the team was healed, and they go back to the last town. Answer: {@code ok}. */
        WHITEOUT
    }

    public Prompt {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A prompt needs an id");
        }
        if (kind == null) {
            throw new IllegalArgumentException("A prompt needs a kind");
        }
        text = text == null ? "" : text;
    }
}
