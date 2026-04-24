# Discount Campaign Management — Design Spec
**Date:** 2026-04-25  
**Status:** Approved  
**Approach:** Option 2 — DB-driven campaigns, `DiscountCalculationType → Strategy` factory, Redis cache

---

## Context

The existing discount system embeds percentage values directly in the `DiscountType` enum and uses `ActiveDiscountsProperties` (a `@ConfigurationProperties` bean) to toggle discounts on/off via `application.properties`. Changing a discount requires a config change and redeploy (or a Spring Cloud Config refresh). There is only one implemented strategy (`BlackFridayDiscountStrategy`), and the `DiscountStrategy` interface only accepts `baseAmount`, making BOGO impossible to implement correctly.

**This spec replaces that system with a DB-driven campaign model with Redis caching and a full admin API.**

---

## Decision: Clean Break

- Delete `ActiveDiscountsProperties`
- Delete `BlackFridayDiscountStrategy`
- Remove `discountPercentage` field from `DiscountType` enum
- Rework `@DiscountTypeHandler` to target `DiscountCalculationType[]`
- No backward-compatibility shim

---

## Section 1 — Data Layer

### New enum: `DiscountCalculationType`
```
NONE, PERCENTAGE, FIXED_AMOUNT, BUY_ONE_GET_ONE_FREE
```
Package: `entities/enums/`

### `DiscountType` enum cleanup
Remove `discountPercentage` float field and constructor. Enum becomes a plain identifier.

### `DiscountCampaignEntity` (extends `BaseEntity<Long>`)
Table: `discount_campaign`

| Column | JPA type | Nullable | Notes |
|--------|----------|----------|-------|
| `discountType` | `@Enumerated(STRING)` | NOT NULL UNIQUE | maps to `DiscountType` |
| `calculationType` | `@Enumerated(STRING)` | NOT NULL | maps to `DiscountCalculationType` |
| `enabled` | `boolean` | NOT NULL | runtime on/off switch |
| `percentage` | `BigDecimal` | yes | PERCENTAGE strategy |
| `fixedAmount` | `BigDecimal` | yes | FIXED_AMOUNT strategy |
| `minOrderAmount` | `BigDecimal` | yes | optional minimum order guard |
| `maxDiscountAmount` | `BigDecimal` | yes | optional discount cap |
| `startsAt` | `LocalDateTime` | yes | null = no start constraint |
| `endsAt` | `LocalDateTime` | yes | null = no end constraint |
| `priority` | `Integer` | yes | display/sort order |

Inherits: `id`, `uuid`, `createdAt`, `updatedAt`, `version` from `BaseEntity`.

### `DiscountCampaignRepository`
```java
Optional<DiscountCampaignEntity> findByDiscountType(DiscountType type);
List<DiscountCampaignEntity> findAll();
void deleteByDiscountType(DiscountType type);
boolean existsByDiscountType(DiscountType type);
```

### SQL init files
Add `CREATE TABLE discount_campaign (...)` to both:
- `src/main/resources/sql/databaseSchemaInitFile.sql`
- `src/main/resources/k8s/helm/charts/mysql-chart/databaseSchemaInitFile.sql`

---

## Section 2 — Service & Cache Layer

### `DiscountContext` record
Internal DTO — not exposed via API. Must be Jackson-serializable for Redis.
```java
record DiscountContext(
    DiscountType discountType,
    DiscountCalculationType calculationType,
    BigDecimal percentage,
    BigDecimal fixedAmount,
    BigDecimal minOrderAmount,
    BigDecimal maxDiscountAmount,
    LocalDateTime startsAt,
    LocalDateTime endsAt
)
```
Package: `dto/data/`

### `DiscountCampaignService`
Interface + implementation in `services/core/` and `services/implementation/`.

**`getActiveDiscountContext(DiscountType)`** — three validation gates:
1. Row not found → `DiscountTypeNotActiveException("Discount X does not exist")`
2. `enabled = false` → `DiscountTypeNotActiveException("Discount X is disabled")`
3. `startsAt` in future or `endsAt` in past → `DiscountTypeNotActiveException("Discount X has not started yet / has expired")`

On success, maps entity to `DiscountContext` record.

### Redis Cache
- Cache name: `"discountCampaigns"`
- `@Cacheable(value = "discountCampaigns", key = "#discountType.name()")` on `getActiveDiscountContext()`
- `@CacheEvict(value = "discountCampaigns", key = "#discountType.name()")` on update/delete
- `@CacheEvict(value = "discountCampaigns", allEntries = true)` on create
- `DiscountContext` is a record — Jackson-serializable without extra config

### `OrderPriceCalculationServiceImp` changes
- Remove `ActiveDiscountsProperties` dependency
- Wire `DiscountCampaignService`
- New flow: `getActiveDiscountContext(type)` → extract `calculationType` → factory lookup → `calculateDiscount(baseAmount, items, context)`
- `NONE` short-circuits before factory lookup (returns `BigDecimal.ZERO`)

---

