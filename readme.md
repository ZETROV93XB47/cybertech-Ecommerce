# 🛒 Cybertech – Backend E-commerce (Spring Boot)

**Cybertech** est un projet e-commerce backend développé en **Java avec Spring Boot**, conçu comme un **monolithe modulaire évolutif**, avec une architecture claire et des choix techniques proches de ceux rencontrés en production.

L’objectif du projet est double :
- mettre en œuvre des **bonnes pratiques backend** (architecture, sécurité, transactions, concurrence)
- servir de **support pédagogique et démonstrateur technique** (monolithe → microservices)

---

## 🚀 Fonctionnalités principales

### 🧑‍💼 Utilisateurs & Sécurité
- Authentification et autorisation via **Keycloak**
- Standard **OAuth2 / OpenID Connect (OIDC)**
- Gestion des rôles (USER / ADMIN)
- Backend Spring Boot configuré en **OAuth2 Resource Server**
- JWT validés automatiquement (stateless)

### 🛍️ Catalogue & Produits
- Gestion des produits
- Recherche et indexation via **Elasticsearch**
- Gestion du stock et du stock réservé
- Protection contre l’**overselling**

### 📦 Commandes
- Création et gestion des commandes
- Workflow de commande clair :
  - validation
  - réservation du stock
  - paiement
  - confirmation
- Historique des commandes par utilisateur

### 💳 Paiement
- Simulation d’un workflow de paiement
- Gestion des statuts (SUCCESS / FAILED / PENDING)
- Intégration transactionnelle avec la commande

### 🏗️ Gestion avancée du stock
- Réservations temporaires de stock
- Système robuste face aux requêtes concurrentes
- Utilisation combinée de :
  - Base relationnelle (source de vérité)
  - Redis (TTL & gestion du temps)
- Libération automatique du stock via **RedisExpirationListener**
- Verrous pessimistes pour garantir la cohérence

---

## 🧠 Architecture & choix techniques

### ⚙️ Architecture générale
- Monolithe Spring Boot structuré par domaines
- Séparation claire des responsabilités :
  - Order
  - Stock
  - Payment
  - User
  - Notification
- Architecture pensée pour une **évolution vers les microservices**

### 🔁 Gestion événementielle
- Events métier via `ApplicationEventPublisher`
- Écoute post-transaction via `@TransactionalEventListener`
- Préparation possible à Kafka / event-driven architecture

### 🗄️ Persistance
- Base relationnelle (MySQL / PostgreSQL)
- JPA / Hibernate
- Gestion fine des transactions
- Versionnement des entités (`@Version` – optimistic locking)

---

## 🧰 Stack technique

### Backend
- Java 21+
- Spring Boot 3
- Spring Security
- Spring Data JPA
- Spring Data Redis
- Spring Events
- Lombok

### Sécurité
- Keycloak
- OAuth2
- OpenID Connect (OIDC)
- JWT

### Données
- MySQL (ou PostgreSQL)
- Redis (in-memory + TTL)
- Elasticsearch

### Infrastructure & DevOps
- Docker
- Docker Compose
- Redis Keyspace Notifications
- Mailhog (envoi d’e-mails en développement)
- LocalStack (exploration DynamoDB – optionnel)

---

## 🔐 Workflow d’authentification

```
Frontend (Angular)
    │
    ▼
Keycloak ── JWT ──▶ Spring Boot Backend
```

- Le backend ne gère **aucun mot de passe**
- Keycloak est la source d’identité
- L’utilisateur métier est lié via le claim `sub` du token

---

## 🕒 Workflow de réservation de stock (Redis)

1. L’utilisateur déclenche une commande
2. Le stock est **réservé en base** (reservedStock)
3. Création d’une clé Redis avec TTL :
   `reservation:order:<uuid>`
4. Cas possibles :
   - paiement OK → stock consommé
   - paiement KO → stock libéré
   - abandon → expiration Redis → libération automatique
5. Le système empêche l’overselling même en forte concurrence

---

## 🧪 Environnement de développement

L’environnement local est entièrement **dockerisé** via Docker Compose.

Services embarqués :
- MySQL
- Redis
- Elasticsearch
- Keycloak
- Mailhog

Configuration simplifiée via un fichier `.env`.

---

## 🎯 Objectifs pédagogiques

- Concevoir une **architecture backend réaliste**
- Comprendre les enjeux de :
  - transactions
  - concurrence
  - cohérence des données
  - sécurité moderne
- Préparer une migration vers :
  - microservices
  - Kafka / event-driven
- Servir de **support technique en entretien**

---

## 🔮 Évolutions possibles

- Migration vers une architecture microservices
- Introduction de Kafka + Outbox Pattern
- Extraction d’un Stock Service dédié
- Ajout d’un frontend Angular
- Monitoring (Prometheus, Grafana)
- Intégration DynamoDB (LocalStack)

---

## 👨‍💻 Auteur

Développé par **Hideyoshi**, développeur backend Java,
passionné par :
- l’architecture logicielle
- les systèmes distribués
- la performance et la cohérence des données

