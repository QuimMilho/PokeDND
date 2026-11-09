-- Phase 32: games, their admins and their members (chapter 17.2).

CREATE TABLE game (
    id          UUID PRIMARY KEY,
    server_id   UUID         NOT NULL REFERENCES server (id),
    owner_id    UUID         NOT NULL REFERENCES app_user (id),
    name        VARCHAR(80)  NOT NULL,
    state       VARCHAR(16)  NOT NULL DEFAULT 'draft',   -- draft, lobby, running, paused, ended, archived (3.4)
    rule_set_id UUID,                                    -- the rule set tables come in phase 33
    settings    JSONB        NOT NULL DEFAULT '{}',      -- this game's own setting overrides (layers: phase 33)
    join_code   VARCHAR(12),                             -- only while the lobby is open
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX game_server ON game (server_id);
-- a join code is in use by one lobby at a time
CREATE UNIQUE INDEX game_join_code_unique ON game (join_code) WHERE join_code IS NOT NULL;
-- one server, one active game (3.8): lobby, running and paused count; draft, ended and archived do not
CREATE UNIQUE INDEX game_one_active_per_server ON game (server_id) WHERE state IN ('lobby', 'running', 'paused');

-- The owner (the server admin who created the game) and the co-admins the owner added.
CREATE TABLE game_admin (
    id         UUID PRIMARY KEY,
    game_id    UUID        NOT NULL REFERENCES game (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES app_user (id),
    role       VARCHAR(8)  NOT NULL,                      -- owner or co
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (game_id, user_id)
);
CREATE INDEX game_admin_user ON game_admin (user_id);

-- A person in a game, or asking to be.
CREATE TABLE membership (
    id         UUID PRIMARY KEY,
    game_id    UUID        NOT NULL REFERENCES game (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES app_user (id),
    role       VARCHAR(16) NOT NULL,                      -- player or spectator
    status     VARCHAR(16) NOT NULL,                      -- requested, accepted, rejected or kicked
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (game_id, user_id)
);
CREATE INDEX membership_user ON membership (user_id);
