PRAGMA foreign_keys = ON;

ALTER TABLE dim_match RENAME COLUMN id TO match_key;
ALTER TABLE dim_match ADD COLUMN canonical_match_id VARCHAR(36) NULL;
CREATE UNIQUE INDEX uq_dim_match_canonical_id ON dim_match (canonical_match_id);

CREATE TABLE match_source_reference
(
    match_key           INTEGER      NOT NULL,
    provider            VARCHAR(100) NOT NULL,
    provider_record_key VARCHAR(255) NOT NULL,
    source_record_id    VARCHAR(36)  NOT NULL,
    raw_content_digest  CHAR(64)     NOT NULL,

    CONSTRAINT pk_match_source_reference PRIMARY KEY (match_key, provider, source_record_id),
    CONSTRAINT uq_match_source_provider_record UNIQUE (provider, source_record_id),
    CONSTRAINT fk_match_source_match FOREIGN KEY (match_key) REFERENCES dim_match (match_key)
);

CREATE INDEX idx_match_source_record ON match_source_reference (source_record_id);

-- SQLite INTEGER PRIMARY KEY remains a generated 64-bit rowid after rename.
-- Existing rows intentionally retain a NULL canonical_match_id. Replay their
-- canonical envelopes to establish identity; never infer it from file_name.