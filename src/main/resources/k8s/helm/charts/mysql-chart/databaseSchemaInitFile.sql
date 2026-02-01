CREATE DATABASE IF NOT EXISTS cybertechDB;
CREATE DATABASE IF NOT EXISTS keycloakDB;
GRANT ALL PRIVILEGES ON keycloakDB.* TO 'rookie'@'%';

FLUSH PRIVILEGES;

use cybertechDB;

CREATE TABLE userTable
(
    -- Hérité de BaseEntity
    id                         BIGINT       NOT NULL AUTO_INCREMENT,
    uuid                       BINARY(16) NOT NULL UNIQUE,
    version                    BIGINT       NOT NULL,

    -- Champs spécifiques à UserEntity
    email                      VARCHAR(50)  NOT NULL UNIQUE,
    firstName                  VARCHAR(50),
    lastName                   VARCHAR(50),
    sex                        VARCHAR(255) NOT NULL, -- EnumType.STRING
    address                    VARCHAR(255),
    birthDate                  DATE,
    password                   VARCHAR(100),
    role                       VARCHAR(255) NOT NULL, -- EnumType.STRING
    phoneNumber                INTEGER,
    isActive                   BOOLEAN      NOT NULL,
    defaultCommunicationChanel VARCHAR(255) NOT NULL, -- EnumType.STRING (supposé)
    numberOfHatefulComments    INT          NOT NULL,

    PRIMARY KEY (id)
);


CREATE TABLE productTable
(
    -- Hérité de BaseEntity
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    uuid          BINARY(16) NOT NULL UNIQUE,
    version       BIGINT         NOT NULL,

    -- Champs spécifiques à ProductEntity
    name          VARCHAR(255)   NOT NULL,
    price         DECIMAL(19, 2) NOT NULL,
    brand         VARCHAR(255)   NOT NULL, -- EnumType.STRING
    category      VARCHAR(255)   NOT NULL, -- EnumType.STRING
    photo         VARCHAR(255),
    stock         INTEGER        NOT NULL,
    reservedStock INTEGER        NOT NULL DEFAULT 0,
    description   TEXT,
    attributes    JSON           NOT NULL,

    PRIMARY KEY (id)
);


CREATE TABLE paymentTable
(
    -- Hérité de BaseEntity
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    uuid          BINARY(16) NOT NULL UNIQUE,
    version       BIGINT         NOT NULL,

    -- Champs spécifiques à PaymentEntity
    amount        DECIMAL(19, 2) NOT NULL,
    paymentType   VARCHAR(255)   NOT NULL, -- EnumType.STRING
    paymentStatus VARCHAR(255)   NOT NULL, -- EnumType.STRING
    paymentDate   DATETIME       NOT NULL,

    -- Clé étrangère implicite pour la relation OneToOne vers OrderEntity
    -- L'annotation 'mappedBy' sur paymentAttemptEntity dans OrderEntity indique
    -- que la colonne de clé étrangère (paymentId) se trouve dans orderTable.
    -- Par conséquent, cette table n'a pas besoin de colonne d'ID de commande ici.

    PRIMARY KEY (id)
);


CREATE TABLE stockTable
(
    -- Hérité de BaseEntity
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    uuid              BINARY(16) NOT NULL UNIQUE,
    version           BIGINT       NOT NULL,

    -- Champs spécifiques à StockEntity
    order_uuid        BINARY(16) NOT NULL,
    product_uuid      BINARY(16) NOT NULL,
    quantity          INT          NOT NULL,
    reservationStatus VARCHAR(255) NOT NULL, -- EnumType.STRING
    createdAt         DATETIME,

    PRIMARY KEY (id),

    -- Contrainte d'unicité pour assurer une seule ligne par commande/produit
    UNIQUE KEY UQ_ORDER_PRODUCT (order_uuid, product_uuid)
);


