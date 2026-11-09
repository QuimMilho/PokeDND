-- Phase 33: rule sets, the settings of a game, and the audit trail (chapters 12, 17.2).

-- A saved rule set: a named bundle of settings and data (12.3). The built-in ones (defaults, casual, hardcore, ...) are not rows;
-- they come with the code. scope: site (everybody can use it), server (the admins of that server) or user (its owner).
CREATE TABLE rule_set (
    id             VARCHAR(36) PRIMARY KEY,
    scope          VARCHAR(8)   NOT NULL,
    owner_id       VARCHAR(36)     NOT NULL,
    server_id      VARCHAR(36),                                  -- only for the scope server
    name           VARCHAR(80)  NOT NULL,
    format_version INT          NOT NULL,
    based_on       VARCHAR(80),                               -- a note: where it was started from
    settings       JSON         NOT NULL,                     -- key -> value, only what differs from the defaults
    data           JSON         NOT NULL,                     -- class list, skill list, power table, type chart, loot
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    FOREIGN KEY (owner_id) REFERENCES app_user (id),
    FOREIGN KEY (server_id) REFERENCES server (id)
) DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE INDEX rule_set_owner ON rule_set (owner_id);
CREATE INDEX rule_set_server ON rule_set (server_id);

-- A game uses one rule set, a saved one (rule_set_id) or a built-in one (builtin_rule_set); both empty means the plain defaults.
-- settings (V6) holds the game's own changes; settings_version counts the changes, so two admins cannot overwrite each other.
ALTER TABLE game ADD CONSTRAINT game_rule_set_fk FOREIGN KEY (rule_set_id) REFERENCES rule_set (id);
ALTER TABLE game ADD COLUMN builtin_rule_set VARCHAR(40);
ALTER TABLE game ADD COLUMN settings_version BIGINT NOT NULL DEFAULT 0;

-- The audit trail: every change an admin makes is a row, never changed afterwards. Phase 33 writes the settings changes.
CREATE TABLE audit_entry (
    id            VARCHAR(36) PRIMARY KEY,
    game_id       VARCHAR(36),
    actor_user_id VARCHAR(36),
    action        VARCHAR(64)  NOT NULL,                      -- settings.update, settings.reset, settings.undo, settings.ruleset
    target        VARCHAR(100),
    before_state  JSON,
    after_state   JSON,
    created_at    DATETIME(6)  NOT NULL,
    FOREIGN KEY (game_id) REFERENCES game (id),
    FOREIGN KEY (actor_user_id) REFERENCES app_user (id)
) DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE INDEX audit_entry_game ON audit_entry (game_id, created_at);
