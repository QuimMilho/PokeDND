/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.mongo;

import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.bson.codecs.pojo.annotations.BsonId;

/**
 * The collections of the MongoDB adapter, one class each. They mirror the tables of the SQL migrations (V1 to V8) so that both
 * adapters hold the same facts; the names of the collections are the names of the tables. Only the MongoDB adapter uses these classes.
 *
 * <p>Where SQL has a partial or functional unique index, a document here carries a helper field that exists only while the rule
 * applies ({@code liveEmail}, {@code nameKey}, {@code activeServerId}, {@code joinCode}); the unique indexes in {@link MongoMigrations}
 * are sparse, so documents without the field are not part of them.
 */
public final class MongoDocs {

    private MongoDocs() {
    }

    // ------------------------------------------------------------------ reference data (V1)

    @MongoEntity(collection = "ref_bundle")
    public static class RefBundle extends PanacheMongoEntityBase {
        @BsonId
        public Integer id;
        public String manifestHash;
        public int bundleFormat;
        public String cobblemon;
        public Instant loadedAt;
    }

    @MongoEntity(collection = "ref_species")
    public static class RefSpecies extends PanacheMongoEntityBase {
        @BsonId
        public String id;
        public int dex;
        public String name;
        public int generation;
        public String type1;
        public String type2;
        public boolean playable;
        public Document data;
        public Document derived;
        public Document evolutions;
        public Document learnsets;
    }

    @MongoEntity(collection = "ref_entry")
    public static class RefEntry extends PanacheMongoEntityBase {
        @BsonId
        public String pk;
        public String kind;
        public String entryId;
        public String name;
        public String grp;
        public Document data;
    }

    @MongoEntity(collection = "ref_document")
    public static class RefDocument extends PanacheMongoEntityBase {
        @BsonId
        public String name;
        public Document data;
    }

    // ------------------------------------------------------------------ accounts (V2, V4)

    @MongoEntity(collection = "app_user")
    public static class AppUser extends PanacheMongoEntityBase {
        @BsonId
        public UUID id;
        public String email;
        /** The e-mail address in lower case; only while the account is live, so a deleted account frees the address. */
        public String liveEmail;
        public boolean emailVerified;
        public String passwordHash;
        public String displayName;
        public String locale;
        public String theme;
        public String siteRole;
        public Instant deletedAt;
        public Instant createdAt;
        public Instant updatedAt;
    }

    @MongoEntity(collection = "identity")
    public static class Identity extends PanacheMongoEntityBase {
        @BsonId
        public UUID id;
        public UUID userId;
        public String provider;
        public String providerUserId;
        public Instant createdAt;
        public Instant updatedAt;
    }

    @MongoEntity(collection = "refresh_token")
    public static class RefreshTokenRow extends PanacheMongoEntityBase {
        @BsonId
        public UUID id;
        public UUID userId;
        public UUID familyId;
        public String tokenHash;
        public Instant expiresAt;
        public Instant revokedAt;
        public String userAgent;
        public Instant createdAt;
        public Instant updatedAt;
    }

    @MongoEntity(collection = "email_token")
    public static class EmailTokenRow extends PanacheMongoEntityBase {
        @BsonId
        public UUID id;
        public UUID userId;
        public String purpose;
        public String tokenHash;
        public Instant expiresAt;
        public Instant usedAt;
        public Instant createdAt;
        public Instant updatedAt;
    }

    @MongoEntity(collection = "mc_link")
    public static class McLinkRow extends PanacheMongoEntityBase {
        @BsonId
        public UUID id;
        public UUID userId;
        public UUID mcUuid;
        public String mcName;
        public String source;
        public Instant linkedAt;
        public Instant createdAt;
        public Instant updatedAt;
    }

    @MongoEntity(collection = "link_code")
    public static class LinkCodeRow extends PanacheMongoEntityBase {
        @BsonId
        public UUID id;
        public String code;
        public String direction;
        public UUID userId;
        public UUID mcUuid;
        public String mcName;
        public Instant expiresAt;
        public Instant usedAt;
        public Instant createdAt;
        public Instant updatedAt;
    }

