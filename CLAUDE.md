Before starting, write PLAN.md with a checklist of every class/package to be tested. Update the checklist after each subagent completes. If we hit a usage limit, I can resume by reading PLAN.md.
After each subagent completes, run `mvn test -pl <module>` and fix any failures before dispatching the next subagent. Do not proceed with red tests.
Estimate how much of this work fits in a 2-hour session without hitting usage caps. Do only that scope, commit the results with a clear message, and tell me exactly what to ask next session to continue.

If a progress.md file exists (or create one), use it to have an overview of the project context, what has been done and 
what to do next, divide the progress.md in 3 differents seciton with the first section being a SITTREP, the second 
section being a list of the task that has been done and the following tasks and the 3rd section being remarqs related to
these taks.

project context :

# Cybertech E-Commerce Backend — Project Context

    ## Purpose
    Backend Spring Boot pour une plateforme e-commerce vendant des produits informatiques (ordinateurs, écrans, smartphones, claviers). Couvre le cycle complet : catalogue produit, panier, wishlist, commande,
    paiement, livraison, notifications, reviews/moderation, gestion utilisateur.

    ## Stack technique

    **Core**
    - Java 26 avec preview features (`--enable-preview`)
    - Spring Boot 4.0.4, Spring Cloud 2025.1.1
    - Maven (wrapper `./mvnw`)
    - Lombok + MapStruct 1.6.3

    **Persistance**
    - MySQL 8.4 (JPA / Hibernate)
    - MongoDB (events utilisateur)
    - Elasticsearch 7.17.10 (recherche produit)
    - Redis (cache panier, locks distribués, TTL jitter)
    - H2 (fallback tests slice)
    - Hypersistence Utils

    **Sécurité / Auth**
    - Keycloak 26.0.4 (admin client + OAuth2 resource server)
    - Spring Security avec `KeycloakRoleConverter` custom
    - JWT via jjwt 0.12.6

**Paiement / Email / Stockage**
- Stripe Java SDK 31.4.0 (PaymentIntent + webhooks HMAC-SHA256)
- AWS S3 (spring-cloud-aws-starter-s3 4.0.0) pour images produit
- Spring Mail + Thymeleaf

    **Asynchrone / Batch**
    - Spring Kafka (events catalogue)
    - Spring Batch (cleanup réservations, cancel pending, ship paid, daily summary)
    - `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)`

    **Resilience / Config**
    - Resilience4j, Spring Cloud Vault (secrets), springdoc-openapi 3.0.1

    **Test stack**
    - JUnit 5 + Mockito + AssertJ
    - MockMvc (`@WebMvcTest` + `TestSecurityConfig`)
    - Testcontainers 1.21.4 (MySQL, Redis, Mongo, Elasticsearch, Kafka) pour ITs Failsafe (`*IT.java`)
    - Spring Cloud Contract Stub Runner, spring-batch-test, spring-restdocs-mockmvc
    - JaCoCo 0.8.14 (gate BUNDLE 80% line+branch, `haltOnFailure=true`)
    - ArchUnit 1.4.1, Datafaker 2.5.3
    - Jackson 3 (`tools.jackson.databind.ObjectMapper`)

    ## Architecture / Conventions


    **Packages sous `com.novatech.cybertech`**
    - `api/controllers/{implementation,spec}/` — controllers REST + interfaces OpenAPI
    - `api/error/{enumpackage,model}/` — `ErrorManagementController` (@ControllerAdvice), ErrorCode, ErrorResponseDto
    - `services/{core,implementation}/` — interfaces + impls (toujours interface avant impl)
    - `entities/{enums,valueObjects,document,attributes,validator}/` — entités JPA héritant de `BaseEntity` (UUID auto via `@PrePersist`)
    - `repositories/` — Spring Data JPA + Mongo + Elasticsearch
    - `mappers/{entity,document}/` — MapStruct (impls générés dans `target/generated-sources`)
    - `dto/{request,response}/` — DTOs Jackson 3
    - `events/` + `listener/` + `dispatcher/` — events domaine + listeners AFTER_COMMIT + dispatchers
    - `factory/` — Discount, Notification, Payment, ShippingProvider factories
    - `strategy/discount/` — strategy pattern (BlackFriday, etc.)
    - `validator/{core,implementation}/` — ChainableOrderValidator, ActiveUserValidator, BankCardValidityValidator, ProductValidationService
    - `batch/{base,job,task}/` — Spring Batch
    - `clients/` — clients HTTP externes
    - `exceptions/` — ~40 exceptions custom, toutes mappées dans `ErrorManagementController`

    **Conventions code**
    - Lombok partout (`@Data`, `@Builder`, `@SuperBuilder`, `@Slf4j`, `@RequiredArgsConstructor`)
    - Streams plutôt que for-loops
    - Interface avant impl systématique
    - Nouvelles entités étendent `BaseEntity`

- Streams plutôt que for-loops
  - Interface avant impl systématique
  - Nouvelles entités étendent `BaseEntity`

  **Conventions test**
  - Pattern reference : `ReviewCrudControllerTest` (constants endpoints, `@WebMvcTest`, `@Import(TestSecurityConfig.class)`, `@MockitoBean`, MockMvc, JSON STRICT, `csrf()` + JWT via `JwtTestUtils`)
  - Services : `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`, pas de Spring context
  - ITs : `*IT.java` (Failsafe) + `@SpringBootTest` + `@Testcontainers`
  - Bug-pinning : test `@Disabled("BUG-xxx")` asserte contrat correct + companion passing test pin comportement cassé
  - Fixtures partagées sous `src/test/java/com/novatech/cybertech/fixtures/{builders,dto,assertions,support,support/stubs}/`

  **API versioning** : `spring.mvc.apiversion.enabled=true`, default `1.0`. Endpoints sous `/api/v1/services/...`.

  **Profiles** : `test` (H2 + stubs), `integration-test` (Testcontainers ITs).




Add as a new ## Testing Standards section near the top of CLAUDE.md, or under an existing Quality section\n\n## Testing Standards
- Target 80%+ test coverage for new code
- When generating tests in bulk, run the full test suite after each subagent completes to catch broken tests early
- Fix any broken tests before moving to the next task
  Add as a new ## Long-Running Work section in CLAUDE.md\n\n## Long-Running Work
- For large test/doc generation tasks, checkpoint progress to a status file (e.g., PROGRESS.md) after each major milestone
- Prefer sequential verified steps over maximally-parallel subagents to reduce rework when usage limits interrupt execution
  Add as a ## Project Context section at the top of CLAUDE.md\n\n## Project Context
- This is a Spring Boot e-commerce application (Java)
- Use JUnit 5 and Mockito for tests
- Follow existing package structure and naming conventions