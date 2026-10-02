SET search_path TO acs_ball_by_ball;

-- Public IDs changed from seven to ten digits; canonical envelope replay assigns replacements.
UPDATE dim_match
SET public_match_id = NULL
WHERE public_match_id IS NOT NULL;