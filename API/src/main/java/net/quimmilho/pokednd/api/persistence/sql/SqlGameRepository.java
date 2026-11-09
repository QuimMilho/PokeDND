/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.sql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.Ids;
import net.quimmilho.pokednd.api.domain.games.GameRepository;
import net.quimmilho.pokednd.api.domain.games.GameState;
import net.quimmilho.pokednd.api.persistence.sql.GameTables.GameAdminRow;
import net.quimmilho.pokednd.api.persistence.sql.GameTables.GameRow;
import net.quimmilho.pokednd.api.persistence.sql.GameTables.MembershipRow;

/** The SQL adapter of the games (chapter 16.3). */
@ApplicationScoped
public class SqlGameRepository implements GameRepository {

    private static final String NOT_ARCHIVED = " and g.state <> 'archived' order by g.createdAt desc, g.id";

    @Inject
    EntityManager em;

    // ------------------------------------------------------------------ games

    @Override
    @Transactional
    public Game create(Game g) {
        GameRow row = new GameRow();
        row.id = g.id();
        row.serverId = g.serverId();
        row.ownerId = g.ownerId();
        row.name = g.name();
        row.state = g.state().id();
        setRuleSet(row, g.ruleSet());
        row.settingsVersion = g.settingsVersion();
        row.settings = g.settings() == null ? JsonNodeFactory.instance.objectNode() : g.settings();
        row.joinCode = g.joinCode();
        row.createdAt = g.createdAt();
        row.updatedAt = g.createdAt();
        em.persist(row);
        em.flush();
        return g;
    }

    @Override
    @Transactional
    public Optional<Game> find(UUID id) {
        return Optional.ofNullable(em.find(GameRow.class, id)).map(SqlGameRepository::toGame);
    }

    @Override
    @Transactional
    public Optional<Game> findByJoinCode(String joinCode) {
        return em.createQuery("from GameRow g where g.joinCode = :c and g.state = 'lobby'", GameRow.class).setParameter("c", joinCode).getResultStream()
                .findFirst().map(SqlGameRepository::toGame);
    }

    @Override
    @Transactional
    public Optional<Game> activeOnServer(UUID serverId) {
        return em.createQuery("from GameRow g where g.serverId = :s and g.state in ('lobby', 'running', 'paused')", GameRow.class)
                .setParameter("s", serverId).getResultStream().findFirst().map(SqlGameRepository::toGame);
    }

    @Override
    @Transactional
    public long countOnServer(UUID serverId) {
        return em.createQuery("select count(g) from GameRow g where g.serverId = :s", Long.class).setParameter("s", serverId).getSingleResult();
    }

