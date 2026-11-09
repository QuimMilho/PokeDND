-- Card export jobs (chapter 17.2: ExportJob). The finished files live in POKEDND_DATA_DIR/exports; the row remembers where.

CREATE TABLE export_job (
    id            CHAR(36) PRIMARY KEY,
    user_id       CHAR(36)     NOT NULL,
    request       JSON         NOT NULL,                 -- what the user asked for and the cards it resolved to
    state         VARCHAR(16)  NOT NULL,                 -- queued, running, done, failed or expired
    progress      INT          NOT NULL DEFAULT 0,       -- cards prepared so far
    total         INT          NOT NULL DEFAULT 0,       -- cards to prepare
    message       TEXT,                                  -- why it failed
    warnings      JSON,                                  -- problems that did not stop it (missing art...)
    file_path     VARCHAR(512),
    manifest_path VARCHAR(512),
    pages         INT,
    expires_at    DATETIME(6),
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    FOREIGN KEY (user_id) REFERENCES app_user (id)
) DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE INDEX export_job_user ON export_job (user_id, created_at);
CREATE INDEX export_job_expires ON export_job (state, expires_at);
