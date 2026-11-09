/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.sql;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The tables of the rule sets and the audit trail (migration V7). Only the SQL adapter uses these classes. */
public final class SettingsTables {

    private SettingsTables() {
    }

    @Entity(name = "RuleSetRow")
    @Table(name = "rule_set")
    public static class RuleSetRow extends PanacheEntityBase {
        @Id
        public UUID id;
        public String scope;
        public UUID ownerId;
        public UUID serverId;
        public String name;
        public int formatVersion;
        public String basedOn;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode settings;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode data;
        public Instant createdAt;
        public Instant updatedAt;
    }

    @Entity(name = "AuditEntryRow")
    @Table(name = "audit_entry")
    public static class AuditEntryRow extends PanacheEntityBase {
        @Id
        public UUID id;
        public UUID gameId;
        public UUID actorUserId;
        public String action;
        public String target;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode beforeState;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode afterState;
        public Instant createdAt;
    }
}
