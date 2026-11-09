/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Versioned migrations for MongoDB (chapter 16.3). MongoDB needs no tables, so a migration here makes the indexes that SQL gets from
 * its Flyway scripts, with the same numbers: <b>V1</b> reference data, <b>V2</b> accounts, <b>V3</b> export jobs, <b>V4</b> e-mail
 * tokens and Minecraft links, <b>V5</b> servers, <b>V6</b> games, <b>V7</b> rule sets and the audit trail, <b>V8</b> trainers.
 *
 * <p>The versions that were applied are remembered in the collection {@code schema_migration}. A migration runs once, in order, when
 * the API starts and before anything else uses the database. Making an index that exists already does nothing, so two API processes that
 * start at the same moment do no harm. To change the schema later, add a version at the end of {@link #MIGRATIONS}; never edit one that
 * has been released.
 */
@ApplicationScoped
public class MongoMigrations {

    private static final Logger LOG = Logger.getLogger(MongoMigrations.class);
    static final String HISTORY = "schema_migration";

    /** One migration: a number, what it is for, and the indexes to make. */
    record Migration(int version, String description, List<Index> indexes) {
    }

    /** An index to make on a collection. {@code sparse} indexes leave out documents that lack the field, so a unique one allows many of those. */
    record Index(String collection, String name, boolean unique, boolean sparse, String... fields) {
        Index(String collection, String name, String... fields) {
            this(collection, name, false, false, fields);
        }
    }

    private static Index unique(String collection, String name, String... fields) {
        return new Index(collection, name, true, false, fields);
    }

    private static Index uniqueSparse(String collection, String name, String field) {
        return new Index(collection, name, true, true, field);
    }

    static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, "reference data", List.of(
                    new Index("ref_species", "ref_species_dex", "dex", "_id"),
                    new Index("ref_species", "ref_species_generation", "generation"),
                    new Index("ref_species", "ref_species_type1", "type1"),
                    new Index("ref_species", "ref_species_type2", "type2"),
                    new Index("ref_entry", "ref_entry_kind", "kind", "entryId"),
                    new Index("ref_entry", "ref_entry_group", "kind", "grp"))),
            new Migration(2, "accounts", List.of(
                    // one live account per e-mail address (the field exists only while the account is live)
                    uniqueSparse("app_user", "app_user_email_unique", "liveEmail"),
                    unique("identity", "identity_provider_unique", "provider", "providerUserId"),
                    new Index("identity", "identity_user", "userId"),
                    unique("refresh_token", "refresh_token_hash_unique", "tokenHash"),
                    new Index("refresh_token", "refresh_token_user", "userId"),
                    new Index("refresh_token", "refresh_token_family", "familyId"))),
            new Migration(3, "export jobs", List.of(
                    new Index("export_job", "export_job_user", "userId", "createdAt"),
                    new Index("export_job", "export_job_expires", "state", "expiresAt"))),
            new Migration(4, "e-mail tokens, Minecraft links and linking codes", List.of(
                    unique("email_token", "email_token_hash_unique", "tokenHash"),
                    new Index("email_token", "email_token_user", "userId", "purpose"),
                    unique("mc_link", "mc_link_uuid_unique", "mcUuid"),
                    new Index("mc_link", "mc_link_user", "userId"),
                    unique("link_code", "link_code_unique", "code"))),
            new Migration(5, "servers", List.of(
                    uniqueSparse("server", "server_name_unique", "nameKey"),
                    unique("server", "server_token_unique", "tokenHash"),
                    unique("server_admin", "server_admin_unique", "serverId", "userId"),
                    new Index("server_admin", "server_admin_user", "userId"))),
            new Migration(6, "games", List.of(
                    // a join code is in use by one lobby at a time; one server has one active game
                    uniqueSparse("game", "game_join_code_unique", "joinCode"),
                    uniqueSparse("game", "game_one_active_per_server", "activeServerId"),
                    new Index("game", "game_server", "serverId"),
                    unique("game_admin", "game_admin_unique", "gameId", "userId"),
                    new Index("game_admin", "game_admin_user", "userId"),
                    unique("membership", "membership_unique", "gameId", "userId"),
                    new Index("membership", "membership_user", "userId"))),
            new Migration(7, "rule sets and the audit trail", List.of(
                    new Index("rule_set", "rule_set_owner", "ownerId"),
                    new Index("rule_set", "rule_set_server", "serverId"),
                    new Index("game", "game_rule_set", "ruleSetId"),
                    new Index("audit_entry", "audit_entry_game", "gameId", "createdAt"))),
            new Migration(8, "trainers", List.of(
                    // one trainer per person in a game, and a name (in lower case) is used once in a game
                    unique("trainer", "trainer_game_user_unique", "gameId", "userId"),
                    unique("trainer", "trainer_game_name_unique", "gameId", "nameKey"),
                    new Index("trainer", "trainer_user", "userId"))));

    @Inject
    MongoClient client;

    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "pokednd")
    String databaseName;

    /** Runs first among the start-up work, so the indexes are there before the first account or game is written. */
    void migrate(@Observes @Priority(100) StartupEvent event) {
        migrate(client.getDatabase(databaseName));
    }

    void migrate(MongoDatabase db) {
        MongoCollection<Document> history = db.getCollection(HISTORY);
        for (Migration m : MIGRATIONS) {
            if (history.countDocuments(new Document("_id", m.version())) > 0) {
                continue;
            }
            LOG.infof("MongoDB migration V%d: %s", m.version(), m.description());
            for (Index i : m.indexes()) {
                db.getCollection(i.collection()).createIndexes(List.of(new IndexModel(Indexes.ascending(i.fields()),
                        new IndexOptions().name(i.name()).unique(i.unique()).sparse(i.sparse()))));
            }
            history.replaceOne(new Document("_id", m.version()), new Document("_id", m.version()).append("description", m.description())
                    .append("appliedAt", java.util.Date.from(Instant.now())), new ReplaceOptions().upsert(true));
        }
    }
}