    @Override
    @Transactional
    public boolean transition(UUID id, GameState from, GameState to, String joinCode, Instant at) {
        try {
            int n = em.createQuery("update GameRow g set g.state = :to, g.joinCode = :code, g.updatedAt = :t where g.id = :id and g.state = :from")
                    .setParameter("to", to.id()).setParameter("code", joinCode).setParameter("t", at).setParameter("id", id)
                    .setParameter("from", from.id()).executeUpdate();
            em.clear();
            return n == 1;
        } catch (PersistenceException e) {
            String text = describe(e);
            if (text.contains("game_one_active_per_server")) {
                // the database transaction is aborted now, so nothing more can be read here
                throw new ActiveGameExistsException(null);
            }
            if (text.contains("game_join_code_unique")) {
                throw new JoinCodeTakenException(joinCode);
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public boolean rename(UUID id, String name) {
        int n = em.createQuery("update GameRow g set g.name = :n, g.updatedAt = :t where g.id = :id").setParameter("n", name)
                .setParameter("t", Instant.now()).setParameter("id", id).executeUpdate();
        em.clear();
        return n == 1;
    }

    @Override
    @Transactional
    public boolean replaceSettings(UUID id, long expectedVersion, String ruleSet, JsonNode overrides, Instant at) {
        UUID saved = null;
        String builtin = null;
        if (ruleSet != null) {
            if (ruleSet.startsWith("builtin:")) {
                builtin = ruleSet;
            } else {
                saved = UUID.fromString(ruleSet);
            }
        }
        int n = em.createQuery("update GameRow g set g.ruleSetId = :rs, g.builtinRuleSet = :b, g.settings = :s, g.settingsVersion = g.settingsVersion + 1, "
                + "g.updatedAt = :t where g.id = :id and g.settingsVersion = :v").setParameter("rs", saved).setParameter("b", builtin).setParameter("s", overrides)
                .setParameter("t", at).setParameter("id", id).setParameter("v", expectedVersion).executeUpdate();
        em.clear();
        return n == 1;
    }

    @Override
    @Transactional
    public long countUsingRuleSet(UUID ruleSetId) {
        return em.createQuery("select count(g) from GameRow g where g.ruleSetId = :r", Long.class).setParameter("r", ruleSetId).getSingleResult();
    }

    private static void setRuleSet(GameRow row, String ruleSet) {
        row.ruleSetId = null;
        row.builtinRuleSet = null;
        if (ruleSet != null) {
            if (ruleSet.startsWith("builtin:")) {
                row.builtinRuleSet = ruleSet;
            } else {
                row.ruleSetId = UUID.fromString(ruleSet);
            }
        }
    }

    @Override
    @Transactional
    public List<Game> allNotArchived() {
        return em.createQuery("from GameRow g where 1 = 1" + NOT_ARCHIVED, GameRow.class).getResultList().stream().map(SqlGameRepository::toGame).toList();
    }

    @Override
    @Transactional
    public List<Game> administeredBy(UUID userId) {
        return em.createQuery("select g from GameRow g, GameAdminRow a where a.gameId = g.id and a.userId = :u" + NOT_ARCHIVED, GameRow.class)
                .setParameter("u", userId).getResultList().stream().map(SqlGameRepository::toGame).toList();
    }

    @Override
    @Transactional
    public List<Game> joinedBy(UUID userId) {
        return em.createQuery("select g from GameRow g, MembershipRow m where m.gameId = g.id and m.userId = :u and m.status in ('requested', 'accepted')"
                + NOT_ARCHIVED, GameRow.class).setParameter("u", userId).getResultList().stream().map(SqlGameRepository::toGame).toList();
    }

    @Override
    @Transactional
    public List<Game> onServersAdministeredBy(UUID userId) {
        return em.createQuery("select g from GameRow g, ServerAdminRow a where a.serverId = g.serverId and a.userId = :u" + NOT_ARCHIVED,
                GameRow.class).setParameter("u", userId).getResultList().stream().map(SqlGameRepository::toGame).toList();
    }

    // ------------------------------------------------------------------ admins

    @Override
    @Transactional
    public List<GameAdmin> adminsOf(UUID gameId) {
        return em.createQuery("from GameAdminRow a where a.gameId = :g order by a.role desc, a.createdAt, a.id", GameAdminRow.class).setParameter("g", gameId)
                .getResultList().stream().map(a -> new GameAdmin(a.gameId, a.userId, a.role)).toList();
    }

    @Override
    @Transactional
    public Optional<GameAdmin> adminOf(UUID gameId, UUID userId) {
        return em.createQuery("from GameAdminRow a where a.gameId = :g and a.userId = :u", GameAdminRow.class).setParameter("g", gameId)
                .setParameter("u", userId).getResultStream().findFirst().map(a -> new GameAdmin(a.gameId, a.userId, a.role));
    }

    @Override
    @Transactional
    public boolean addAdmin(UUID gameId, UUID userId, String role) {
        if (adminOf(gameId, userId).isPresent()) {
            return false;
        }
        GameAdminRow row = new GameAdminRow();
        row.id = Ids.newId();
        row.gameId = gameId;
        row.userId = userId;
        row.role = role;
        row.createdAt = Instant.now();
        row.updatedAt = row.createdAt;
        try {
            em.persist(row);
            em.flush();
        } catch (PersistenceException e) {
            return false;
        }
        return true;
    }

    @Override
    @Transactional
    public boolean removeCoAdmin(UUID gameId, UUID userId) {
        return em.createQuery("delete from GameAdminRow a where a.gameId = :g and a.userId = :u and a.role = 'co'").setParameter("g", gameId)
                .setParameter("u", userId).executeUpdate() > 0;
    }

    @Override
    @Transactional
    public int removeCoAdminsOnServer(UUID serverId, UUID userId) {
        return em.createQuery("delete from GameAdminRow a where a.userId = :u and a.role = 'co' and a.gameId in (select g.id from GameRow g where g.serverId = :s)")
                .setParameter("u", userId).setParameter("s", serverId).executeUpdate();
    }

    // ------------------------------------------------------------------ members

    @Override
    @Transactional
    public Optional<Membership> membership(UUID gameId, UUID userId) {
        return em.createQuery("from MembershipRow m where m.gameId = :g and m.userId = :u", MembershipRow.class).setParameter("g", gameId)
                .setParameter("u", userId).getResultStream().findFirst().map(SqlGameRepository::toMembership);
    }

    @Override
    @Transactional
    public Optional<Membership> membershipById(UUID gameId, UUID membershipId) {
        return em.createQuery("from MembershipRow m where m.gameId = :g and m.id = :id", MembershipRow.class).setParameter("g", gameId)
                .setParameter("id", membershipId).getResultStream().findFirst().map(SqlGameRepository::toMembership);
    }

    @Override
    @Transactional
    public List<Membership> memberships(UUID gameId, String status) {
        return em.createQuery("from MembershipRow m where m.gameId = :g and m.status = :s order by m.createdAt, m.id", MembershipRow.class)
                .setParameter("g", gameId).setParameter("s", status).getResultList().stream().map(SqlGameRepository::toMembership).toList();
    }

    @Override
    @Transactional
    public boolean addMembership(Membership m) {
        if (membership(m.gameId(), m.userId()).isPresent()) {
            return false;
        }
        MembershipRow row = new MembershipRow();
        row.id = m.id();
        row.gameId = m.gameId();
        row.userId = m.userId();
        row.role = m.role();
        row.status = m.status();
        row.createdAt = m.createdAt();
        row.updatedAt = m.createdAt();
        try {
            em.persist(row);
            em.flush();
        } catch (PersistenceException e) {
            // the same person asked twice at the same moment: the unique index stops the second
            return false;
        }
        return true;
    }

    @Override
    @Transactional
    public boolean setMembershipStatus(UUID membershipId, String from, String to) {
        int n = em.createQuery("update MembershipRow m set m.status = :to, m.updatedAt = :t where m.id = :id and m.status = :from")
                .setParameter("to", to).setParameter("t", Instant.now()).setParameter("id", membershipId).setParameter("from", from).executeUpdate();
        em.clear();
        return n == 1;
    }

    @Override
    @Transactional
    public void setMembershipRole(UUID membershipId, String role) {
        em.createQuery("update MembershipRow m set m.role = :r, m.updatedAt = :t where m.id = :id").setParameter("r", role)
                .setParameter("t", Instant.now()).setParameter("id", membershipId).executeUpdate();
        em.clear();
    }

    // ------------------------------------------------------------------ helpers

    /** The text of an exception and of everything below it, to see which constraint was broken. */
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

    private static Game toGame(GameRow r) {
        return new Game(r.id, r.serverId, r.ownerId, r.name, GameState.ofId(r.state), r.joinCode,
                r.ruleSetId != null ? r.ruleSetId.toString() : r.builtinRuleSet, r.settings, r.settingsVersion, r.createdAt);
    }

    private static Membership toMembership(MembershipRow m) {
        return new Membership(m.id, m.gameId, m.userId, m.role, m.status, m.createdAt);
    }
}
