/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.sql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.quimmilho.pokednd.api.domain.cards.ExportJobRepository;
import net.quimmilho.pokednd.api.persistence.sql.ExportTables.ExportJobRow;

/** The SQL adapter of the export jobs. */
@ApplicationScoped
public class SqlExportJobRepository implements ExportJobRepository {

    @Inject
    EntityManager em;

    @Inject
    ObjectMapper json;

    @Override
    @Transactional
    public void create(ExportJob job) {
        ExportJobRow row = new ExportJobRow();
        row.id = job.id();
        row.createdAt = job.createdAt();
        copy(job, row);
        em.persist(row);
    }

    @Override
    @Transactional
    public void update(ExportJob job) {
        ExportJobRow row = em.find(ExportJobRow.class, job.id());
        if (row != null) {
            copy(job, row);
        }
    }

    @Override
    @Transactional
    public Optional<ExportJob> find(UUID id) {
        return Optional.ofNullable(em.find(ExportJobRow.class, id)).map(this::toJob);
    }

    @Override
    @Transactional
    public List<ExportJob> ofUser(UUID userId, int limit) {
        return em.createQuery("from ExportJobRow j where j.userId = :u order by j.createdAt desc, j.id desc", ExportJobRow.class)
                .setParameter("u", userId).setMaxResults(limit).getResultList().stream().map(this::toJob).toList();
    }

    @Override
    @Transactional
    public List<ExportJob> doneAndExpiredBefore(Instant now) {
        return em.createQuery("from ExportJobRow j where j.state = 'done' and j.expiresAt < :now", ExportJobRow.class)
                .setParameter("now", now).getResultList().stream().map(this::toJob).toList();
    }

    @Override
    @Transactional
    public List<ExportJob> unfinished() {
        return em.createQuery("from ExportJobRow j where j.state in ('queued', 'running')", ExportJobRow.class).getResultList().stream()
                .map(this::toJob).toList();
    }

    private void copy(ExportJob j, ExportJobRow row) {
        row.userId = j.userId();
        row.request = j.request();
        row.state = j.state();
        row.progress = j.progress();
        row.total = j.total();
        row.message = j.message();
        row.warnings = j.warnings() == null ? null : json.valueToTree(j.warnings());
        row.filePath = j.filePath();
        row.manifestPath = j.manifestPath();
        row.pages = j.pages();
        row.expiresAt = j.expiresAt();
        row.updatedAt = j.updatedAt();
    }

    private ExportJob toJob(ExportJobRow r) {
        List<String> warnings = new ArrayList<>();
        if (r.warnings != null) {
            r.warnings.forEach(w -> warnings.add(w.asText()));
        }
        return new ExportJob(r.id, r.userId, r.request, r.state, r.progress, r.total, r.message, warnings, r.filePath, r.manifestPath, r.pages,
                r.expiresAt, r.createdAt, r.updatedAt);
    }
}
