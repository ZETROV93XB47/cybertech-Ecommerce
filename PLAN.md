# Bug-Fix Orchestration Plan

## Status Summary

After a full code audit (2026-04-23), the previous subagent waves have fixed the vast majority of
documented bugs. See `progress.md` for the full historical record.

## Confirmed Already Fixed (no action needed)

| Bug(s) | Area | Verified Fix |
|--------|------|--------------|
| BUG-130/131/132/133/134 | Money, Address, CurrencyCode | compareTo equality, immutability, currencies, fromString() |
| BUG-017/018/019 | ProductMapper, CartMapper, UserMapper | photoUrl wired, NPE guard, null address guard |
| BUG-028 | CartCreateRequestDto | @Valid on nested list |
| BUG-035 | UserEventController | HttpStatus.CREATED (not FolderEvent.CREATED) |
| BUG-026/027 | CartManagementController | @PathVariable + @RequestBody on update/delete |
| BUG-001–016 | ErrorManagementController | All exceptions now have @ExceptionHandler |
| BUG-025 | ErrorManagementController | CartNotFoundException → CART_NOT_FOUND (404) |
| BUG-029 | ErrorManagementController | MethodArgumentTypeMismatchException → 400 |
| BUG-031 | ErrorManagementController | AccessDeniedException → 403 |
| BUG-2503 | ErrorManagementController | HttpMessageNotReadableException → 400 |
| BUG-140 | ErrorManagementController | getMessage() removed from 500 body |
| BUG-036 (addBankCard) | BankCardManagementServiceImp | applyPciStorageRules() + AesCardEncryptionService |
| BUG-037/038 | BankCardManagementServiceImp | Expiry validation, setDefault/getDefaultCard |
| BUG-050 | OrderManagementServiceImp | stockService.releaseStock() on FAILED payment |
| BUG-060 | StockServiceImp | TreeMap(UUID::toString) eliminates deadlock |
| BUG-110 | CleanUpExpiredStockReservationsTasklet | findByReservationStatusAndCreatedAtBefore() |
| BUG-111 | CancelAllPendingOrdersByTimeTasklet | per-item try/catch |
| BUG-124 | OrderPaymentConfirmationEventListener | Optional.ofNullable guard |
| BUG-160/161 | CartServiceImp + CartManagementController | Ownership check + UnauthorizedCartAccessException |
| BUG-201 | UserManagementController | @PreAuthorize("hasRole('ADMIN')") on registerAuto |
| BUG-2505 | ReviewCrudController | @PreAuthorize("hasRole('USER')") on createReview |
| BUG-170/171/520/521/522 | PaymentWebhookServiceImp | Idempotency, events, terminal-state guard, livemode |
| BUG-075/076/077 | StripePaymentAttemptProcessor | Configurable payment method, correct refund encoding |
| BUG-2501/2502 | StripeWebhookController | 200-ACK for non-retriable, 400 for signature/JSON |

---

## Remaining Open Bugs — 6 Total

### Wave 1 — LOW/MEDIUM (dispatched in parallel)

| # | Bug | File | Description |
|---|-----|------|-------------|
| W1-A | BUG-138 | `api/error/ErrorManagementController.java` | Surface field-level validation errors instead of canned string |
| W1-B | BUG-2507 + BUG-2508 | `services/implementation/MailServiceImp.java` | Still calls send() after MessagingException; PII logged at INFO |
| W1-C | BUG-082 + BUG-083 | `services/implementation/S3ServiceImp.java` | No MIME-type allow-list; no file size cap |

### Wave 2 — HIGH (dispatched in parallel after Wave 1 tests pass)

| # | Bug | File | Description |
|---|-----|------|-------------|
| W2-A | BUG-2506 | `services/implementation/ReviewManagementServiceImp.java` | No order-ownership check — user can review using another user's orderUuid |
| W2-B | BUG-036 remaining | `services/implementation/BankCardManagementServiceImp.java` | Admin generic `create()` path skips encryption (applyPciStorageRules not called) |

---

## Dispatch Log

| Wave | Agent | Status |
|------|-------|--------|
| W1-A | BUG-138 | pending |
| W1-B | BUG-2507/2508 | pending |
| W1-C | BUG-082/083 | pending |
| W2-A | BUG-2506 | pending |
| W2-B | BUG-036 remaining | pending |

