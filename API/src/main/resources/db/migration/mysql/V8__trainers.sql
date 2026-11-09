-- Phase 38: trainers (chapter 17.2). The sheet is stored; proficiency, Armor Class and the rest are worked out from it by Core.

CREATE TABLE trainer (
    id             CHAR(36) PRIMARY KEY,
    game_id        CHAR(36)      NOT NULL,
    user_id        CHAR(36)      NOT NULL,
    name           VARCHAR(16)  NOT NULL,
    name_key       VARCHAR(16)  NOT NULL,                  -- the name in lower case: unique in the game, whatever the case (3.6)
    class_id       VARCHAR(64)  NOT NULL,
    level          INT          NOT NULL,
    xp             BIGINT       NOT NULL,
    ability_scores JSON         NOT NULL,                  -- {"str": 15, "dex": 14, ...}
    skills         JSON         NOT NULL,                  -- the skills the player picked
    hp             INT          NOT NULL,
    hp_max         INT          NOT NULL,
    money          BIGINT       NOT NULL,
    items          JSON         NOT NULL,                  -- the bag: item id -> count
    score_method   VARCHAR(24),                            -- how the scores were made
    starter_species VARCHAR(64),                           -- the chosen starter Pokemon (the Pokemon rows come with the battle phases)
    starter_level  INT,
    skin_model     VARCHAR(8),                             -- classic or slim; empty until a skin is set
    skin_source    VARCHAR(8),                             -- upload or username
    skin_username  VARCHAR(16),
    skin_sha256    VARCHAR(64),                            -- of the stored PNG, in data/skins/<id>.png
    skin_at        DATETIME(6),
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    UNIQUE KEY trainer_game_user_unique (game_id, user_id),
    UNIQUE KEY trainer_game_name_unique (game_id, name_key),
    FOREIGN KEY (game_id) REFERENCES game (id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES app_user (id)
) DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE INDEX trainer_user ON trainer (user_id);
