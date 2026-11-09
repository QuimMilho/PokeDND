-- Reference data (species, moves, items, abilities and more), loaded from the data bundle (chapter 19.2). MySQL and MariaDB.
-- The tables are a cache of the bundle: the loader replaces them whenever MANIFEST.json changes.
-- Every table is utf8mb4 with the same case-insensitive collation, so text is compared the way PostgreSQL's lower() compares it.

CREATE TABLE ref_bundle (
    id            INT PRIMARY KEY,                 -- always 1: one bundle at a time
    manifest_hash VARCHAR(64)  NOT NULL,           -- "outputHash" of MANIFEST.json
    bundle_format INT          NOT NULL,
    cobblemon     VARCHAR(64),
    loaded_at     DATETIME(6)  NOT NULL            -- UTC
) DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE ref_species (
    id          VARCHAR(64) PRIMARY KEY,           -- species id, e.g. bulbasaur
    dex         INT          NOT NULL,
    name        VARCHAR(100) NOT NULL,
    generation  INT          NOT NULL,             -- by National Dex range (9.6)
    type1       VARCHAR(16)  NOT NULL,
    type2       VARCHAR(16),
    playable    BOOLEAN      NOT NULL,
    data        JSON         NOT NULL,             -- the merged species with its forms
    derived     JSON,                              -- stage badge, weaknesses, signature moves
    evolutions  JSON,                              -- the evolution rules that start from this species
    learnsets   JSON                               -- moves by form and method
) DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE INDEX ref_species_dex ON ref_species (dex, id);
CREATE INDEX ref_species_generation ON ref_species (generation);
CREATE INDEX ref_species_type1 ON ref_species (type1);
CREATE INDEX ref_species_type2 ON ref_species (type2);

-- Moves, items and abilities share one table: "pk" is "<kind>:<id>".
CREATE TABLE ref_entry (
    pk        VARCHAR(160) PRIMARY KEY,
    kind      VARCHAR(16)  NOT NULL,               -- move, item or ability
    entry_id  VARCHAR(100) NOT NULL,
    name      VARCHAR(160) NOT NULL,
    grp       VARCHAR(64),                         -- the move's type, or the item's category
    data      JSON         NOT NULL
) DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE INDEX ref_entry_kind ON ref_entry (kind, entry_id);
CREATE INDEX ref_entry_group ON ref_entry (kind, grp);

-- Small whole files: the type chart, the natures, the roster and the manifest.
CREATE TABLE ref_document (
    name  VARCHAR(64) PRIMARY KEY,
    data  JSON NOT NULL
) DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
