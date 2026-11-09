-- Phase 33: rule sets, the settings of a game, and the audit trail (chapters 12, 17.2).

-- A saved rule set: a named bundle of settings and data (12.3). The built-in ones (defaults, casual, hardcore, ...) are not rows;
-- they come with the code. scope: site (everybody can use it), server (the admins of that server) or user (its owner).
CREATE TABLE rule_set (
    id             UUID PRIMARY KEY,
    scope          VARCHAR(8)   NOT NULL,
    owner_id       UUID         NOT NULL REFERENCES app_user (id),
    server_id      UUID REFERENCES server (id),               -- only for the scope server
    name           VARCHAR(80)  NOT NULL,
    format_version INT          NOT NULL,
    based_on       VARCHAR(80),                                -- a note: where it was started from
    settings       JSONB        NOT NULL DEFAULT '{}',        -- key -> value, only what differs from the defaults
    data           JSONB        NOT NULL DEFAULT '{}',        -- class list, skill list, power table, type chart, loot
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX rule_set_owner ON rule_set (owner_id);
CREATE INDEX rule_set_server ON rule_set (server_id);

-- A game uses one rule set, a saved one (rule_set_id) or a built-in one (builtin_rule_set); both empty means the plain defaults.
-- settings (V6) holds the game's own changes; settings_version counts the changes, so two admins cannot overwrite each other.
ALTER TABLE game ADD CONSTRAINT game_rule_set_fk FOREIGN KEY (rule_set_id) REFERENCES rule_set (id);
ALTER TABLE game ADD COLUMN builtin_rule_set VARCHAR(40);
ALTER TABLE game ADD COLUMN settings_version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX game_rule_set ON game (rule_set_id);

-- The audit trail: every change an admin makes is a row, never changed afterwards. Phase 33 writes the settings changes.
CREATE TABLE audit_entry (
    id            UUID PRIMARY KEY,
    game_id       UUID REFERENCES game (id),
    actor_user_id UUID REFERENCES app_user (id),
    action        VARCHAR(64) NOT NULL,                        -- settings.update, settings.reset, settings.undo, ruleset.apply
    target        VARCHAR(100),
    before_state  JSONB,
    after_state   JSONB,
    created_at    TIMESTAMPTZ NOT NULL
);
CREATE INDEX audit_entry_game ON audit_entry (game_id, created_at);