    // ------------------------------------------------------------------ export jobs (V3)

    @MongoEntity(collection = "export_job")
    public static class ExportJobRow extends PanacheMongoEntityBase {
        @BsonId
        public UUID id;
        public UUID userId;
        public Document request;
        public String state;
        public int progress;
        public int total;
        public String message;
        public List<String> warnings;
        public String filePath;
        public String manifestPath;
        public Integer pages;
        public Instant expiresAt;
        public Instant createdAt;
        public Instant updatedAt;
    }

    // ------------------------------------------------------------------ servers (V5)

    @MongoEntity(collection = "server")
    public static class ServerRow extends PanacheMongoEntityBase {
        @BsonId
        public UUID id;
        public String name;
        /** The name in lower case, with a unique index: two servers cannot have the same name whatever the capitals. */
        public String nameKey;
        public String tokenHash;
        public String tokenPrefix;
        public String bluemapUrl;
        public String status;
        public Instant lastSeenAt;
        public String modVersion;
        public String mcVersion;
        public Instant createdAt;
        public Instant updatedAt;
    }

    @MongoEntity(collection = "server_admin")
    public static class ServerAdminRow extends PanacheMongoEntityBase {
        @BsonId
        public UUID id;
        public UUID serverId;
        public UUID userId;
        public Instant createdAt;
        public Instant updatedAt;
    }

    // ------------------------------------------------------------------ games (V6, V7)

    @MongoEntity(collection = "game")
    public static class GameRow extends PanacheMongoEntityBase {
        @BsonId
        public UUID id;
        public UUID serverId;
        public UUID ownerId;
        public String name;
        public String state;
        public UUID ruleSetId;
        public String builtinRuleSet;
        public Document settings;
        public long settingsVersion;
        /** Only while the lobby is open; unique. */
        public String joinCode;
        /** The server's id while the game is active (lobby, running or paused), otherwise missing; unique: one active game per server. */
        public UUID activeServerId;
        public Instant createdAt;
        public Instant updatedAt;
    }

    @MongoEntity(collection = "game_admin")
    public static class GameAdminRow extends PanacheMongoEntityBase {
        @BsonId
        public UUID id;
        public UUID gameId;
        public UUID userId;
        public String role;
        public Instant createdAt;
        public Instant updatedAt;
    }

    @MongoEntity(collection = "membership")
    public static class MembershipRow extends PanacheMongoEntityBase {
        @BsonId
        public UUID id;
        public UUID gameId;
        public UUID userId;
        public String role;
        public String status;
        public Instant createdAt;
        public Instant updatedAt;
    }

    @MongoEntity(collection = "rule_set")
    public static class RuleSetRow extends PanacheMongoEntityBase {
        @BsonId
        public UUID id;
        public String scope;
        public UUID ownerId;
        public UUID serverId;
        public String name;
        public int formatVersion;
        public String basedOn;
        public Document settings;
        public Document data;
        public Instant createdAt;
        public Instant updatedAt;
    }

    @MongoEntity(collection = "audit_entry")
    public static class AuditEntryRow extends PanacheMongoEntityBase {
        @BsonId
        public UUID id;
        public UUID gameId;
        public UUID actorUserId;
        public String action;
        public String target;
        public Document beforeState;
        public Document afterState;
        public Instant createdAt;
    }

    // ------------------------------------------------------------------ trainers (V8)

    @MongoEntity(collection = "trainer")
    public static class TrainerRow extends PanacheMongoEntityBase {
        @BsonId
        public UUID id;
        public UUID gameId;
        public UUID userId;
        public String name;
        /** The name in lower case; unique in the game. */
        public String nameKey;
        public String classId;
        public int level;
        public long xp;
        public Document abilityScores;
        public Document skills;
        public int hp;
        public int hpMax;
        public long money;
        public Document items;
        public String scoreMethod;
        public String starterSpecies;
        public Integer starterLevel;
        public String skinModel;
        public String skinSource;
        public String skinUsername;
        public String skinSha256;
        public Instant skinAt;
        public Instant createdAt;
        public Instant updatedAt;
    }
}
