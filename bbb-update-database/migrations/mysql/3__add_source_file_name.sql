ALTER TABLE dim_match
    ADD COLUMN source_file_name VARCHAR(120) NULL AFTER file_name;

UPDATE dim_match
SET source_file_name = SUBSTRING_INDEX(file_name, '/', -1);

ALTER TABLE dim_match
    MODIFY COLUMN source_file_name VARCHAR(120) NOT NULL;

CREATE INDEX idx_dim_match_source_file_name ON dim_match (source_file_name);