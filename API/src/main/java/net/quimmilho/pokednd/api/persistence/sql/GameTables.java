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

/** The tables of the games (migration V6). Only the SQL adapter uses these classes. */
public final class GameTables {

    private GameTables() {
    }

    @Entity(name = "GameRow")
    @Table(name = "game")
    public static class GameRow extends PanacheEntityBase {
        @Id
        public UUID id;
        public UUID serverId;
        public UUID ownerId;
        public String name;
        public String state;
        public UUID ruleSetId;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode settings;
        public String joinCode;
        public Instant createdAt;
        public Instant updatedAt;
    }

    @Entity(name = "GameAdminRow")
    @Table(name = "game_admin")
    public static class GameAdminRow extends PanacheEntityBase {
        @Id
        public UUID id;
        public UUID gameId;
        public UUID userId;
        public String role;
        public Instant createdAt;
        public Instant updatedAt;
    }

    @Entity(name = "MembershipRow")
    @Table(name = "membership")
    public static class MembershipRow extends PanacheEntityBase {
        @Id
        public UUID id;
        public UUID gameId;
        public UUID userId;
        public String role;
        public String status;
        public Instant createdAt;
        public Instant updatedAt;
    }
}