## Wave 3 — DTO hardening (I1..I5)

- [x] I1 — Remove `userUuid` from `OrderPlacingRequestDto`
- [x] I2 — Remove `userUuid` from `ReviewCreateRequestDto`
- [x] I3 — `@NotNull` on `OrderCancellationRequestDto.orderUuid`
- [x] I4 — `@Pattern("\\d{13,19}")` on `BankCardCreationRequestDto.cardNumber`
- [x] I5 — Drop `@NotNull` on `ProductSearchRequestDto.category`

## Wave 3+ — Regression fixes (K1..K4)

- [x] K1 — Restore admin access to `OrderManagementController.getOrderByUuid` (use `SecurityContextHolder` in service to avoid signature change)
- [x] K2 — `registerAuto` returns `Map.of(...)` so `keycloakId` is exposed (matches `register()` pattern)
- [x] K3 — `ShipAllPaidOrdersTasklet` per-order optimistic-lock isolation via `ShipOrderTransactionalDelegate` (REQUIRES_NEW)
- [x] K4 — `RedisConfig` tighten `BasicPolymorphicTypeValidator` to explicit allowlist

Verification: full unit test suite green — 1648 tests, 0 failures, 0 errors, 18 skipped.

---

## Wave 5b — OpenAPI / Swagger documentation audit (this session)

- [x] Audit every `*ApiSpec.java` and add/normalize `@Tag`, `@Operation`, `@ApiResponse` against the actual `ErrorManagementController` exception → status mappings.
- [x] Add class-level `@Tag` to every spec missing one.
- [x] Add `@ApiResponse(401)` to every authenticated spec method that doesn't already declare one.
- [x] Create `config/OpenApiConfig.java` with bearer/JWT security scheme + global metadata.
- [x] Wire `springdoc-openapi-maven-plugin` (skip=true) into `pom.xml` for opt-in static YAML generation.
- [x] Generate `docs/openapi/openapi.yaml` (handcrafted fallback — booting requires MySQL/Redis/Mongo/ES not available locally).
- [x] `mvn compile` clean + `mvn test -Dtest="*Controller*Test"` green (268 tests, 0 failures, 0 errors, 1 skipped).
- [x] Commit with `docs(openapi): ...` message — commit `6ab1552`.

Foreign files to leave alone (other agents): `NotificationListener.java`, `NotificationRepository.java`, `src/test/.../listener/**`.

---

## Wave 7B — Helm front-app chart + ingress + minikube README (this session)

- [x] Audit 11 existing charts (probes / PVC / resources / gaps).
- [x] Create `charts/front-app-chart/` (Chart.yaml, values.yaml, deployment, service, ingress, secret, _helpers).
- [x] Add backend ingress to `charts/cybertech-app-chart/templates/ingress.yaml` (host `api.cybertech.local`).
- [x] Update `helmfile.yaml` to add `front-app` release with `needs:[cybertech-app]`.
- [x] Create `src/main/resources/k8s/helm/README.md` minikube setup guide.
- [x] Create `front/app/Dockerfile` (multi-stage, standalone output).
- [x] `helm lint` and `helm template` clean for both new chart and modified backend chart.

---

## Wave 8A2 — PRE-2 close BUG-160 cart race + harden mysql-chart (this session)

- [x] Add `UNIQUE KEY uk_cart_user (userId)` to `sql/databaseSchemaInitFile.sql` cartTable
- [x] mysql-chart `databaseSchemaInitFile.sql` does NOT contain a cartTable -> no edit there (chart only seeds DBs + a subset of tables; runtime tables created by Hibernate `ddl-auto=update` or by the source SQL via init Job pointing at `sql/databaseSchemaInitFile.sql`).
- [x] CartServiceImp: retry-once on `DataIntegrityViolationException` in `addItemsToCart`
- [x] Re-enable `CartFlowIT.concurrentAddsFromTwoThreadsShouldSumNotRace` (remove `@Disabled`)
- [x] mysql-chart deployment.yaml: add liveness (TCP 3306), readiness (mysqladmin ping), resources requests/limits
- [x] mysql-chart values.yaml: expose probes/resources knobs
- [x] `helm lint mysql-chart` PASS
- [x] `mvn test -DskipITs` GREEN (1681/1681)
- [x] CartFlowIT.concurrentAddsFromTwoThreadsShouldSumNotRace PASSES
- [x] Discovered & fixed root cause: CartCacheHelperImp.acquireLock used
      template.opsForValue().setIfAbsent which JSON-encodes the token, breaking
      the unlock Lua script's CAS comparison. Switched to a raw-byte connection
      callback so set/get/delete all agree on byte-for-byte equality.
