-- V10__review_author_name.sql — denormalise the reviewer's display name onto the
-- review row: a review must stay attributable even after its author is deleted
-- (reviews.user_id cascades on user delete, but the review row itself outlives
-- the author when moderation keeps it; backfill from the users table once).

ALTER TABLE reviews
    ADD COLUMN author_name VARCHAR(150) NOT NULL DEFAULT '';

UPDATE reviews r
SET author_name = CONCAT_WS(' ', u.first_name, u.last_name)
FROM users u
WHERE r.user_id = u.id;

ALTER TABLE reviews
    ALTER COLUMN author_name DROP DEFAULT;
