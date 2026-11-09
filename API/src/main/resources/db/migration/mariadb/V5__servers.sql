-- Phase 31: Minecraft servers and their admins (chapter 17.2).

-- A registered Minecraft server. The mod proves who it is with a 256-bit token; only the SHA-256 hash is stored (chapter 14.6),
-- and token_prefix (the first characters of the token) only helps a person tell tokens apart.
CREATE TABLE server (
    id           VARCHAR(36) PRIMARY KEY,
    name         VARCHAR(64) NOT NULL,
    token_hash   VARCHAR(64) NOT NULL UNIQUE,
    token_prefix VARCHAR(12) NOT NULL,
    bluemap_url  VARCHAR(512),
    status       VARCHAR(16) NOT NULL DEFAULT 'offline',   -- online or offline; the realtime channel (phase 40) keeps it up to date
    last_seen_at DATETIME(6),
    mod_version  VARCHAR(64),
    mc_version   VARCHAR(32),
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    -- two servers cannot have the same name, whatever the capitals (PostgreSQL: a unique index on lower(name))
    name_key     VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin GENERATED ALWAYS AS (LOWER(name)) STORED,
    UNIQUE KEY server_name_unique (name_key)
) DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Who administers a server. A person may administer several servers, and a server has several admins.
CREATE TABLE server_admin (
    id         VARCHAR(36) PRIMARY KEY,
    server_id  VARCHAR(36)    NOT NULL,
    user_id    VARCHAR(36)    NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE (server_id, user_id),
    FOREIGN KEY (server_id) REFERENCES server (id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES app_user (id)
) DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE INDEX server_admin_user ON server_admin (user_id);
