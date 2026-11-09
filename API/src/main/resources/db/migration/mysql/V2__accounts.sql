-- Accounts (chapter 17.2): User, Identity, RefreshToken. All ids are UUIDs (time-ordered, made by the API), written as 36 characters.

CREATE TABLE app_user (
    id             CHAR(36) PRIMARY KEY,
    email          VARCHAR(254) NOT NULL,
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    password_hash  VARCHAR(255),                          -- Argon2id; null for accounts that only use OAuth
    display_name   VARCHAR(60)  NOT NULL,
    locale         VARCHAR(8)   NOT NULL DEFAULT 'en',
    theme          VARCHAR(16)  NOT NULL DEFAULT 'system',
    site_role      VARCHAR(16)  NOT NULL DEFAULT 'none',  -- none or siteAdmin
    deleted_at     DATETIME(6),
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    -- One live account per e-mail address, whatever the capital letters; deleted accounts free the address.
    -- (PostgreSQL has a partial index on lower(email) for this; here a column that is empty for deleted accounts does the same.)
    email_key      VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
                   GENERATED ALWAYS AS (IF(deleted_at IS NULL, LOWER(email), NULL)) STORED,
    UNIQUE KEY app_user_email_unique (email_key)
) DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE identity (
    id               CHAR(36) PRIMARY KEY,
    user_id          CHAR(36)     NOT NULL,
    provider         VARCHAR(16)  NOT NULL,               -- password or discord
    provider_user_id VARCHAR(255) NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    UNIQUE (provider, provider_user_id),
    FOREIGN KEY (user_id) REFERENCES app_user (id)
) DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE INDEX identity_user ON identity (user_id);

-- Only the hash of a refresh token is stored. A token and the ones it was swapped for share a family:
-- presenting an already used token revokes the whole family.
CREATE TABLE refresh_token (
    id         CHAR(36) PRIMARY KEY,
    user_id    CHAR(36)    NOT NULL,
    family_id  CHAR(36)    NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6),
    user_agent VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES app_user (id)
) DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE INDEX refresh_token_user ON refresh_token (user_id);
CREATE INDEX refresh_token_family ON refresh_token (family_id);