- [x] CartCacheHelperImpTest updated (3 tests) to mock the new callback path.
- [ ] commit

---

## Wave User-Bugs — Bug 3 / Bug 4 / Bug 6 (this session)

Order: Bug 6 (1 line) -> Bug 4 (event/listener) -> Bug 3 (REQUIRES_NEW refactor).

### Bug 6 — register() leaks keycloakId
- [x] `UserManagementController.register()` body strips `keycloakId`, includes `email` + `username`
- [x] `UserManagementControllerTest`: adapt `shouldRegisterUserSuccessfullyReturning201WithMapShape`
- [x] `UserManagementControllerTest`: add `registerResponseShouldNotContainKeycloakId`

### Bug 4 — deleteByUUID Keycloak-before-DB -> AFTER_COMMIT event
- [x] Create `events/UserDeletedEvent.java`
- [x] Create `events/listener/UserDeletionListener.java`
- [x] Refactor `UserManagementServiceImp.deleteByUUID()` to publish event after DB delete
- [x] Tests in `UserManagementServiceImpTest`: `deleteShouldDeleteFromDbAndPublishEvent`, `deleteShouldNotPublishEventWhenDbDeleteFails`
- [x] New `UserDeletionListenerTest`: `onUserDeletedShouldCallKeycloakDelete`, `onUserDeletedShouldLogAndSwallowWhenKeycloakFails`

### Bug 3 — update() Keycloak inside @Transactional -> Option B REQUIRES_NEW
- [x] Add `UserPersistenceService.updateUser(UserUpdateRequestDto, UserEntity)` with REQUIRES_NEW
- [x] Refactor `UserManagementServiceImp.update()`: drop `@Transactional`, DB-first then Keycloak
- [x] Tests in `UserManagementServiceImpTest`: ordering + DB-fail-skips-KC + KC-fail-after-DB
- [x] Tests in `UserPersistenceServiceTest`: `updateUserShouldSaveAndReturnDto`

### Verification
- [x] `mvnw compile` clean
- [x] `mvnw test -Dtest=UserManagementServiceImpTest,UserPersistenceServiceTest,UserManagementControllerTest,UserDeletionListenerTest` -> 42/42 GREEN

### Decisions
- Bug 3 exception: no `KeycloakSyncException` exists -> `RuntimeException("Keycloak sync failed after DB update: " + cause.getMessage(), cause)`.
- `updateUser` signature: `UserResponseDto updateUser(UserUpdateRequestDto dto, UserEntity loadedUser)`.
- `UserDeletedEvent` style: Lombok `@Getter` + explicit `super(source)` constructor (mirrors `OrderCreatedEvent`).

---

## Wave Frontend-Gaps — 4 backend endpoints (this session)

### Endpoints
- [x] **#1** `GET /services/management/order/mine` (paginated) — added on existing `OrderManagementController` (class base `/api/v1/services/management/order`); the requested `/services/order/mine` would have required a brand-new controller for a single endpoint, which contradicts the "follow existing patterns" rule. Documented as a deviation.
- [x] **#2** `GET /api/v1/services/admin/management/order/get/all` — new `OrderManagementAdminController` + `OrderManagementAdminControllerApiSpec`. New constant `ORDER_MANAGEMENT_ADMIN_CONTROLLER_BASE_PATH`.
- [x] **#3** `PATCH /api/v1/services/user/me` — new `UserSelfUpdateRequestDto`, new service method `updateMe`, new controller method.
- [x] **#4** `GET /api/v1/services/bank-card/all-mine` — model is 1-card-per-user (UserEntity OneToOne BankCardEntity); endpoint returns the (possibly empty) list of cards for the JWT subject — currently 0 or 1 entry.

