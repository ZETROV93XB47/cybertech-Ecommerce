-- Back-in-stock alerts (STOCK_ALERT_JOB): one row per (user, product), reused when the user
-- subscribes again. status: PENDING, NOTIFIED, CANCELLED, EXPIRED.
CREATE TABLE IF NOT EXISTS stockAlertTable
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    uuid        BINARY(16)  NOT NULL UNIQUE,
    version     BIGINT      NOT NULL,
    createdAt   DATETIME    NOT NULL,
    updatedAt   DATETIME,

    userId      BIGINT      NOT NULL,
    productId   BIGINT      NOT NULL,
    status      VARCHAR(20) NOT NULL,
    requestedAt DATETIME    NOT NULL,
    notifiedAt  DATETIME,

    PRIMARY KEY (id),
    CONSTRAINT uk_stock_alert_user_product UNIQUE (userId, productId),
    INDEX idx_stock_alert_status_product (status, productId),
    FOREIGN KEY (userId) REFERENCES userTable (id),
    FOREIGN KEY (productId) REFERENCES productTable (id)
);
