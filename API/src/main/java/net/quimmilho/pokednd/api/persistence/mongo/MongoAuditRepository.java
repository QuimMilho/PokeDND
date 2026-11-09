/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.mongo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.regex;

import com.mongodb.client.MongoCollection;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import net.quimmilho.pokednd.api.domain.audit.AuditRepository;
import net.quimmilho.pokednd.api.persistence.mongo.MongoDocs.AuditEntryRow;

/** The MongoDB adapter of the audit trail (chapter 16.3). */
@ApplicationScoped
public class MongoAuditRepository implements AuditRepository {

    private static MongoCollection<AuditEntryRow> entries() {
        return AuditEntryRow.mongoCollection();
    }

    @Override
    public void append(AuditEntry e) {
        AuditEntryRow row = new AuditEntryRow();
        row.id = e.id();
        row.gameId = e.gameId();
        row.actorUserId = e.actorUserId();
        row.action = e.action();
        row.target = e.target();
        row.beforeState = MongoJson.toDocument(e.before());
        row.afterState = MongoJson.toDocument(e.after());
        row.createdAt = e.createdAt();
        entries().insertOne(row);
    }

    @Override
    public List<AuditEntry> forGame(UUID gameId, String actionPrefix) {
        return MongoSupport.list(entries().find(and(eq("gameId", gameId), regex("action", "^" + Pattern.quote(actionPrefix)))).sort(MongoSupport.newestFirst()))
                .stream().map(a -> new AuditEntry(a.id, a.gameId, a.actorUserId, a.action, a.target, MongoJson.toJson(a.beforeState), MongoJson.toJson(a.afterState),
                        a.createdAt)).toList();
    }
}
