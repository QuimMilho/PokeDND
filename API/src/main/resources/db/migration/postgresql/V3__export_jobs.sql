-- Card export jobs (chapter 17.2: ExportJob). The finished files live in POKEDND_DATA_DIR/exports; the row remembers where.

CREATE TABLE export_job (
    id            UUID PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES app_user (id),
    request       JSONB        NOT NULL,                 -- what the user asked for and the cards it resolved to
    state         VARCHAR(16)  NOT NULL,                 -- queued, running, done, failed or expired
    progress      INT          NOT NULL DEFAULT 0,       -- cards prepared so far
    total         INT          NOT NULL DEFAULT 0,       -- cards to prepare
    message       TEXT,                                  -- why it failed
    warnings      JSONB,                                 -- problems that did not stop it (missing art...)
    file_path     VARCHAR(512),
    manifest_path VARCHAR(512),
    pages         INT,
    expires_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);
CREATE INDEX export_job_user ON export_job (user_id, created_at DESC);
CREATE INDEX export_job_expires ON export_job (expires_at) WHERE state = 'done';
