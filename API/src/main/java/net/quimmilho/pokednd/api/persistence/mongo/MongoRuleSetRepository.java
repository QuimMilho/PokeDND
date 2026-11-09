/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.mongo;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.quimmilho.pokednd.api.domain.settings.RuleSetRepository;
import net.quimmilho.pokednd.api.persistence.mongo.MongoDocs.RuleSetRow;
import org.bson.conversions.Bson;

/** The MongoDB adapter of the saved rule sets (chapter 16.3). */
@ApplicationScoped
public class MongoRuleSetRepository implements RuleSetRepository {

    /** By name without regard to capitals, then by id: the order the SQL adapter gives. */
    private static final Comparator<RuleSet> BY_NAME = Comparator.comparing((RuleSet r) -> r.name().toLowerCase(Locale.ROOT)).thenComparing(RuleSet::id);

    private static MongoCollection<RuleSetRow> sets() {
        return RuleSetRow.mongoCollection();
    }

    @Override
    public RuleSet create(RuleSet r) {
        RuleSetRow row = new RuleSetRow();
        row.id = r.id();
        row.scope = r.scope();
        row.ownerId = r.ownerId();
        row.serverId = r.serverId();
        row.name = r.name();
        row.formatVersion = r.formatVersion();
        row.basedOn = r.basedOn();
        row.settings = MongoJson.toDocument(r.settings());
        row.data = MongoJson.toDocument(r.data());
        row.createdAt = r.createdAt();
        row.updatedAt = r.updatedAt();
        sets().insertOne(row);
        return r;
    }

    @Override
    public Optional<RuleSet> find(UUID id) {
        return Optional.ofNullable(sets().find(eq("_id", id)).first()).map(MongoRuleSetRepository::toRuleSet);
    }

    @Override
    public List<RuleSet> ofScope(String scope) {
        return sorted(sets().find(eq("scope", scope)));
    }

    @Override
    public List<RuleSet> ownedBy(UUID userId) {
        return sorted(sets().find(and(eq("scope", SCOPE_USER), eq("ownerId", userId))));
    }

    @Override
    public List<RuleSet> ofServers(Collection<UUID> serverIds) {
        if (serverIds.isEmpty()) {
            return List.of();
        }
        return sorted(sets().find(and(eq("scope", SCOPE_SERVER), in("serverId", serverIds))));
    }

    @Override
    public long countOwnedBy(UUID userId) {
        return sets().countDocuments(and(eq("scope", SCOPE_USER), eq("ownerId", userId)));
    }

    @Override
    public boolean update(UUID id, String name, String basedOn, JsonNode settings, JsonNode data, Instant at) {
        Bson based = basedOn == null ? Updates.unset("basedOn") : Updates.set("basedOn", basedOn);
        return sets().updateOne(eq("_id", id), Updates.combine(Updates.set("name", name), based, Updates.set("settings", MongoJson.toDocument(settings)),
                Updates.set("data", MongoJson.toDocument(data)), Updates.set("updatedAt", at))).getMatchedCount() == 1;
    }

    @Override
    public boolean delete(UUID id) {
        return sets().deleteOne(eq("_id", id)).getDeletedCount() == 1;
    }

    private static List<RuleSet> sorted(Iterable<RuleSetRow> rows) {
        return MongoSupport.list(rows).stream().map(MongoRuleSetRepository::toRuleSet).sorted(BY_NAME).toList();
    }

    private static RuleSet toRuleSet(RuleSetRow r) {
        return new RuleSet(r.id, r.scope, r.ownerId, r.serverId, r.name, r.formatVersion, r.basedOn, MongoJson.toJson(r.settings), MongoJson.toJson(r.data),
                r.createdAt, r.updatedAt);
    }
}
