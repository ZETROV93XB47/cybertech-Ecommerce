-- GET /review/mine returns the order each review refers to. Store it on the review and backfill
-- the existing ones with the most recent PAID / SHIPPED / DELIVERED order of the author that
-- contains the product (the rule reviews were created under). Reviews with no matching order stay NULL.
START TRANSACTION;

ALTER TABLE reviewTable ADD COLUMN orderUuid BINARY(16) NULL AFTER isHateful;

UPDATE reviewTable r
SET r.orderUuid = (
    SELECT o.uuid
    FROM orderTable o
    JOIN orderItemTable oi ON oi.orderId = o.id
    WHERE o.userId = r.userId
      AND oi.productEntity = r.reviewId
      AND o.status IN ('PAID', 'SHIPPED', 'DELIVERED')
    ORDER BY o.orderDate DESC
    LIMIT 1
)
WHERE r.orderUuid IS NULL;

COMMIT;