## Section 3 — Strategy Layer

### `@DiscountTypeHandler` rework
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DiscountTypeHandler {
    DiscountCalculationType[] value();
}
```

### `DiscountStrategy` interface — widened signature
```java
BigDecimal calculateDiscount(BigDecimal baseAmount, List<OrderItemPriceDto> items, DiscountContext context);
```

### Four strategy beans

| Class | `@DiscountTypeHandler` | Logic |
|-------|------------------------|-------|
| `PercentageDiscountStrategy` | `PERCENTAGE` | `baseAmount × percentage / 100`, capped by `maxDiscountAmount` |
| `FixedAmountDiscountStrategy` | `FIXED_AMOUNT` | `min(fixedAmount, baseAmount)`, respects `minOrderAmount` |
| `BuyOneGetOneFreeDiscountStrategy` | `BUY_ONE_GET_ONE_FREE` | `Σ floor(qty/2) × unitPrice` per line |
| `NoDiscountStrategy` | `NONE` | always `BigDecimal.ZERO` |

### `DiscountStrategyFactory` rework
Maps `DiscountCalculationType → DiscountStrategy`. Built in `AppConfig` from `@DiscountTypeHandler` annotations (same pattern as `paymentServiceMap`).

### Deleted files
- `BlackFridayDiscountStrategy.java`
- `ActiveDiscountsProperties.java`

---

## Section 4 — Admin API

**Base path:** `/api/v1/services/admin/discounts`  
**Security:** `@PreAuthorize("hasRole('ADMIN')")` on all endpoints

| Method | Path | Description | Success code |
|--------|------|-------------|--------------|
| `GET` | `/` | List all campaigns | 200 |
| `GET` | `/{discountType}` | Get one campaign | 200 |
| `POST` | `/` | Create campaign (enum value not yet in DB) | 201 |
| `PATCH` | `/{discountType}` | Partial update (null fields = keep existing) | 200 |
| `DELETE` | `/{discountType}` | Delete campaign row | 204 |

**DTOs:**
- `DiscountCampaignResponseDto` — all fields, including `calculationType`, `enabled`, dates, amounts
- `DiscountCampaignCreateRequestDto` — `@NotNull` on `discountType` and `calculationType`; other fields optional
- `DiscountCampaignUpdateRequestDto` — all fields optional (`Boolean`, `BigDecimal`, `LocalDateTime`)

**Error cases:**
- `POST` on existing `discountType` → `409 CONFLICT` (new `DISCOUNT_CAMPAIGN_ALREADY_EXISTS` ErrorCode)
- `GET`/`PATCH`/`DELETE` on non-existent `discountType` → `404 NOT_FOUND` (new `DISCOUNT_CAMPAIGN_NOT_FOUND` ErrorCode)

**`DiscountAdminService`** interface + `DiscountAdminServiceImp`:
- CRUD methods with cache eviction
- `DiscountCampaignMapper` (MapStruct, in `mappers/entity/`) handles entity ↔ DTO

---

## Section 5 — Initialization & Cleanup

### `DiscountCampaignInitializer` (`ApplicationRunner`)
Seeds missing rows from `DiscountType.values()` at startup. Skips existing rows.

Default seeds:

| DiscountType | calculationType | enabled | percentage | priority |
|---|---|---|---|---|
| `NO_DISCOUNT` | `NONE` | `true` | — | 0 |
| `BLACK_FRIDAY` | `PERCENTAGE` | `false` | 20 | 100 |
| `WINTER_SALES` | `PERCENTAGE` | `false` | 15 | 80 |
| `SPRING_SALES` | `PERCENTAGE` | `false` | 10 | 60 |
| `BUY_ONE_GET_ONE_FREE` | `BUY_ONE_GET_ONE_FREE` | `false` | — | 70 |

---

## Section 6 — Testing

All unit tests follow project conventions: `@ExtendWith(MockitoExtension.class)` for services, `@WebMvcTest` + `@Import(TestSecurityConfig.class)` + `@MockitoBean` for controllers.

| Test class | Coverage |
|---|---|
| `PercentageDiscountStrategyTest` | Happy path, `maxDiscountAmount` cap, null percentage throws |
| `FixedAmountDiscountStrategyTest` | Happy path, `minOrderAmount` guard blocks discount, cap at `baseAmount` |
| `BuyOneGetOneFreeDiscountStrategyTest` | `floor(qty/2)` per line, multi-line summing, odd quantities (qty=1 → 0 free) |
| `NoDiscountStrategyTest` | Always returns ZERO regardless of input |
| `DiscountCampaignServiceTest` | Not found, disabled, start date in future, end date in past, happy path |
| `OrderPriceCalculationServiceImpTest` | NONE short-circuit, PERCENTAGE flow, BOGO flow, strategy not found |
| `DiscountAdminControllerTest` | All 5 endpoints, 403 for non-admin, 409 on duplicate create, 404 on missing |
| `DiscountCampaignInitializerTest` | Seeds missing types, skips existing rows |
