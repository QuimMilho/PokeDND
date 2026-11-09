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

/** The tables of the accounts (migration V2). Only the SQL adapter uses these classes. */
public final class AccountTables {

    private AccountTables() {
    }

    @Entity(name = "AppUser")
    @Table(name = "app_user")
    public static class AppUser extends PanacheEntityBase {
        @Id
        public UUID id;
        public String email;
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

    @Entity(name = "Identity")
    @Table(name = "identity")
    public static class Identity extends PanacheEntityBase {
        @Id
        public UUID id;
        public UUID userId;
        public String provider;
        public String providerUserId;
        public Instant createdAt;
        public Instant updatedAt;
    }

    @Entity(name = "EmailTokenRow")
    @Table(name = "email_token")
    public static class EmailTokenRow extends PanacheEntityBase {
        @Id
        public UUID id;
        public UUID userId;
        public String purpose;
        public String tokenHash;
        public Instant expiresAt;
        public Instant usedAt;
        public Instant createdAt;
        public Instant updatedAt;
    }

    @Entity(name = "RefreshTokenRow")
    @Table(name = "refresh_token")
    public static class RefreshTokenRow extends PanacheEntityBase {
        @Id
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
}
