/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package net.quimmilho.pokednd.api.persistence.sql;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** The tables of the Minecraft linking (migration V4). Only the SQL adapter uses these classes. */
public final class MinecraftTables {

    private MinecraftTables() {
    }

    @Entity(name = "McLinkRow")
    @Table(name = "mc_link")
    public static class McLinkRow extends PanacheEntityBase {
        @Id
        public UUID id;
        public UUID userId;
        public UUID mcUuid;
        public String mcName;
        public String source;
        public Instant linkedAt;
        public Instant createdAt;
        public Instant updatedAt;
    }

    @Entity(name = "LinkCodeRow")
    @Table(name = "link_code")
    public static class LinkCodeRow extends PanacheEntityBase {
        @Id
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
}
