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
    addressStreet             VARCHAR(255),
    addressCity               VARCHAR(255),
    addressZipCode           VARCHAR(20),
    addressCountry            VARCHAR(255),
    birthDate                  DATE,
    keycloakId                 VARCHAR(100) NOT NULL UNIQUE,
    role                       VARCHAR(255) NOT NULL, -- EnumType.STRING
    phoneNumber                VARCHAR(50),
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
    -- L'annotation 'mappedBy' sur paymentEntity dans OrderEntity indique
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
    cardNumber     VARCHAR(100) NOT NULL,
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

CREATE TABLE cartTable
(
    -- Hérité de BaseEntity
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    uuid          BINARY(16)   NOT NULL UNIQUE,
    version       BIGINT       NOT NULL,

    -- Champs spécifiques à CartEntity
    userId        BIGINT       NOT NULL,
    createdAt     DATETIME     NOT NULL,
    updatedAt     DATETIME,
    isCheckedOut  BOOLEAN      NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    FOREIGN KEY (userId) REFERENCES userTable (id)
);

CREATE TABLE cartItemTable
(
    -- Hérité de BaseEntity
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    uuid          BINARY(16)     NOT NULL UNIQUE,
    version       BIGINT         NOT NULL,

    -- Champs spécifiques à CartItemEntity
    quantity      INTEGER        NOT NULL,
    unitPrice     DECIMAL(10, 2) NOT NULL,
    cartId        BIGINT         NOT NULL,
    productEntity BIGINT         NOT NULL, -- FK to productTable
    addedAt       DATETIME       NOT NULL,

    PRIMARY KEY (id),
    FOREIGN KEY (cartId) REFERENCES cartTable (id),
    FOREIGN KEY (productEntity) REFERENCES productTable (id)
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
    currency         VARCHAR(10)    NOT NULL,
    shippingStreet   VARCHAR(255)   NOT NULL,
    shippingCity     VARCHAR(255)   NOT NULL,
    shippingZipCode  VARCHAR(20)    NOT NULL,
    shippingCountry  VARCHAR(255)   NOT NULL,
    status           VARCHAR(255)   NOT NULL, -- EnumType.STRING
    shippingType     VARCHAR(255)   NOT NULL, -- EnumType.STRING
    shippingProvider VARCHAR(255)   NOT NULL, -- EnumType.STRING
    discountType     VARCHAR(255)   NOT NULL, -- EnumType.STRING
    updatedAt        DATETIME,

    -- Relations ManyToOne et OneToOne
    userId           BIGINT,                  -- Vers UserEntity
    paymentId        BIGINT,                  -- Vers PaymentEntity
    cartId           BIGINT,                  -- Vers CartEntity

    PRIMARY KEY (id),

    -- Contraintes de clés étrangères (PaymentEntity est également déduit)
    FOREIGN KEY (userId) REFERENCES userTable (id),
    -- On suppose l'existence de la table paymentTable
    FOREIGN KEY (paymentId) REFERENCES paymentTable (id),
    FOREIGN KEY (cartId) REFERENCES cartTable (id)
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


-- Autogenerated: do not edit this file

CREATE TABLE BATCH_JOB_INSTANCE  (
	JOB_INSTANCE_ID BIGINT  NOT NULL PRIMARY KEY ,
	VERSION BIGINT ,
	JOB_NAME VARCHAR(100) NOT NULL,
	JOB_KEY VARCHAR(32) NOT NULL,
	constraint JOB_INST_UN unique (JOB_NAME, JOB_KEY)
) ENGINE=InnoDB;

CREATE TABLE BATCH_JOB_EXECUTION  (
	JOB_EXECUTION_ID BIGINT  NOT NULL PRIMARY KEY ,
	VERSION BIGINT  ,
	JOB_INSTANCE_ID BIGINT NOT NULL,
	CREATE_TIME DATETIME(6) NOT NULL,
	START_TIME DATETIME(6) DEFAULT NULL ,
	END_TIME DATETIME(6) DEFAULT NULL ,
	STATUS VARCHAR(10) ,
	EXIT_CODE VARCHAR(2500) ,
	EXIT_MESSAGE VARCHAR(2500) ,
	LAST_UPDATED DATETIME(6),
	constraint JOB_INST_EXEC_FK foreign key (JOB_INSTANCE_ID)
	references BATCH_JOB_INSTANCE(JOB_INSTANCE_ID)
) ENGINE=InnoDB;

CREATE TABLE BATCH_JOB_EXECUTION_PARAMS  (
	JOB_EXECUTION_ID BIGINT NOT NULL ,
	PARAMETER_NAME VARCHAR(100) NOT NULL ,
	PARAMETER_TYPE VARCHAR(100) NOT NULL ,
	PARAMETER_VALUE VARCHAR(2500) ,
	IDENTIFYING CHAR(1) NOT NULL ,
	constraint JOB_EXEC_PARAMS_FK foreign key (JOB_EXECUTION_ID)
	references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ENGINE=InnoDB;

CREATE TABLE BATCH_STEP_EXECUTION  (
	STEP_EXECUTION_ID BIGINT  NOT NULL PRIMARY KEY ,
	VERSION BIGINT NOT NULL,
	STEP_NAME VARCHAR(100) NOT NULL,
	JOB_EXECUTION_ID BIGINT NOT NULL,
	CREATE_TIME DATETIME(6) NOT NULL,
	START_TIME DATETIME(6) DEFAULT NULL ,
	END_TIME DATETIME(6) DEFAULT NULL ,
	STATUS VARCHAR(10) ,
	COMMIT_COUNT BIGINT ,
	READ_COUNT BIGINT ,
	FILTER_COUNT BIGINT ,
	WRITE_COUNT BIGINT ,
	READ_SKIP_COUNT BIGINT ,
	WRITE_SKIP_COUNT BIGINT ,
	PROCESS_SKIP_COUNT BIGINT ,
	ROLLBACK_COUNT BIGINT ,
	EXIT_CODE VARCHAR(2500) ,
	EXIT_MESSAGE VARCHAR(2500) ,
	LAST_UPDATED DATETIME(6),
	constraint JOB_EXEC_STEP_FK foreign key (JOB_EXECUTION_ID)
	references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ENGINE=InnoDB;

CREATE TABLE BATCH_STEP_EXECUTION_CONTEXT  (
	STEP_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
	SHORT_CONTEXT VARCHAR(2500) NOT NULL,
	SERIALIZED_CONTEXT TEXT ,
	constraint STEP_EXEC_CTX_FK foreign key (STEP_EXECUTION_ID)
	references BATCH_STEP_EXECUTION(STEP_EXECUTION_ID)
) ENGINE=InnoDB;

CREATE TABLE BATCH_JOB_EXECUTION_CONTEXT  (
	JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
	SHORT_CONTEXT VARCHAR(2500) NOT NULL,
	SERIALIZED_CONTEXT TEXT ,
	constraint JOB_EXEC_CTX_FK foreign key (JOB_EXECUTION_ID)
	references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ENGINE=InnoDB;

CREATE TABLE BATCH_STEP_EXECUTION_SEQ (
	ID BIGINT NOT NULL,
	UNIQUE_KEY CHAR(1) NOT NULL,
	constraint UNIQUE_KEY_UN unique (UNIQUE_KEY)
) ENGINE=InnoDB;

INSERT INTO BATCH_STEP_EXECUTION_SEQ (ID, UNIQUE_KEY) select * from (select 0 as ID, '0' as UNIQUE_KEY) as tmp where not exists(select * from BATCH_STEP_EXECUTION_SEQ);

CREATE TABLE BATCH_JOB_EXECUTION_SEQ (
	ID BIGINT NOT NULL,
	UNIQUE_KEY CHAR(1) NOT NULL,
	constraint UNIQUE_KEY_UN unique (UNIQUE_KEY)
) ENGINE=InnoDB;

INSERT INTO BATCH_JOB_EXECUTION_SEQ (ID, UNIQUE_KEY) select * from (select 0 as ID, '0' as UNIQUE_KEY) as tmp where not exists(select * from BATCH_JOB_EXECUTION_SEQ);

CREATE TABLE BATCH_JOB_SEQ (
	ID BIGINT NOT NULL,
	UNIQUE_KEY CHAR(1) NOT NULL,
	constraint UNIQUE_KEY_UN unique (UNIQUE_KEY)
) ENGINE=InnoDB;

INSERT INTO BATCH_JOB_SEQ (ID, UNIQUE_KEY) select * from (select 0 as ID, '0' as UNIQUE_KEY) as tmp where not exists(select * from BATCH_JOB_SEQ);
