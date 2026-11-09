/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.sql;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.quimmilho.pokednd.api.domain.settings.RuleSetRepository;
import net.quimmilho.pokednd.api.persistence.sql.SettingsTables.RuleSetRow;

/** The SQL adapter of the saved rule sets (chapter 16.3). */
@ApplicationScoped
public class SqlRuleSetRepository implements RuleSetRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public RuleSet create(RuleSet r) {
        RuleSetRow row = new RuleSetRow();
        row.id = r.id();
        row.scope = r.scope();
        row.ownerId = r.ownerId();
        row.serverId = r.serverId();
        row.name = r.name();
        row.formatVersion = r.formatVersion();
        row.basedOn = r.basedOn();
        row.settings = r.settings();
        row.data = r.data();
        row.createdAt = r.createdAt();
        row.updatedAt = r.updatedAt();
        em.persist(row);
        em.flush();
        return r;
    }

    @Override
    @Transactional
    public Optional<RuleSet> find(UUID id) {
        return Optional.ofNullable(em.find(RuleSetRow.class, id)).map(SqlRuleSetRepository::toRuleSet);
    }

    @Override
    @Transactional
    public List<RuleSet> ofScope(String scope) {
        return em.createQuery("from RuleSetRow r where r.scope = :s order by lower(r.name), r.id", RuleSetRow.class).setParameter("s", scope).getResultList()
                .stream().map(SqlRuleSetRepository::toRuleSet).toList();
    }

    @Override
    @Transactional
    public List<RuleSet> ownedBy(UUID userId) {
        return em.createQuery("from RuleSetRow r where r.scope = 'user' and r.ownerId = :u order by lower(r.name), r.id", RuleSetRow.class)
                .setParameter("u", userId).getResultList().stream().map(SqlRuleSetRepository::toRuleSet).toList();
    }

    @Override
    @Transactional
    public List<RuleSet> ofServers(Collection<UUID> serverIds) {
        if (serverIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery("from RuleSetRow r where r.scope = 'server' and r.serverId in :s order by lower(r.name), r.id", RuleSetRow.class)
                .setParameter("s", serverIds).getResultList().stream().map(SqlRuleSetRepository::toRuleSet).toList();
    }

    @Override
    @Transactional
    public long countOwnedBy(UUID userId) {
        return em.createQuery("select count(r) from RuleSetRow r where r.scope = 'user' and r.ownerId = :u", Long.class).setParameter("u", userId)
                .getSingleResult();
    }

    @Override
    @Transactional
    public boolean update(UUID id, String name, String basedOn, com.fasterxml.jackson.databind.JsonNode settings, com.fasterxml.jackson.databind.JsonNode data,
                          Instant at) {
        RuleSetRow row = em.find(RuleSetRow.class, id);
        if (row == null) {
            return false;
        }
        row.name = name;
        row.basedOn = basedOn;
        row.settings = settings;
        row.data = data;
        row.updatedAt = at;
        em.flush();
        return true;
    }

    @Override
    @Transactional
    public boolean delete(UUID id) {
        int n = em.createQuery("delete from RuleSetRow r where r.id = :id").setParameter("id", id).executeUpdate();
        em.clear();
        return n == 1;
    }

    private static RuleSet toRuleSet(RuleSetRow r) {
        return new RuleSet(r.id, r.scope, r.ownerId, r.serverId, r.name, r.formatVersion, r.basedOn, r.settings, r.data, r.createdAt, r.updatedAt);
    }
}
