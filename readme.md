# Cybertech — E-Commerce Backend Platform

A production-grade e-commerce backend built with **Spring Boot 4** and **Java 21**, implementing a clean monolithic architecture with domain-driven separation of concerns and enterprise-grade patterns.

---

## Overview

Cybertech is a full-featured online marketplace backend covering the complete customer journey: browsing products, managing a shopping cart, placing orders, processing payments, and receiving notifications. It is designed with scalability in mind, using industry-standard tooling across authentication, caching, search, and infrastructure — and is structured to evolve toward a microservices architecture if needed.

---

## Features

| Domain | Capabilities |
|---|---|
| **User Management** | OAuth2/OIDC via Keycloak, role-based access (USER / ADMIN), user registration |
| **Product Catalog** | CRUD, multi-brand/category support, image storage (S3), JSON attributes, stock tracking |
| **Search & Discovery** | Full-text search (Elasticsearch), product recommendations (Gorse ML engine) |
| **Shopping Cart** | Cart lifecycle management, Redis-backed persistence with TTL |
| **Orders** | Full order lifecycle: PENDING → PAID → SHIPPED → DELIVERED |
| **Payments** | Stripe integration with webhook handling, refund support, and transaction logging |
| **Inventory** | Concurrent stock reservation with Redis TTL, optimistic/pessimistic locking, oversell prevention |
| **Notifications** | Event-driven multi-channel dispatch (email, SMS) with templated messages |
| **Reviews & Ratings** | User-submitted product reviews with content moderation |
| **Wishlists** | Per-user wishlist management |
| **Batch Jobs** | Scheduled order status updates, stock cleanup, automatic order cancellation |

---

## Architecture

Cybertech follows a **layered monolithic architecture** with clear domain boundaries:

```
Controller → Service → Repository → Entity
```

Key architectural patterns in use:

- **Event-Driven** — Spring `ApplicationEventPublisher` with `@TransactionalEventListener` for post-commit domain events (`OrderCreatedEvent`, `OrderPaidEvent`, `PaymentSucceededEvent`, …)
- **Strategy Pattern** — Pluggable payment processors, discount strategies, notification channels, and shipping providers, wired via annotated Maps
- **Chain of Responsibility** — Order validator chain (active user check → bank card validity check)
- **Factory / Dispatcher** — `NotificationDispatcher`, `ShippingDispatcher` for dynamic strategy selection
- **Repository Pattern** — Spring Data repositories for all data access
- **DTO Mapping** — Compile-time MapStruct mappers between entities and request/response DTOs
- **Concurrency Safety** — Redis TTL-based stock reservations, keyspace notifications for automatic expiry, pessimistic DB locks to prevent overselling

---

## Technology Stack

### Core
| | |
|---|---|
| Language | Java 21 (virtual threads enabled) |
| Framework | Spring Boot 4.0.4 |
| Build | Maven 3.x |

### Spring Ecosystem
- Spring Security (OAuth2 Resource Server, JWT)
- Spring Data JPA / MongoDB / Elasticsearch / Redis
- Spring Batch (scheduled jobs)
- Spring Cloud Vault (secrets management)
- Spring Cloud AWS (S3 integration)
- Spring Kafka (event bus, prepared for future use)
- Spring Mail, Spring Cache, Spring Async, Spring Events

### Authentication & Security
- **Keycloak 24** — OAuth2 / OIDC identity provider
- **JWT** (JJWT 0.12.6) — stateless token validation
- Role-based access control with custom Keycloak JWT role converter
- HashiCorp **Vault** for secrets management

### Databases & Storage
| Store | Purpose |
|---|---|
| **MySQL 9.3** | Primary relational store (orders, products, payments, users) |
| **Redis 7** | Caching (cart, user existence), stock reservations with TTL, keyspace notifications |
| **MongoDB 8** | User event / analytics document store |
| **Elasticsearch 9** | Full-text product search and indexing |
| **AWS S3** (LocalStack in dev) | Product image storage |

### External Services
| Service | Purpose |
|---|---|
| **Stripe** (SDK 31.4.0) | Payment processing, webhooks, refunds |
| **Gorse** | ML-based product recommendation engine |
| **Moderation API** | Custom Python/Flask service for review content moderation |
| **Mailpit** | SMTP testing server (dev) |

### Infrastructure & DevOps
| Tool | Purpose |
|---|---|
| **Docker / Docker Compose** | Local containerised environment |
| **Kubernetes + Helm** | Production-grade orchestration (charts for every service) |
| **Skaffold** | Local Kubernetes development loop |
| **Jib** | Maven plugin for OCI-compliant container image builds |
| **GitHub Actions** | CI pipeline (build, test, code quality) |
| **Qodana** | Static code analysis in CI |

### Testing
- **JUnit 5** + **Mockito** — unit tests
- **TestContainers** — integration tests with real MySQL, Kafka, and Elasticsearch containers
- **ArchUnit** — architecture compliance tests
- **Spring REST Docs** — API documentation generated from tests
- **Spring Batch Test** — batch job testing
- **DataFaker** — test data generation

### Code Quality & Utilities
- **Lombok** — boilerplate reduction
- **MapStruct** — compile-time DTO mapping
- **Resilience4j** — circuit breakers
- **Apache Commons** (Collections4, Lang3)
- **springdoc-openapi** — Swagger / OpenAPI 3 documentation at `/swagger-ui`
- **Spring Boot Actuator** — health checks and metrics

---

## API

RESTful API with JSON payloads, versioned via the `X-API-VERSION` header (versions 1.0, 2.0, 3.0).

| Prefix | Domain |
|---|---|
| `/api/v1/services/product/**` | Product catalog |
| `/api/v1/services/cart/**` | Cart management |
| `/api/v1/services/order/**` | Orders |
| `/api/v1/services/user/**` | User accounts |
| `/api/v1/webhooks/stripe` | Stripe webhook receiver |

Interactive docs available at `/swagger-ui` when the application is running.

### Authentication flow

```
Client (e.g. Angular frontend)
    │
    ▼
Keycloak ── JWT ──▶ Spring Boot Backend (Resource Server)
```

The backend stores no passwords. Keycloak is the identity source; users are linked via the `sub` claim of the JWT.

### Stock reservation flow (Redis)

1. User places an order
2. Stock is reserved in MySQL (`reservedStock`)
3. A Redis key with TTL is created: `reservation:order:<uuid>`
4. Outcomes:
   - Payment succeeds → stock consumed
   - Payment fails → stock released immediately
   - Abandoned → Redis key expires → `RedisExpirationListener` releases stock automatically
5. Pessimistic locks in the DB prevent overselling under concurrent load

---

## Local Development

### Prerequisites
- Java 21
- Docker & Docker Compose
- Maven 3.x

### Start all infrastructure services
```bash
docker compose up -d
```

Services started: MySQL, MongoDB, Redis, Elasticsearch, Keycloak, Vault, LocalStack (S3), Mailpit, Gorse, Stripe CLI, Moderation API.

### Run the application
```bash
./mvnw spring-boot:run
```

### Run tests
```bash
./mvnw test
```

### Kubernetes (Skaffold)
```bash
skaffold dev
```

---

## Author

Developed by **Loïc GOTTOH**, backend Java developer focused on software architecture, distributed systems, and data consistency.
