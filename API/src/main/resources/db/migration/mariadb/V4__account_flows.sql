-- Phases 29 and 30: single-use e-mail tokens, linked Minecraft accounts and linking codes (chapter 17.2).

-- Verification and password-reset tokens. Only the hash is stored; a token works once and expires.
CREATE TABLE email_token (
    id         VARCHAR(36) PRIMARY KEY,
    user_id    VARCHAR(36)    NOT NULL,
    purpose    VARCHAR(16) NOT NULL,                 -- verify or reset
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES app_user (id)
) DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE INDEX email_token_user ON email_token (user_id, purpose);

-- A Minecraft account belongs to exactly one app account (mc_uuid is unique); one app account may link several.
CREATE TABLE mc_link (
    id         VARCHAR(36) PRIMARY KEY,
    user_id    VARCHAR(36)    NOT NULL,
    mc_uuid    VARCHAR(36)    NOT NULL UNIQUE,
    mc_name    VARCHAR(16) NOT NULL,
    source     VARCHAR(16) NOT NULL DEFAULT 'code',
    linked_at  DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES app_user (id)
) DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE INDEX mc_link_user ON mc_link (user_id);

-- The 8-character linking codes. direction 'mc': made for a Minecraft account (user_id empty), typed in the app.
-- direction 'app': made for an app account (mc_uuid empty), typed in Minecraft.
CREATE TABLE link_code (
    id         VARCHAR(36) PRIMARY KEY,
    code       VARCHAR(8)  NOT NULL UNIQUE,
    direction  VARCHAR(8)  NOT NULL,
    user_id    VARCHAR(36),
    mc_uuid    VARCHAR(36),
    mc_name    VARCHAR(16),
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES app_user (id)
) DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
