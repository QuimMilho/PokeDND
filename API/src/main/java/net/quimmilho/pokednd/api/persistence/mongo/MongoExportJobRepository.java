/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.mongo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.lt;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.quimmilho.pokednd.api.domain.cards.ExportJobRepository;
import net.quimmilho.pokednd.api.persistence.mongo.MongoDocs.ExportJobRow;
import org.bson.conversions.Bson;

/** The MongoDB adapter of the card export jobs (chapter 16.3). */
@ApplicationScoped
public class MongoExportJobRepository implements ExportJobRepository {

    private static MongoCollection<ExportJobRow> jobs() {
        return ExportJobRow.mongoCollection();
    }

    @Override
    public void create(ExportJob job) {
        ExportJobRow row = new ExportJobRow();
        row.id = job.id();
        row.createdAt = job.createdAt();
        copy(job, row);
        jobs().insertOne(row);
    }

    @Override
    public void update(ExportJob j) {
        // every field is saved, as the SQL adapter does; a field that is null is removed
        List<Bson> sets = new ArrayList<>();
        sets.add(Updates.set("userId", j.userId()));
        sets.add(Updates.set("request", MongoJson.toDocument(j.request())));
        sets.add(Updates.set("state", j.state()));
        sets.add(Updates.set("progress", j.progress()));
        sets.add(Updates.set("total", j.total()));
        sets.add(optional("message", j.message()));
        sets.add(optional("warnings", j.warnings() == null ? null : new ArrayList<>(j.warnings())));
        sets.add(optional("filePath", j.filePath()));
        sets.add(optional("manifestPath", j.manifestPath()));
        sets.add(optional("pages", j.pages()));
        sets.add(optional("expiresAt", j.expiresAt()));
        sets.add(Updates.set("updatedAt", j.updatedAt()));
        jobs().updateOne(eq("_id", j.id()), Updates.combine(sets));
    }

    private static Bson optional(String field, Object value) {
        return value == null ? Updates.unset(field) : Updates.set(field, value);
    }

    @Override
    public Optional<ExportJob> find(UUID id) {
        return Optional.ofNullable(jobs().find(eq("_id", id)).first()).map(MongoExportJobRepository::toJob);
    }

    @Override
    public List<ExportJob> ofUser(UUID userId, int limit) {
        return MongoSupport.list(jobs().find(eq("userId", userId)).sort(MongoSupport.newestFirst()).limit(limit)).stream()
                .map(MongoExportJobRepository::toJob).toList();
    }

    @Override
    public List<ExportJob> doneAndExpiredBefore(Instant now) {
        return MongoSupport.list(jobs().find(and(eq("state", DONE), lt("expiresAt", now)))).stream().map(MongoExportJobRepository::toJob).toList();
    }

    @Override
    public List<ExportJob> unfinished() {
        return MongoSupport.list(jobs().find(in("state", QUEUED, RUNNING))).stream().map(MongoExportJobRepository::toJob).toList();
    }

    private static void copy(ExportJob j, ExportJobRow row) {
        row.userId = j.userId();
        row.request = MongoJson.toDocument(j.request());
        row.state = j.state();
        row.progress = j.progress();
        row.total = j.total();
        row.message = j.message();
        row.warnings = j.warnings() == null ? null : new ArrayList<>(j.warnings());
        row.filePath = j.filePath();
        row.manifestPath = j.manifestPath();
        row.pages = j.pages();
        row.expiresAt = j.expiresAt();
        row.updatedAt = j.updatedAt();
    }

    private static ExportJob toJob(ExportJobRow r) {
        List<String> warnings = r.warnings == null ? new ArrayList<>() : new ArrayList<>(r.warnings);
        return new ExportJob(r.id, r.userId, MongoJson.toJson(r.request), r.state, r.progress, r.total, r.message, warnings, r.filePath, r.manifestPath, r.pages,
                r.expiresAt, r.createdAt, r.updatedAt);
    }
}
