/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.mongo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.quimmilho.pokednd.api.domain.trainers.TrainerRepository;
import net.quimmilho.pokednd.api.persistence.mongo.MongoDocs.TrainerRow;
import org.bson.conversions.Bson;

/** The MongoDB adapter of the trainers (chapter 16.3). Two unique indexes keep the rules: one trainer per person, one name per game. */
@ApplicationScoped
public class MongoTrainerRepository implements TrainerRepository {

    private static MongoCollection<TrainerRow> trainers() {
        return TrainerRow.mongoCollection();
    }

    @Override
    public void create(StoredTrainer t) {
        TrainerRow row = new TrainerRow();
        row.id = t.id();
        row.gameId = t.gameId();
        row.userId = t.userId();
        row.createdAt = t.createdAt();
        copySheet(t, row);
        try {
            trainers().insertOne(row);
        } catch (RuntimeException e) {
            throw translate(e, t);
        }
    }

    @Override
    public Optional<StoredTrainer> find(UUID id) {
        return Optional.ofNullable(trainers().find(eq("_id", id)).first()).map(MongoTrainerRepository::toStored);
    }

    @Override
    public Optional<StoredTrainer> findByUser(UUID gameId, UUID userId) {
        return Optional.ofNullable(trainers().find(and(eq("gameId", gameId), eq("userId", userId))).first()).map(MongoTrainerRepository::toStored);
    }

    @Override
    public List<StoredTrainer> ofGame(UUID gameId) {
        return MongoSupport.list(trainers().find(eq("gameId", gameId)).sort(MongoSupport.oldestFirst())).stream()
                .map(MongoTrainerRepository::toStored).toList();
    }

    @Override
    public boolean replaceSheet(StoredTrainer t) {
        List<Bson> sets = new ArrayList<>();
        sets.add(Updates.set("name", t.name()));
        sets.add(Updates.set("nameKey", t.name().toLowerCase(Locale.ROOT)));
        sets.add(Updates.set("classId", t.classId()));
        sets.add(Updates.set("level", t.level()));
        sets.add(Updates.set("xp", t.xp()));
        sets.add(Updates.set("abilityScores", MongoJson.toDocument(t.abilityScores())));
        sets.add(Updates.set("skills", MongoJson.toDocument(t.skills())));
        sets.add(Updates.set("hp", t.hp()));
        sets.add(Updates.set("hpMax", t.hpMax()));
        sets.add(Updates.set("money", t.money()));
        sets.add(Updates.set("items", MongoJson.toDocument(t.items())));
        sets.add(optional("scoreMethod", t.scoreMethod()));
        sets.add(optional("starterSpecies", t.starterSpecies()));
        sets.add(optional("starterLevel", t.starterLevel()));
        sets.add(Updates.set("updatedAt", t.updatedAt()));
        try {
            return MongoSupport.changedOne(trainers().updateOne(eq("_id", t.id()), Updates.combine(sets)));
        } catch (RuntimeException e) {
            throw translate(e, t);
        }
    }

    @Override
    public void setSkin(UUID id, String model, String source, String username, String sha256, Instant at) {
        trainers().updateOne(eq("_id", id), Updates.combine(optional("skinModel", model), optional("skinSource", source),
                optional("skinUsername", username), optional("skinSha256", sha256), optional("skinAt", at), Updates.set("updatedAt", Instant.now())));
    }

    @Override
    public void clearSkin(UUID id) {
        setSkin(id, null, null, null, null, null);
    }

    @Override
    public boolean delete(UUID id) {
        return trainers().deleteOne(eq("_id", id)).getDeletedCount() == 1;
    }

    private static Bson optional(String field, Object value) {
        return value == null ? Updates.unset(field) : Updates.set(field, value);
    }

    private static void copySheet(StoredTrainer t, TrainerRow row) {
        row.name = t.name();
        row.nameKey = t.name().toLowerCase(Locale.ROOT);
        row.classId = t.classId();
        row.level = t.level();
        row.xp = t.xp();
        row.abilityScores = MongoJson.toDocument(t.abilityScores());
        row.skills = MongoJson.toDocument(t.skills());
        row.hp = t.hp();
        row.hpMax = t.hpMax();
        row.money = t.money();
        row.items = MongoJson.toDocument(t.items());
        row.scoreMethod = t.scoreMethod();
        row.starterSpecies = t.starterSpecies();
        row.starterLevel = t.starterLevel();
        row.updatedAt = t.updatedAt();
    }

    /** Names the failure the way the SQL adapter does, by the index that refused the write. */
    private static RuntimeException translate(RuntimeException e, StoredTrainer t) {
        if (MongoSupport.isDuplicate(e)) {
            String text = MongoSupport.text(e);
            if (text.contains("trainer_game_name_unique")) {
                return new NameTakenException(t.name());
            }
            if (text.contains("trainer_game_user_unique")) {
                return new TrainerExistsException();
            }
        }
        return e;
    }

    private static StoredTrainer toStored(TrainerRow r) {
        return new StoredTrainer(r.id, r.gameId, r.userId, r.name, r.classId, r.level, r.xp, MongoJson.toJson(r.abilityScores), MongoJson.toJson(r.skills),
                r.hp, r.hpMax, r.money, MongoJson.toJson(r.items), r.scoreMethod, r.starterSpecies, r.starterLevel, r.skinModel, r.skinSource,
                r.skinUsername, r.skinSha256, r.skinAt, r.createdAt, r.updatedAt);
    }
}
