ALTER TABLE dim_match
    ADD COLUMN source_file_name VARCHAR(120);

UPDATE dim_match
SET source_file_name = regexp_replace(file_name, '^.*/', '');

ALTER TABLE dim_match
    ALTER COLUMN source_file_name SET NOT NULL;

CREATE INDEX idx_dim_match_source_file_name ON dim_match (source_file_name);