CREATE TABLE bankCardTable
(
    -- Hérité de BaseEntity
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    uuid           BINARY(16) NOT NULL UNIQUE,
    version        BIGINT       NOT NULL,

    -- Champs spécifiques à BankCardEntity
    cardHolderName VARCHAR(100) NOT NULL,
    cardNumber     VARCHAR(19)  NOT NULL,
    expiryDate     VARCHAR(7)   NOT NULL, -- MM/YYYY
    cardType       VARCHAR(255) NOT NULL, -- EnumType.STRING
    isDefault      BOOLEAN      NOT NULL,

    -- Relation ManyToOne vers UserEntity
    user_id        BIGINT       NOT NULL,

    PRIMARY KEY (id),

    -- Contrainte de clé étrangère
    FOREIGN KEY (user_id) REFERENCES userTable (id)
);


CREATE TABLE reviewTable
(
    -- Hérité de BaseEntity
    id        BIGINT   NOT NULL AUTO_INCREMENT,
    uuid      BINARY(16) NOT NULL UNIQUE,
    version   BIGINT   NOT NULL,

    -- Champs spécifiques à ReviewEntity
    rating    INTEGER  NOT NULL,
    comment   TEXT,
    isHateful BOOLEAN  NOT NULL,
    createdAt DATETIME NOT NULL,
    updatedAt DATETIME,

    -- Relations ManyToOne
    reviewId  BIGINT,            -- Clé étrangère vers ProductEntity (nommé étrangement 'reviewId' dans l'entité)
    userId    BIGINT   NOT NULL, -- Clé étrangère vers UserEntity

    PRIMARY KEY (id),

    -- Contraintes de clés étrangères
    -- Le nom de la colonne 'reviewId' dans l'entité semble pointer vers un produit
    FOREIGN KEY (reviewId) REFERENCES productTable (id),
    FOREIGN KEY (userId) REFERENCES userTable (id),

     -- Contrainte pour valider le rating au niveau de la base de données
     CONSTRAINT chk_rating CHECK (rating >= 1 AND rating <= 5)
);


CREATE TABLE orderTable
(
    -- Hérité de BaseEntity
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    uuid             BINARY(16) NOT NULL UNIQUE,
    version          BIGINT         NOT NULL,

    -- Champs spécifiques à OrderEntity
    orderDate        DATETIME       NOT NULL,
    totalAmount      DECIMAL(19, 2) NOT NULL,
    shippingAddress  VARCHAR(255)   NOT NULL,
    status           VARCHAR(255)   NOT NULL, -- EnumType.STRING
    shippingType     VARCHAR(255)   NOT NULL, -- EnumType.STRING
    shippingProvider VARCHAR(255)   NOT NULL, -- EnumType.STRING
    discountType     VARCHAR(255)   NOT NULL, -- EnumType.STRING

    -- Relations ManyToOne et OneToOne
    userId           BIGINT,                  -- Vers UserEntity
    paymentId        BIGINT,                  -- Vers PaymentEntity

    PRIMARY KEY (id),

    -- Contraintes de clés étrangères (PaymentEntity est également déduit)
    FOREIGN KEY (userId) REFERENCES userTable (id),
    -- On suppose l'existence de la table paymentTable
    FOREIGN KEY (paymentId) REFERENCES paymentTable (id)
);



CREATE TABLE orderItemTable
(
    -- Hérité de BaseEntity
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    uuid          BINARY(16) NOT NULL UNIQUE,
    version       BIGINT         NOT NULL,

    -- Champs spécifiques à OrderItemEntity
    quantity      INTEGER        NOT NULL,
    unitPrice     DECIMAL(10, 2) NOT NULL,
    subtotal      DECIMAL(10, 2) NOT NULL,

    -- Relations ManyToOne
    orderId       BIGINT         NOT NULL,
    productEntity BIGINT         NOT NULL, -- Supposé être la clé étrangère vers productTable

    PRIMARY KEY (id),

    -- Contraintes de clés étrangères
    FOREIGN KEY (orderId) REFERENCES orderTable (id),
    -- On suppose l'existence de la table productTable
    FOREIGN KEY (productEntity) REFERENCES productTable (id)
);