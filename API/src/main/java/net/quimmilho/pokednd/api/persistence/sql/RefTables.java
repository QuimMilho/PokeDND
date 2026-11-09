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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The tables of the reference data (migration V1). Only the SQL adapter uses these classes. */
public final class RefTables {

    private RefTables() {
    }

    @Entity(name = "RefBundle")
    @Table(name = "ref_bundle")
    public static class RefBundle extends PanacheEntityBase {
        @Id
        public int id;
        public String manifestHash;
        public int bundleFormat;
        public String cobblemon;
        public Instant loadedAt;
    }

    @Entity(name = "RefSpecies")
    @Table(name = "ref_species")
    public static class RefSpecies extends PanacheEntityBase {
        @Id
        public String id;
        public int dex;
        public String name;
        public int generation;
        public String type1;
        public String type2;
        public boolean playable;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode data;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode derived;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode evolutions;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode learnsets;
    }

    @Entity(name = "RefEntry")
    @Table(name = "ref_entry")
    public static class RefEntry extends PanacheEntityBase {
        @Id
        public String pk;
        public String kind;
        public String entryId;
        public String name;
        public String grp;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode data;
    }

    @Entity(name = "RefDocument")
    @Table(name = "ref_document")
    public static class RefDocument extends PanacheEntityBase {
        @Id
        public String name;
        @JdbcTypeCode(SqlTypes.JSON)
        @Column(columnDefinition = "jsonb")
        public JsonNode data;
    }
}
