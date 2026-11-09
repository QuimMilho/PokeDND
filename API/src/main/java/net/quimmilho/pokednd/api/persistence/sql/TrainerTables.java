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

/** The table of the trainers (migration V8). Only the SQL adapter uses this class. */
public final class TrainerTables {

    private TrainerTables() {
    }

    @Entity(name = "TrainerRow")
    @Table(name = "trainer")
    public static class TrainerRow extends PanacheEntityBase {
        @Id
        public UUID id;
        public UUID gameId;
        public UUID userId;
        public String name;
        public String nameKey;
        public String classId;
        public int level;
        public long xp;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode abilityScores;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode skills;
        public int hp;
        public int hpMax;
        public long money;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode items;
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