### Tests
- [x] OrderRepository — JPQL paginated finder used by service unit (no @DataJpaTest needed; covered through service tests)
- [x] OrderManagementServiceImp — `findMyOrders`, `findAllPaged` unit tests
- [x] OrderManagementController — getMyOrders (200, 401, status filter)
- [x] OrderManagementAdminController — listing (200, 401, 403)
- [x] UserManagementServiceImp — `updateMe` happy path + Keycloak fail
- [x] UserManagementController — updateMe (200, 401, 404 user-not-found)
- [x] BankCardManagementServiceImp — `findAllMine`
- [x] BankCardManagementController — getAllMine (200, 401)

### Verification
- [ ] mvnw test green

---

## Wave Shipping-Integration — Inject ShippingProviderStrategyFactory into OrderPriceCalculationService (this session)

### Files modified
- [x] `dto/request/order/PriceCalculationRequestDto.java` — add `@NotNull shippingProvider` + `@NotNull shippingType` fields
- [x] `dto/response/order/PriceCalculationResultDto.java` — add `shippingCost` field, finalAmount now includes shipping (asFinalMoney() returns Money(finalAmount,…))
- [x] `services/implementation/OrderPriceCalculationServiceImp.java` — inject `ShippingProviderStrategyFactory`, fold shipping into both NO_DISCOUNT and discounted branches; throws `NoStrategyFoundForProcessingTheRequest` when factory returns null
- [x] `services/implementation/OrderManagementServiceImp.java` — placeOrder & updateOrder forward `shippingProvider`/`shippingType` to PriceCalculationRequestDto; explicit `orderRepository.save(order)` after handlePaymentUpdate

### Tests updated
- [x] `OrderPriceCalculationServiceImpTest` — added `@BeforeEach` shipping factory stub (DEFAULT_SHIPPING_COST=15.00), enriched `request(...)` helper with DHL+STANDARD defaults, recomputed `finalAmount` assertions on existing tests, added 3 new tests: `shippingCostAddedToFinalAmount`, `noShippingStrategyThrowsNoStrategyFoundForProcessingTheRequest`, `shippingCostNotDoubleCountedOnNO_DISCOUNT`
- [x] `OrderManagementServiceImpTest` — `@BeforeEach` default stub now includes `shippingCost`; added `placeOrder_forwardsShippingProviderAndTypeToPriceCalc` and shipping-fields ArgumentCaptor assertion in `updatesAddressTotalAndStatus`

### Verification
- [x] `mvnw test -Dtest='*PriceCalculation*Test,*OrderManagementService*Test'` GREEN — Tests run: 81, Failures: 0, Errors: 0, Skipped: 0

---

## Wave User-Bank-Validator Hardening — PII / saga / PCI / exception mapping (this session)

Scope: 4 targeted fixes on the User + Bank-card + Validator domains. Constraint: do not touch the
controllers (parallel agent) and only add the minimum needed to `ErrorCode.java` /
`ErrorManagementController.java` (parallel agent owns these for the NotificationDeliveryException
mapping).

### FIX 1 — PII leaks in service logs
- [x] Created `utils/LogSafetyUtils.java` with `maskEmail`, `extractEmailDomain`, `maskUuid` (null-safe, no-throw).
- [x] `UserManagementServiceImp.create()` — replaced `log.info("user creation request : {}", req)` with email-domain-only log.
- [x] `UserPersistenceService.saveNewUser()` — replaced `log.info("Saved user : {}", savedUser)` with UUID-only log.
- [x] `UserPersistenceService.updateUser()` — same fix on the update branch.

### FIX 2 — `deleteByUUIDs` saga inconsistency
- [x] Refactored `UserManagementServiceImp.deleteByUUIDs` to publish one `UserDeletedEvent` per resolved user AFTER the SQL bulk delete, mirroring the singular `deleteByUUID` AFTER_COMMIT pattern.
- [x] Updated `UserManagementServiceImpTest.deleteByUUIDs_happyPath` + `_emptyList` to pin the new ordering (SQL -> event publish, no direct Keycloak call from the service).

