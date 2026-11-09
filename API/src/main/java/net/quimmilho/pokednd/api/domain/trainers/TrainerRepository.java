/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.domain.trainers;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where trainers are kept (chapter 17.2: Trainer, Skin). The SQL and the MongoDB adapter implement it. */
public interface TrainerRepository {

    /**
     * A trainer as it is stored. The rules (Armor Class, skill bonuses, features) are worked out from it by Core; only the choices and the
     * numbers that change during play are stored.
     *
     * @param abilityScores  a JSON object, for example {@code {"str": 15, "dex": 14, ...}}
     * @param skills         a JSON array of the skill ids the player picked
     * @param items          a JSON object of item id to count
     * @param scoreMethod    how the scores were made ({@code pointBuy}, {@code standardArray}, {@code roll4d6DropLowest})
     * @param starterSpecies the starter Pokémon the player chose (the Pokémon rows come with the battle phases), or null if the admin assigns it
     * @param skinModel      {@code classic} or {@code slim}; null until a skin is set
     * @param skinSource     {@code upload} or {@code username}
     * @param skinSha256     the hash of the stored PNG
     */
    record StoredTrainer(UUID id, UUID gameId, UUID userId, String name, String classId, int level, long xp, JsonNode abilityScores, JsonNode skills,
                         int hp, int hpMax, long money, JsonNode items, String scoreMethod, String starterSpecies, Integer starterLevel,
                         String skinModel, String skinSource, String skinUsername, String skinSha256, Instant skinAt, Instant createdAt,
                         Instant updatedAt) {

        public boolean hasSkin() {
            return skinSha256 != null;
        }
    }

    /** Raised when another trainer of the game has the same name (whatever the case). */
    class NameTakenException extends RuntimeException {
        public NameTakenException(String name) {
            super("The trainer name is taken: " + name);
        }
    }

    /** Raised when the person has a trainer in this game already. */
    class TrainerExistsException extends RuntimeException {
        public TrainerExistsException() {
            super("The person has a trainer in this game already");
        }
    }

    /**
     * Adds a trainer.
     *
     * @throws NameTakenException     if the name is in use in the game
     * @throws TrainerExistsException if the person has a trainer in the game
     */
    void create(StoredTrainer trainer);

    Optional<StoredTrainer> find(UUID id);

    Optional<StoredTrainer> findByUser(UUID gameId, UUID userId);

    /** The trainers of a game, oldest first. */
    List<StoredTrainer> ofGame(UUID gameId);

    /**
     * Replaces the sheet of a trainer: name, class, level, XP, scores, skills, HP, money, bag, method and starter. The skin is not touched.
     *
     * @return false if the trainer does not exist
     * @throws NameTakenException if the name is in use by another trainer of the game
     */
    boolean replaceSheet(StoredTrainer trainer);

    /** Records the skin of a trainer. */
    void setSkin(UUID id, String model, String source, String username, String sha256, Instant at);

    /** Removes the skin record (the file is removed by the caller). */
    void clearSkin(UUID id);

    /** Deletes the trainer. Returns false if there was none. */
    boolean delete(UUID id);
}
