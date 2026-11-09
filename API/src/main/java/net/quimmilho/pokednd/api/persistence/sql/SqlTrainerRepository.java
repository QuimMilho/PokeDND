/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.sql;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.quimmilho.pokednd.api.domain.trainers.TrainerRepository;
import net.quimmilho.pokednd.api.persistence.sql.TrainerTables.TrainerRow;

/** The SQL adapter of the trainers (chapter 16.3). */
@ApplicationScoped
public class SqlTrainerRepository implements TrainerRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void create(StoredTrainer t) {
        TrainerRow row = new TrainerRow();
        row.id = t.id();
        row.gameId = t.gameId();
        row.userId = t.userId();
        row.createdAt = t.createdAt();
        copySheet(t, row);
        try {
            em.persist(row);
            em.flush();
        } catch (PersistenceException e) {
            throw translate(e, t);
        }
    }

    @Override
    @Transactional
    public Optional<StoredTrainer> find(UUID id) {
        return Optional.ofNullable(em.find(TrainerRow.class, id)).map(SqlTrainerRepository::toStored);
    }

    @Override
    @Transactional
    public Optional<StoredTrainer> findByUser(UUID gameId, UUID userId) {
        return em.createQuery("from TrainerRow t where t.gameId = :g and t.userId = :u", TrainerRow.class).setParameter("g", gameId)
                .setParameter("u", userId).getResultStream().findFirst().map(SqlTrainerRepository::toStored);
    }

    @Override
    @Transactional
    public List<StoredTrainer> ofGame(UUID gameId) {
        return em.createQuery("from TrainerRow t where t.gameId = :g order by t.createdAt, t.id", TrainerRow.class).setParameter("g", gameId)
                .getResultList().stream().map(SqlTrainerRepository::toStored).toList();
    }

    @Override
    @Transactional
    public boolean replaceSheet(StoredTrainer t) {
        TrainerRow row = em.find(TrainerRow.class, t.id());
        if (row == null) {
            return false;
        }
        copySheet(t, row);
        try {
            em.flush();
        } catch (PersistenceException e) {
            throw translate(e, t);
        }
        return true;
    }

    @Override
    @Transactional
    public void setSkin(UUID id, String model, String source, String username, String sha256, Instant at) {
        TrainerRow row = em.find(TrainerRow.class, id);
        if (row != null) {
            row.skinModel = model;
            row.skinSource = source;
            row.skinUsername = username;
            row.skinSha256 = sha256;
            row.skinAt = at;
            row.updatedAt = Instant.now();
        }
    }

    @Override
    @Transactional
    public void clearSkin(UUID id) {
        setSkin(id, null, null, null, null, null);
    }

    @Override
    @Transactional
    public boolean delete(UUID id) {
        int n = em.createQuery("delete from TrainerRow t where t.id = :id").setParameter("id", id).executeUpdate();
        em.clear();
        return n == 1;
    }

    private static void copySheet(StoredTrainer t, TrainerRow row) {
        row.name = t.name();
        row.nameKey = t.name().toLowerCase(Locale.ROOT);
        row.classId = t.classId();
        row.level = t.level();
        row.xp = t.xp();
        row.abilityScores = t.abilityScores();
        row.skills = t.skills();
        row.hp = t.hp();
        row.hpMax = t.hpMax();
        row.money = t.money();
        row.items = t.items();
        row.scoreMethod = t.scoreMethod();
        row.starterSpecies = t.starterSpecies();
        row.starterLevel = t.starterLevel();
        row.updatedAt = t.updatedAt();
    }

    /** Names the failure by the constraint that refused the write. The transaction is aborted after this. */
    private static RuntimeException translate(PersistenceException e, StoredTrainer t) {
        String text = describe(e);
        if (text.contains("trainer_game_name_unique")) {
            return new NameTakenException(t.name());
        }
        if (text.contains("trainer_game_user_unique")) {
            return new TrainerExistsException();
        }
        return e;
    }

    private static String describe(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            sb.append(t.getMessage()).append(' ');
            if (t instanceof org.hibernate.exception.ConstraintViolationException c && c.getConstraintName() != null) {
                sb.append(c.getConstraintName()).append(' ');
            }
        }
        return sb.toString();
    }

    private static StoredTrainer toStored(TrainerRow r) {
        return new StoredTrainer(r.id, r.gameId, r.userId, r.name, r.classId, r.level, r.xp, r.abilityScores, r.skills, r.hp, r.hpMax, r.money,
                r.items, r.scoreMethod, r.starterSpecies, r.starterLevel, r.skinModel, r.skinSource, r.skinUsername, r.skinSha256, r.skinAt,
                r.createdAt, r.updatedAt);
    }
}
