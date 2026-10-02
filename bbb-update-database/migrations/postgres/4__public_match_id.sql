SET search_path TO acs_ball_by_ball;

ALTER TABLE dim_match ADD COLUMN public_match_id BIGINT NULL;

CREATE UNIQUE INDEX uq_dim_match_public_match_id
    ON dim_match (public_match_id)
    WHERE public_match_id IS NOT NULL;

-- Existing rows with a NULL canonical_match_id remain without a public ID.
-- Replay canonical envelopes to backfill deterministic public_match_id values.