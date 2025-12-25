
-- Table pour l'entité OrderEntity
CREATE TABLE orderTable (
    -- Colonnes de BaseEntity
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid BINARY(16) NOT NULL UNIQUE,

    -- Colonnes spécifiques à OrderEntity
    orderDate DATETIME NOT NULL,
    totalAmount DECIMAL(19, 4) NOT NULL DEFAULT 0.00,
    shippingAddress VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,

    -- Clés étrangères
    userId BIGINT,
    paymentId BIGINT UNIQUE, -- La contrainte UNIQUE est cruciale pour la relation @OneToOne

    -- Définition des contraintes de clé étrangère
    CONSTRAINT fk_order_user FOREIGN KEY (userId) REFERENCES user_entity(id),
    CONSTRAINT fk_order_payment FOREIGN KEY (paymentId) REFERENCES payment_entity(id)
);



-- ========= TABLES PRINCIPALES DÉFINIES DANS LA QUESTION =========

-- Table pour l'entité UserEntity
CREATE TABLE userTable (
    -- Colonnes de BaseEntity
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid BINARY(16) NOT NULL UNIQUE,

    -- Colonnes spécifiques à UserEntity
    email VARCHAR(50) NOT NULL UNIQUE,
    firstName VARCHAR(50) NOT NULL,
    lastName VARCHAR(50) NOT NULL,
    sex VARCHAR(20) NOT NULL,
    address VARCHAR(255),
    birthDate DATE,
    password VARCHAR(100),
    role VARCHAR(50) NOT NULL
);

-- Table pour l'entité ProductEntity
CREATE TABLE productTable (
    -- Colonnes de BaseEntity
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid BINARY(16) NOT NULL UNIQUE,

    -- Colonnes spécifiques à ProductEntity
    name VARCHAR(255) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    brand VARCHAR(255) NOT NULL,
    category VARCHAR(255) NOT NULL,
    cpu VARCHAR(255) NOT NULL,
    gpu CHAR(50) NOT NULL,         -- Respecte columnDefinition = "char(50)"
    ram CHAR(50) NOT NULL,         -- Respecte columnDefinition = "char(50)"
    ssd CHAR(50) NOT NULL,         -- Respecte columnDefinition = "char(50)"
    displayType VARCHAR(255),
    displaySize VARCHAR(255),
    os CHAR(20) NOT NULL,           -- Respecte columnDefinition = "char(20)"
    connectivity VARCHAR(255),
    photo VARCHAR(255),            -- Typiquement une URL ou un chemin de fichier
    stock INT,
    description TEXT,              -- Respecte columnDefinition = "text"

    INDEX idx_product_brand (brand),      -- Index pour la recherche par marque
    INDEX idx_product_category (category) -- Index pour la recherche par catégorie
);

-- Table pour l'entité OrderItemEntity
CREATE TABLE orderItemTable (
    -- Colonnes de BaseEntity
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid BINARY(16) NOT NULL UNIQUE,

    -- Colonnes spécifiques à OrderItemEntity
    quantity INT NOT NULL,
    unitPrice DECIMAL(10, 2) NOT NULL,
    subtotal DECIMAL(10, 2) NOT NULL,

    -- Clés étrangères
    orderId BIGINT NOT NULL,
    productEntity BIGINT NOT NULL,

    -- Définition des contraintes de clé étrangère
    CONSTRAINT fk_orderitem_order_id FOREIGN KEY (orderId) REFERENCES orderTable(id),
    CONSTRAINT fk_orderitem_product_id FOREIGN KEY (productEntity) REFERENCES productTable(id)
);

-- ========= TABLE POUR L'ENTITÉ PaymentEntity =========

CREATE TABLE paymentTable (
    -- Colonnes de BaseEntity
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid BINARY(16) NOT NULL UNIQUE,

    -- Colonnes spécifiques à PaymentEntity
    amount DECIMAL(19, 4) NOT NULL, -- Précision standard pour les montants
    paymentType VARCHAR(50) NOT NULL,
    paymentStatus VARCHAR(50) NOT NULL,
    paymentDate DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP -- Traduction de @CreationTimestamp

    -- Note : La colonne pour la relation @OneToOne se trouve dans la table 'orderTable'
    -- car cette entité est le côté "mappedBy" (inverse) de la relation.
);


-- ========= TABLE POUR L'ENTITÉ BankCardEntity =========

CREATE TABLE bankCardTable (
    -- Colonnes de BaseEntity
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid BINARY(16) NOT NULL UNIQUE,

    -- Colonnes spécifiques à BankCardEntity
    cardHolderName VARCHAR(100) NOT NULL,
    cardNumber VARCHAR(19) NOT NULL,
    expiryDate VARCHAR(7) NOT NULL, -- Format MM/YYYY
    cvv VARCHAR(4) ,
    cardType VARCHAR(50) NOT NULL,
    isDefault BOOLEAN NOT NULL DEFAULT FALSE,

    -- Clé étrangère vers UserEntity
    user_id BIGINT NOT NULL,

    -- Définition de la contrainte de clé étrangère
    CONSTRAINT fk_bankcard_user FOREIGN KEY (user_id) REFERENCES userTable(id)
);


-- ========= TABLE POUR L'ENTITÉ ReviewEntity =========

CREATE TABLE reviewTable (
    -- Colonnes de BaseEntity
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid BINARY(16) NOT NULL UNIQUE,

    -- Colonnes spécifiques à ReviewEntity
    rating INT NOT NULL,
    comment TEXT,
    createdAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,         -- Traduction de @CreationTimestamp
    updatedAt DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Traduction de @UpdateTimestamp

    -- Clés étrangères
    userId BIGINT NOT NULL,
    reviewId BIGINT NOT NULL, -- Nom de colonne basé sur votre @JoinColumn

    -- Définition des contraintes de clé étrangère
    CONSTRAINT fk_review_user FOREIGN KEY (userId) REFERENCES userTable(id),
    CONSTRAINT fk_review_product FOREIGN KEY (reviewId) REFERENCES productTable(id),

    -- Contrainte pour valider le rating au niveau de la base de données
    CONSTRAINT chk_rating CHECK (rating >= 1 AND rating <= 5)
);