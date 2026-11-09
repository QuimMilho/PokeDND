-- Phase 31: Minecraft servers and their admins (chapter 17.2).

-- A registered Minecraft server. The mod proves who it is with a 256-bit token; only the SHA-256 hash is stored (chapter 14.6),
-- and token_prefix (the first characters of the token) only helps a person tell tokens apart.
CREATE TABLE server (
    id           UUID PRIMARY KEY,
    name         VARCHAR(64) NOT NULL,
    token_hash   VARCHAR(64) NOT NULL UNIQUE,
    token_prefix VARCHAR(12) NOT NULL,
    bluemap_url  VARCHAR(512),
    status       VARCHAR(16) NOT NULL DEFAULT 'offline',   -- online or offline; the realtime channel (phase 40) keeps it up to date
    last_seen_at TIMESTAMPTZ,
    mod_version  VARCHAR(64),
    mc_version   VARCHAR(32),
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL
);
-- two servers cannot have the same name, whatever the capitals
CREATE UNIQUE INDEX server_name_unique ON server (lower(name));

-- Who administers a server. A person may administer several servers, and a server has several admins.
CREATE TABLE server_admin (
    id         UUID PRIMARY KEY,
    server_id  UUID        NOT NULL REFERENCES server (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES app_user (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (server_id, user_id)
);
CREATE INDEX server_admin_user ON server_admin (user_id);
