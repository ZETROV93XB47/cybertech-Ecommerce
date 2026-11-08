-- One review per (user, product), enforced by the database: ReviewManagementServiceImp#create
-- checks first, but two simultaneous requests could both pass that check.
--
-- Run this query BEFORE the ALTER. The ALTER fails (error 1062, nothing changed) while duplicates
-- exist. Each duplicate group needs a decision (keep the most recent review, merge comments...):
-- delete the extra rows by hand, then run the ALTER.
--
--   SELECT userId, reviewId AS productId, COUNT(*) AS reviews, GROUP_CONCAT(id ORDER BY createdAt) AS reviewIds
--   FROM reviewTable
--   GROUP BY userId, reviewId
--   HAVING COUNT(*) > 1;

ALTER TABLE reviewTable
    ADD CONSTRAINT uk_review_user_product UNIQUE (userId, reviewId);
