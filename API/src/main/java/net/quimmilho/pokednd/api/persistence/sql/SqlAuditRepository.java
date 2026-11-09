/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.sql;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import net.quimmilho.pokednd.api.domain.audit.AuditRepository;
import net.quimmilho.pokednd.api.persistence.sql.SettingsTables.AuditEntryRow;

/** The SQL adapter of the audit trail (chapter 16.3). */
@ApplicationScoped
public class SqlAuditRepository implements AuditRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void append(AuditEntry e) {
        AuditEntryRow row = new AuditEntryRow();
        row.id = e.id();
        row.gameId = e.gameId();
        row.actorUserId = e.actorUserId();
        row.action = e.action();
        row.target = e.target();
        row.beforeState = e.before();
        row.afterState = e.after();
        row.createdAt = e.createdAt();
        em.persist(row);
        em.flush();
    }

    @Override
    @Transactional
    public List<AuditEntry> forGame(UUID gameId, String actionPrefix) {
        return em.createQuery("from AuditEntryRow a where a.gameId = :g and a.action like :p order by a.createdAt desc, a.id desc", AuditEntryRow.class)
                .setParameter("g", gameId).setParameter("p", actionPrefix + "%").getResultList().stream()
                .map(a -> new AuditEntry(a.id, a.gameId, a.actorUserId, a.action, a.target, a.beforeState, a.afterState, a.createdAt)).toList();
    }
}
