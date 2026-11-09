-- Phase 32: games, their admins and their members (chapter 17.2).
-- MariaDB 11 refuses a generated column made from a CHAR(36) column ("cannot be used in the GENERATED ALWAYS AS clause"), so in the
-- MariaDB migrations every id is VARCHAR(36). Hibernate writes and reads the ids as text either way.

CREATE TABLE game (
    id          VARCHAR(36) PRIMARY KEY,
    server_id   VARCHAR(36)     NOT NULL,
    owner_id    VARCHAR(36)     NOT NULL,
    name        VARCHAR(80)  NOT NULL,
    state       VARCHAR(16)  NOT NULL DEFAULT 'draft',   -- draft, lobby, running, paused, ended, archived (3.4)
    rule_set_id VARCHAR(36),                                -- the rule set tables come in V7
    settings    JSON         NOT NULL,                   -- this game's own setting overrides (layers: phase 33)
    join_code   VARCHAR(12),                             -- only while the lobby is open
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    -- one server, one active game (3.8): lobby, running and paused count; draft, ended and archived do not.
    -- (PostgreSQL has a partial unique index for this; here a column that is empty for inactive games does the same.)
    active_server_id VARCHAR(36) GENERATED ALWAYS AS (IF(state IN ('lobby', 'running', 'paused'), server_id, NULL)) STORED,
    FOREIGN KEY (server_id) REFERENCES server (id),
    FOREIGN KEY (owner_id) REFERENCES app_user (id),
    -- a join code is in use by one lobby at a time (a unique key lets any number of rows have no code)
    UNIQUE KEY game_join_code_unique (join_code),
    UNIQUE KEY game_one_active_per_server (active_server_id)
) DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE INDEX game_server ON game (server_id);

-- The owner (the server admin who created the game) and the co-admins the owner added.
CREATE TABLE game_admin (
    id         VARCHAR(36) PRIMARY KEY,
    game_id    VARCHAR(36)    NOT NULL,
    user_id    VARCHAR(36)    NOT NULL,
    role       VARCHAR(8)  NOT NULL,                      -- owner or co
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE (game_id, user_id),
    FOREIGN KEY (game_id) REFERENCES game (id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES app_user (id)
) DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE INDEX game_admin_user ON game_admin (user_id);

-- A person in a game, or asking to be.
CREATE TABLE membership (
    id         VARCHAR(36) PRIMARY KEY,
    game_id    VARCHAR(36)    NOT NULL,
    user_id    VARCHAR(36)    NOT NULL,
    role       VARCHAR(16) NOT NULL,                      -- player or spectator
    status     VARCHAR(16) NOT NULL,                      -- requested, accepted, rejected or kicked
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE (game_id, user_id),
    FOREIGN KEY (game_id) REFERENCES game (id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES app_user (id)
) DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE INDEX membership_user ON membership (user_id);
