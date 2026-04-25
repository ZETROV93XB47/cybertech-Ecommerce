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

