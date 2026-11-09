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

/** The table of the export jobs (migration V3). */
public final class ExportTables {

    private ExportTables() {
    }

    @Entity(name = "ExportJobRow")
    @Table(name = "export_job")
    public static class ExportJobRow extends PanacheEntityBase {
        @Id
        public UUID id;
        public UUID userId;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode request;
        public String state;
        public int progress;
        public int total;
        public String message;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode warnings;
        public String filePath;
        public String manifestPath;
        public Integer pages;
        public Instant expiresAt;
        public Instant createdAt;
        public Instant updatedAt;
    }
}