### FIX 3 — `BankCardManagementServiceImp.updateBankCard` PCI bypass
- [x] User-facing `updateBankCard` now runs `validateExpiryNotInThePast` + `applyPciStorageRules` (only when PAN supplied).
- [x] Admin `update(BankCardUpdateRequestDto)` — same guards added.
- [x] `BankCardManagementServiceImpTest` — added 4 new tests (re-encrypt user-update, expiry user-update, re-encrypt admin-update, expiry admin-update); existing happy-path tests now stub `cardEncryptionService.encrypt`.

### FIX 4 — `ActiveUserValidator` exception mapping
- [x] Replaced `IllegalStateException("Utilisateur inactif !")` with `UserNotActiveException("User is inactive")` — reuses the existing exception and its already-registered handler (HTTP 403, `USER_NOT_ACTIVE`).
- [x] No new exception class created — CLAUDE.md "Pas de duplication cross-classes". `ErrorCode.java` and `ErrorManagementController.java` were not modified, leaving them clear of merge conflicts with the parallel agent.
- [x] `ActiveUserValidatorTest.inactiveUserThrowsAndShortCircuits` — updated to assert `UserNotActiveException` + English message.

### Verification
- [x] `mvnw test -Dtest=UserManagementServiceImpTest,UserPersistenceServiceTest,BankCardManagementServiceImpTest,ActiveUserValidatorTest` -> 81 tests, 0 failures, 0 errors.
- [x] Extended sweep adding `UserDeletionListenerTest,ErrorManagementControllerBranchTest,UserManagementAdminControllerTest` -> 151 tests, 0 failures, 0 errors.

---

## Wave Interface-Contract — controllers must depend on interface, not impl (this session)

Convention CLAUDE.md "Interface avant impl systématique" — six controllers were injecting their concrete `*ServiceImp`. Root cause: several methods used by controllers were missing from the interfaces. Fix: lift the missing methods to the interfaces, then flip the injections.

### Methods promoted to interface

**OrderManagementService** (`services/core/OrderManagementService.java`)
- [x] `OrderResponseDto getByUUID(UUID uuid, String keycloakId)` — used by `OrderManagementController#getOrderByUuid`. Ownership-checked variant. `@Override` added on impl.

**ProductManagementService** (`services/core/ProductManagementService.java`)
- [x] `Page<ProductResponseDto> getAll(Pageable)` — used by `ProductManagementAdminController#getAllProducts`.
- [x] `Page<ProductResponseDto> searchProducts(ProductSearchRequestDto)` — used by `ProductSearchController#searchProducts`.
- [x] `Page<ProductResponseDto> getBestSellers(Pageable)` — used by `ProductSearchController#getBestSellers`.
- [x] `@Override` annotations added on the three impl methods.

**UserManagementService** (`services/core/UserManagementService.java`)
- [x] `Page<UserResponseDto> getAll(Pageable)` — used by `UserManagementAdminController#getAllUsers`.
- [x] `Collection<UserResponseDto> createAutomatically(Collection<UserEntity>)` — used by `UserManagementAdminController#createUserAutomatically`. Return type kept as `Collection` (impl returns `ArrayList`); the broader interface contract preserves Collection semantics.
- [x] `@Override` annotations added on the two impl methods.

### Controllers flipped to interface
- [x] `OrderManagementController` — `OrderManagementService`
- [x] `OrderManagementAdminController` — `OrderManagementService`
- [x] `ProductManagementAdminController` — `ProductManagementService`
- [x] `ProductSearchController` — `ProductManagementService`
- [x] `UserManagementController` — `UserManagementService`
- [x] `UserManagementAdminController` — `UserManagementService`

### Tests updated (`@MockitoBean` types switched to interface)
- [x] `OrderManagementControllerTest`
- [x] `OrderManagementAdminControllerTest`
- [x] `ProductManagementAdminControllerTest`
- [x] `ProductSearchControllerTest`
- [x] `UserManagementControllerTest`
- [x] `UserManagementAdminControllerTest`

### Verification
- [x] `./mvnw compile` — BUILD SUCCESS.
- [x] `./mvnw test -Dtest='OrderManagementControllerTest,OrderManagementAdminControllerTest,ProductManagementAdminControllerTest,ProductSearchControllerTest,UserManagementControllerTest,UserManagementAdminControllerTest'` — 107 tests, 0 failures, 0 errors.

