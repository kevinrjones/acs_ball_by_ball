ALTER TABLE dim_innings DROP FOREIGN KEY fk_dim_innings_match;
ALTER TABLE fact_match DROP FOREIGN KEY fk_fact_match_match;
ALTER TABLE fact_delivery DROP FOREIGN KEY fk_fact_delivery_match;
ALTER TABLE bridge_match_person DROP FOREIGN KEY fk_bridge_match_person_match;

ALTER TABLE dim_innings MODIFY match_key BIGINT NOT NULL;
ALTER TABLE fact_match MODIFY match_key BIGINT NOT NULL;
ALTER TABLE fact_delivery MODIFY match_key BIGINT NOT NULL;
ALTER TABLE bridge_match_person MODIFY match_key BIGINT NOT NULL;
ALTER TABLE dim_match CHANGE id match_key BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE dim_match ADD COLUMN canonical_match_id CHAR(36) NULL AFTER match_key;
ALTER TABLE dim_match ADD CONSTRAINT uq_dim_match_canonical_id UNIQUE (canonical_match_id);

ALTER TABLE dim_innings ADD CONSTRAINT fk_dim_innings_match
    FOREIGN KEY (match_key) REFERENCES dim_match (match_key);
ALTER TABLE fact_match ADD CONSTRAINT fk_fact_match_match
    FOREIGN KEY (match_key) REFERENCES dim_match (match_key);
ALTER TABLE fact_delivery ADD CONSTRAINT fk_fact_delivery_match
    FOREIGN KEY (match_key) REFERENCES dim_match (match_key);
ALTER TABLE bridge_match_person ADD CONSTRAINT fk_bridge_match_person_match
    FOREIGN KEY (match_key) REFERENCES dim_match (match_key);

CREATE TABLE match_source_reference
(
    match_key           BIGINT       NOT NULL,
    provider            VARCHAR(100) NOT NULL,
    provider_record_key VARCHAR(255) NOT NULL,
    source_record_id    CHAR(36)     NOT NULL,
    raw_content_digest  CHAR(64)     NOT NULL,

    CONSTRAINT pk_match_source_reference PRIMARY KEY (match_key, provider, source_record_id),
    CONSTRAINT uq_match_source_provider_record UNIQUE (provider, source_record_id),
    CONSTRAINT fk_match_source_match FOREIGN KEY (match_key) REFERENCES dim_match (match_key)
) ENGINE = InnoDB;

CREATE INDEX idx_match_source_record ON match_source_reference (source_record_id);

-- Existing rows intentionally retain a NULL canonical_match_id. Replay their
-- canonical envelopes to establish identity; never infer it from file_name.