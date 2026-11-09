-- Accounts (chapter 17.2): User, Identity, RefreshToken. All ids are UUIDs (time-ordered, made by the API).

CREATE TABLE app_user (
    id             UUID PRIMARY KEY,
    email          VARCHAR(254) NOT NULL,
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    password_hash  VARCHAR(255),                          -- Argon2id; null for accounts that only use OAuth
    display_name   VARCHAR(60)  NOT NULL,
    locale         VARCHAR(8)   NOT NULL DEFAULT 'en',
    theme          VARCHAR(16)  NOT NULL DEFAULT 'system',
    site_role      VARCHAR(16)  NOT NULL DEFAULT 'none',  -- none or siteAdmin
    deleted_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);
-- One live account per e-mail address, whatever the capital letters; deleted accounts free the address.
CREATE UNIQUE INDEX app_user_email_unique ON app_user (lower(email)) WHERE deleted_at IS NULL;

CREATE TABLE identity (
    id               UUID PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES app_user (id),
    provider         VARCHAR(16)  NOT NULL,               -- password or discord
    provider_user_id VARCHAR(255) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    UNIQUE (provider, provider_user_id)
);
CREATE INDEX identity_user ON identity (user_id);

-- Only the hash of a refresh token is stored. A token and the ones it was swapped for share a family:
-- presenting an already used token revokes the whole family.
CREATE TABLE refresh_token (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES app_user (id),
    family_id  UUID        NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    user_agent VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX refresh_token_user ON refresh_token (user_id);
CREATE INDEX refresh_token_family ON refresh_token (family_id);
