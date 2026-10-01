ALTER TABLE dim_match
    ADD COLUMN source_file_name VARCHAR(120);

WITH RECURSIVE file_parts(match_key, remaining, source_file_name) AS (
    SELECT match_key, file_name, file_name
    FROM dim_match
    UNION ALL
    SELECT match_key,
           substr(remaining, instr(remaining, '/') + 1),
           substr(remaining, instr(remaining, '/') + 1)
    FROM file_parts
    WHERE instr(remaining, '/') > 0
)
UPDATE dim_match
SET source_file_name = (
    SELECT source_file_name
    FROM file_parts
    WHERE file_parts.match_key = dim_match.match_key
      AND instr(file_parts.remaining, '/') = 0
);

CREATE INDEX idx_dim_match_source_file_name ON dim_match (source_file_name);