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
import java.util.Optional;
import java.util.UUID;
import net.quimmilho.pokednd.api.auth.Ids;
import net.quimmilho.pokednd.api.domain.servers.ServerRepository;
import net.quimmilho.pokednd.api.persistence.sql.ServerTables.ServerAdminRow;
import net.quimmilho.pokednd.api.persistence.sql.ServerTables.ServerRow;

/** The SQL adapter of the servers (chapter 16.3). */
@ApplicationScoped
public class SqlServerRepository implements ServerRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public Server create(Server s) {
        if (nameTaken(s.name(), null)) {
            throw new ServerNameTakenException(s.name());
        }
        ServerRow row = new ServerRow();
        row.id = s.id();
        row.name = s.name();
        row.tokenHash = s.tokenHash();
        row.tokenPrefix = s.tokenPrefix();
        row.bluemapUrl = s.bluemapUrl();
        row.status = s.status();
        row.lastSeenAt = s.lastSeenAt();
        row.modVersion = s.modVersion();
        row.mcVersion = s.mcVersion();
        row.createdAt = s.createdAt();
        row.updatedAt = s.createdAt();
        try {
            em.persist(row);
            em.flush();
        } catch (PersistenceException e) {
            // two servers with the same name registered at the same moment: the unique index stops the second
            throw new ServerNameTakenException(s.name());
        }
        return s;
    }

    @Override
    @Transactional
    public Optional<Server> find(UUID id) {
        return Optional.ofNullable(em.find(ServerRow.class, id)).map(SqlServerRepository::toServer);
    }

    @Override
    @Transactional
    public Optional<Server> findByTokenHash(String tokenHash) {
        return em.createQuery("from ServerRow s where s.tokenHash = :h", ServerRow.class).setParameter("h", tokenHash).getResultStream().findFirst()
                .map(SqlServerRepository::toServer);
    }

    @Override
    @Transactional
    public List<Server> all() {
        return em.createQuery("from ServerRow s order by lower(s.name)", ServerRow.class).getResultList().stream().map(SqlServerRepository::toServer).toList();
    }

    @Override
    @Transactional
    public List<Server> administeredBy(UUID userId) {
        return em.createQuery("select s from ServerRow s, ServerAdminRow a where a.serverId = s.id and a.userId = :u order by lower(s.name)", ServerRow.class)
                .setParameter("u", userId).getResultList().stream().map(SqlServerRepository::toServer).toList();
    }

    @Override
    @Transactional
    public boolean update(UUID id, String name, String bluemapUrl) {
        if (nameTaken(name, id)) {
            throw new ServerNameTakenException(name);
        }
        try {
            int n = em.createQuery("update ServerRow s set s.name = :n, s.bluemapUrl = :b, s.updatedAt = :t where s.id = :id")
                    .setParameter("n", name).setParameter("b", bluemapUrl).setParameter("t", Instant.now()).setParameter("id", id).executeUpdate();
            em.clear();
            return n == 1;
        } catch (PersistenceException e) {
            throw new ServerNameTakenException(name);
        }
    }

    @Override
    @Transactional
    public boolean replaceToken(UUID id, String tokenHash, String tokenPrefix) {
        int n = em.createQuery("update ServerRow s set s.tokenHash = :h, s.tokenPrefix = :p, s.updatedAt = :t where s.id = :id")
                .setParameter("h", tokenHash).setParameter("p", tokenPrefix).setParameter("t", Instant.now()).setParameter("id", id).executeUpdate();
        // a bulk update does not touch the entities already loaded in this transaction; forget them so the next read sees the new row
        em.clear();
        return n == 1;
    }

    @Override
    @Transactional
    public boolean delete(UUID id) {
        // the admin list goes with the server (ON DELETE CASCADE)
        return em.createQuery("delete from ServerRow s where s.id = :id").setParameter("id", id).executeUpdate() == 1;
    }

    @Override
    @Transactional
    public List<UUID> adminsOf(UUID serverId) {
        return em.createQuery("select a.userId from ServerAdminRow a where a.serverId = :s order by a.createdAt, a.id", UUID.class)
                .setParameter("s", serverId).getResultList();
    }

    @Override
    @Transactional
    public boolean addAdmin(UUID serverId, UUID userId) {
        if (isAdmin(serverId, userId)) {
            return false;
        }
        ServerAdminRow row = new ServerAdminRow();
        row.id = Ids.newId();
        row.serverId = serverId;
        row.userId = userId;
        row.createdAt = Instant.now();
        row.updatedAt = row.createdAt;
        try {
            em.persist(row);
            em.flush();
        } catch (PersistenceException e) {
            // the same person added twice at the same moment
            return false;
        }
        return true;
    }

    @Override
    @Transactional
    public boolean removeAdmin(UUID serverId, UUID userId) {
        return em.createQuery("delete from ServerAdminRow a where a.serverId = :s and a.userId = :u").setParameter("s", serverId)
                .setParameter("u", userId).executeUpdate() > 0;
    }

    @Override
    @Transactional
    public boolean isAdmin(UUID serverId, UUID userId) {
        return !em.createQuery("select a.id from ServerAdminRow a where a.serverId = :s and a.userId = :u", UUID.class).setParameter("s", serverId)
                .setParameter("u", userId).setMaxResults(1).getResultList().isEmpty();
    }

    @Override
    @Transactional
    public void recordStatus(UUID id, String status, Instant seenAt, String modVersion, String mcVersion) {
        ServerRow row = em.find(ServerRow.class, id);
        if (row == null) {
            return;
        }
        row.status = status;
        row.lastSeenAt = seenAt;
        if (modVersion != null) {
            row.modVersion = modVersion;
        }
        if (mcVersion != null) {
            row.mcVersion = mcVersion;
        }
        row.updatedAt = seenAt;
    }

    @Override
    @Transactional
    public int markAllOffline() {
        return em.createQuery("update ServerRow s set s.status = :off, s.updatedAt = :t where s.status <> :off").setParameter("off", OFFLINE)
                .setParameter("t", Instant.now()).executeUpdate();
    }

    private boolean nameTaken(String name, UUID exceptId) {
        List<UUID> found = em.createQuery("select s.id from ServerRow s where lower(s.name) = lower(:n)", UUID.class).setParameter("n", name).getResultList();
        return found.stream().anyMatch(id -> !id.equals(exceptId));
    }

    private static Server toServer(ServerRow r) {
        return new Server(r.id, r.name, r.tokenHash, r.tokenPrefix, r.bluemapUrl, r.status, r.lastSeenAt, r.modVersion, r.mcVersion, r.createdAt);
    }
}
