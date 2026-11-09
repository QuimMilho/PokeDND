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

/** The tables of the servers (migration V5). Only the SQL adapter uses these classes. */
public final class ServerTables {

    private ServerTables() {
    }

    @Entity(name = "ServerRow")
    @Table(name = "server")
    public static class ServerRow extends PanacheEntityBase {
        @Id
        public UUID id;
        public String name;
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

    @Entity(name = "ServerAdminRow")
    @Table(name = "server_admin")
    public static class ServerAdminRow extends PanacheEntityBase {
        @Id
        public UUID id;
        public UUID serverId;
        public UUID userId;
        public Instant createdAt;
        public Instant updatedAt;
    }
}
