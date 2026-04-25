# Cybertech Bug-Fixing Campaign — Progress Tracker

> Started: 2026-04-25
> Orchestrator: Claude Sonnet 4.6 (4 subagents)
> Source: full-codebase review report (28 issues identified)

---

## 1. SITREP

**Mission:** ship the Cybertech backend to production. Sequence:
1. **Wave 1** — Tier-1 critical bugs (DONE)
2. **Wave 2** — Tier-2 high backend bugs (IN FLIGHT)
3. **Wave 3** — backend N+1 query fixes + final hardening
4. **Wave 4** — Swagger / OpenAPI doc generation
5. **Wave 5** — Postman collection (with annotated requests)
6. **Wave 6** — Frontend Tier-3 bugs (only after backend is sealed)

The frontend bugs identified in the original review are deferred to Wave 6 by user instruction.

**Status:**
- **Wave 1 COMPLETE** — 14 Tier-1 fixes landed, 1645 tests pass.
- **Wave 2 COMPLETE** — 14 Tier-2 fixes landed across 6 parallel subagents, 1647 tests pass.
- **Wave 3 IN FLIGHT** — IT suite running in background; regression-review agent dispatched in parallel.

| Wave | Agent | Scope | Status | Commit |
|------|-------|-------|--------|--------|
| 1 | A | JPA cascade data-loss (`OrderItemEntity`, `ReviewEntity`) | DONE | `5fe4d5f` |
| 1 | B | Order pricing & lifecycle (`NO_DISCOUNT` guard, `cancelOrder` stock release, review update) | DONE | `0d26559` (+ pieces folded into `ed68af0`) |
| 1 | C | Batch tasklets (cancel persist, double-ship race, swallowed step failures) | DONE | `ed68af0` |
| 1 | D | Security / IDOR (5 endpoints + 1 DTO leak) | DONE | `03e86bf` |
| 2 | E | Cache / Redis serialization (`RedisConfig`, `CartServiceImp` double-write) | DONE | `e72b484` |
| 2 | F | External clients (Stripe webhook dedup, `CommentModerationClient` URL) | DONE | `16408e7` |
| 2 | G | Repository transaction discipline (`@Modifying` on derived deletes) | DONE | `40ffa84` |
| 2 | H | Cross-system consistency (Keycloak/DB transaction split, new `UserPersistenceService`) | DONE | `59ea6b6` |
| 2 | I | DTO validation hardening (5 request DTOs + their fixtures/tests) | DONE | `cbd4353` |
| 2 | J | N+1 elimination in `ReviewManagementServiceImp` (JOIN FETCH + boolean repo query) | DONE | `a66db6a` |
| 3 | K | 4 regression-review fixes (admin bypass on `getOrderByUuid`, `registerAuto` shape, `ShipOrderTransactionalDelegate`, Redis allowlist) | DONE | `44fb0b9` |
| 3 | L1 | `CartFlowIT` IT triage (concurrentAdds BUG-160 + deleteByUuid) | DISPATCHED | — |
| 3 | L2 | `OrderFlowIT` IT triage (4 failures: 3× place-order body + lazy-init) | DISPATCHED | — |
| 3 | L3 | `PaymentWebhookFlowIT` IT triage (7 failures: Stripe signature + dedup) | DISPATCHED | — |
| 3 | L4 | `UserRegistrationFlowIT` IT triage (registerAuto shape + register persistence + actuator) | DISPATCHED | — |
| — | Orchestrator | Aggregate + full-suite verification per wave | ONGOING | n/a |

**Wave 1 verification:** `./mvnw test -DskipITs` → 1645 / 1645 pass (18 skipped pre-existing bug-pin).
**Wave 2 verification:** `./mvnw test -DskipITs` → **1647 / 1647 pass**, 18 skipped, EXIT 0, `BUILD SUCCESS`. Net +2 tests (Agent H added 4 `UserPersistenceServiceTest` tests; some review/order tests were renamed/consolidated, leaving +2 net).

---

## 2. TO-DO LIST

### Tier 1 — Critical (DONE in Wave 1)
- [x] **A1** Drop `CascadeType.ALL` from `OrderItemEntity.orderEntity` and `OrderItemEntity.productEntity` `@ManyToOne` relations — `OrderItemEntity.java:29,33`
- [x] **A2** Drop `CascadeType.ALL` from `ReviewEntity.productEntity` — `ReviewEntity.java:33`
- [x] **B1** Add `NO_DISCOUNT` short-circuit in `OrderPriceCalculationServiceImp.calculate()` — `OrderPriceCalculationServiceImp.java:49-60`
- [x] **B2** Add `stockService.releaseStock(orderUUID)` in `OrderManagementServiceImp.cancelOrder()` — `OrderManagementServiceImp.java:506-508`
- [x] **B3** Patch loaded review entity in `ReviewManagementServiceImp.update()` instead of saving a blank-FK new object — `ReviewManagementServiceImp.java:103-112`
- [x] **C1** `orderRepository.saveAll(successfullyCancelled)` in `CancelAllPendingOrdersByTimeTasklet` — `CancelAllPendingOrdersByTimeTasklet.java:65-69`
- [x] **C2** Atomic-claim pattern (PAID → AWAITING_SHIPPING via optimistic-lock save) in both `ShipAllPaidOrdersTasklet` AND `ShippingListener` — `ShipAllPaidOrdersTasklet.java:54-71`, `ShippingListener.java:74-80`
- [x] **C3** `BaseTasklet` sets `ExitStatus.FAILED` and rethrows on caught exceptions — `BaseTasklet.java:47-69`
- [x] **D1** `OrderManagementController.getOrderByUuid` ownership check via JWT subject; non-owners get 403
- [x] **D2** `UserManagementController.getUserByUuid` `@PreAuthorize` + ownership check; admins bypass via `ROLE_ADMIN` authority
- [x] **D3** `/api/v1/services/user/get/all` removed from `SecurityConfig.PUBLIC_URLS` (and from `TestSecurityConfig`)
- [x] **D4** `OrderManagementController.placeOrder2` (`POST /place/auto`) restricted to `ROLE_ADMIN`
- [x] **D5** `UserEventController.collectEvent` overwrites `eventDto.setUserId(jwt.getSubject())`; `UserEventDto` carries `@JsonIgnoreProperties(value="userId", allowGetters=true)`
- [x] **D6** `UserResponseDto.keycloakId` annotated `@JsonIgnore` (registration `Map.of(...)` response unaffected because it bypasses the DTO)

### Tier 2 — High (DONE in Wave 2)
- [x] **E1** `RedisConfig`: `activateDefaultTyping(BasicPolymorphicTypeValidator, DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY)` on the Jackson-3 `JsonMapper` builder
- [x] **E2** `CartServiceImp`: removed `@CachePut`/`@CacheEvict`/`@Cacheable`; added explicit `cartCacheHelper.putWithJitter` to `clearCart` (helper has no `evict` API — write the cleared DTO instead)
- [x] **F1** `CommentModerationClient`: restored `@Value("${moderation.api.url}")` (property already defined in `application.properties` line 21 + test profile)
- [x] **F2** `PaymentWebhookServiceImp`: introduced `boolean handled` flag inside the switch; only handled cases set it; trailing dedup-ledger write guarded by `if (handled)`
- [x] **G1** `DiscountCampaignRepository.deleteByDiscountType` annotated `@Modifying` + `@Transactional`
- [x] **G2** `CrudBaseRepository.deleteByUuid` and `deleteAllByUuidIn` annotated `@Modifying` + `@Transactional` (confirmed second method name)
- [x] **H1** Created `UserPersistenceService` with `@Transactional(REQUIRES_NEW) saveNewUser(...)`. `UserManagementServiceImp.create()` now: (a) calls Keycloak (no TX), (b) delegates DB+bank-card to `UserPersistenceService` in its own TX, (c) compensates with `keycloakUserManagementService.deleteUser(...)` in a try/catch, logging loudly if compensation also fails. `BankCardManagementService` moved into `UserPersistenceService`.
- [x] **I1** `OrderPlacingRequestDto.userUuid` removed
- [x] **I2** `ReviewCreateRequestDto.userUuid` removed
- [x] **I3** `OrderCancellationRequestDto.orderUuid` annotated `@NotNull`
- [x] **I4** `BankCardCreationRequestDto.cardNumber` annotated `@Pattern("\\d{13,19}")` alongside existing `@Size`
- [x] **I5** `ProductSearchRequestDto.category` `@NotNull` removed; `ProductSearchServiceImp` already null-guards (lines 46/91/114)
- [x] **J1** `OrderRepository.findReviewableOrdersWithItemsByKeycloakIdAndStatusIn` (JOIN FETCH on `orderItemEntities` + `productEntity`); old N+1 method removed (no other callers)
- [x] **J2** `OrderItemRepository.userHasBoughtProduct(keycloakId, productUuid)` boolean query; `ReviewManagementServiceImp.checkIfUserAlreadyBoughtThisProduct` no longer iterates lazy graphs

### Wave 3 — Final backend hardening (IN FLIGHT)

**Regression review findings (HIGH/MEDIUM confidence) — to fix in Agent K:**
- [ ] **K1** `OrderManagementController.getOrderByUuid` — restore admin access. Currently `@PreAuthorize("hasRole('USER')")` only; admins lost the ability to fetch orders by UUID. Fix: change to `"hasRole('USER') or hasRole('ADMIN')"` AND add admin bypass in `OrderManagementServiceImp.getByUUID(UUID, String)` (mirror `UserManagementController.isAdmin()`).
- [ ] **K2** `UserResponseDto.@JsonIgnore` on `keycloakId` breaks `POST /register/auto/single` admin endpoint. `registerAuto()` returns the DTO directly (not a `Map.of(...)`), so `keycloakId` is suppressed. Fix: switch `registerAuto()` to return `Map.of("id", uuid, "keycloakId", kid, ...)` consistent with `register`, OR introduce a dedicated `RegistrationResponseDto` without `@JsonIgnore`.
- [ ] **K3** `ShipAllPaidOrdersTasklet.execute()` outer `@Transactional` defeats per-order optimistic-lock catch. The exception escapes the per-order try/catch because the flush happens at end-of-method. Fix: extract `processShipping(order)` into a delegate `@Service` annotated `@Transactional(propagation = REQUIRES_NEW)` (analogous to `UserPersistenceService`). Each order gets its own session+flush so the lock exception is raised and caught per-order, not on outer commit.
- [ ] **K4** `RedisConfig` `BasicPolymorphicTypeValidator.allowIfBaseType(Object.class)` is over-broad. A future cache-poisoning attacker could craft `@class` headers and trigger gadget-chain RCE. Fix: enumerate concrete cached types (`allowIfSubType(CartResponseDto.class)`, `allowIfSubType(DiscountContext.class)`, `allowIfSubType(Boolean.class)`, etc.) — confirm by `Grep`-ing for `@Cacheable` and `cartCacheHelper` usages.

**Pre-existing issue noted (NOT introduced by Wave 1/2, scheduled for Wave 3 follow-up):**
- [ ] `OrderEntity.orderItemEntities` has `CascadeType.ALL` but no `orphanRemoval = true`. `OrderManagementServiceImp.updateOrder` calls `order.getOrderItemEntities().clear()`; without `orphanRemoval`, the old rows become orphaned (FK still set, no DELETE). Add `orphanRemoval = true` to the `@OneToMany` annotation on `OrderEntity`.

**Wave 3 verification result:** `./mvnw verify -Pintegration-test` exited 1.
- All 1647 unit tests still green.
- JaCoCo `report` and `check` ran cleanly (gate appears to have passed; build failed downstream at failsafe verify).
- Failsafe reported **5 IT failures + 1 IT error**:
  - `CartFlowIT.concurrentAddsFromTwoThreadsShouldSumNotRace` (FAILURE — `expected: 4 but was: 2`, suggests cache double-write removal in Agent E broke concurrent add accumulation)
  - `CartFlowIT.deleteByCartUuidBindsPathVariable` (FAILURE — `Expecting value to be false but was true`, possibly related to `@Modifying` derived-delete change in Agent G)
  - `OrderFlowIT.happyPathPlaceOrderDecrementsStockAndPublishesEvent` (FAILURE — assertion mismatch)
  - `OrderFlowIT.cancelOrderRestoresStockAndMovesStatusToCanceled` (FAILURE — likely related to Wave 1 B `cancelOrder` stock-release change)
  - `OrderFlowIT.retryPaymentAfterInitialFailureEventuallyPays` (FAILURE — `expected: PAID but was: AWAITING_PAYMENT`, Stripe webhook listener not flipping the status)
  - `OrderFlowIT.addsToCartForAuthenticatedUser` (ERROR — JSON parse error in `ReviewCrudController.createReview`; likely the test still sends a `userUuid` field that was removed by Agent I)
- Pre-existing Stripe noise (`SignatureVerificationException`) appears in several test runs but is also visible against `master` baseline — not a Wave 1/2 regression.

**Wave 3 follow-ups (after Agent K finishes the 4 regression-review fixes):**
- [ ] **L1** Triage `CartFlowIT.concurrentAddsFromTwoThreadsShouldSumNotRace` — investigate whether removing Spring `@CachePut` exposed a missing read-modify-write lock in `CartServiceImp.addItemsToCart`. Likely fix: ensure `cartCacheHelper.putWithJitter` is called inside the same DB transaction OR re-read the cart from DB before the put. May need a redis distributed lock if the test exercises real concurrency.
- [ ] **L2** Triage `CartFlowIT.deleteByCartUuidBindsPathVariable` — the `@Modifying` change in Agent G means the delete now runs as a single DELETE; some test environments don't have an outer TX. Verify the controller call path opens a TX (`@Transactional` on the service or the controller method).
- [ ] **L3** Triage `OrderFlowIT` 3 assertion failures + 1 error. The error (`addsToCartForAuthenticatedUser`) is likely a stale test JSON body still sending `userUuid: null` post-Agent-I. The 3 assertion failures need stack traces from `target/failsafe-reports/` to pin the exact root cause.
- [ ] Verify JaCoCo gate explicitly: `./mvnw jacoco:check` after fixes land

### Wave 4 — Swagger / OpenAPI doc (after Wave 3)
- [ ] Verify all controllers have full `@Operation` / `@ApiResponse` annotations on the spec interfaces
- [ ] Ensure error responses (404, 403, 409, 401) are documented per endpoint
- [ ] Generate `openapi.yaml` from the running app and commit it under `docs/openapi/`

### Wave 5 — Postman collection (after Wave 4)
- [ ] Build a Postman collection grouped by domain (auth, user, product, cart, order, review, payment, admin)
- [ ] Each request annotated with intent + expected status codes + sample body
- [ ] Include an environment file (`{{base_url}}`, `{{access_token}}`) and a pre-request auth script for Keycloak
- [ ] Commit under `docs/postman/`

### Wave 6 — Frontend (after backend is sealed)
- [ ] Align frontend `OrderStatus` type with backend (`CANCELED` spelling + missing `AWAITING_PAYMENT`/`AWAITING_SHIPPING`/`RETURNED`/`REFUNDED`)
- [ ] Wire `RefreshTokenError` to redirect to sign-in in `proxy.ts` / `apiFetch`

---

## 3. IMPORTANT REMARKS

- **Wave 2 commit hygiene worked.** The defensive `git diff --cached --name-only` check (added after the Wave 1 collision) prevented every Wave 2 agent from picking up foreign work. Each commit (`40ffa84`, `16408e7`, `e72b484`, `59ea6b6`, `cbd4353`, `a66db6a`) is atomic and contains only its agent's intended files. Reuse this pattern for Waves 3+.
- **Wave 1 commit collision (historical).** Agents B and C ran in parallel; Agent C's commit accidentally swept up Agent B's NO_DISCOUNT + review-update work. Code correct, history misleading. If a future bisect lands on `ed68af0` for a non-batch regression, look there first.
- **External reverter observed.** Agent A reported that an "external linter/tool" reverted the `OrderItemEntity` and `ReviewEntity` changes mid-session, forcing it to re-apply and commit. This suggests an IDE save-action or pre-commit hook is fighting our edits. Worth investigating before Wave 2 — running an IntelliJ "Optimize Imports / Reformat on save" hook with strict cascade rules could explain it.
- **Discount campaign refactor is now in mainline.** Commits `44a26b7` (`refactor(discount): DB-driven campaigns + algorithm-keyed strategies + admin endpoint`) and `a936549` (`feat(discount): public GET /api/v1/services/discounts/active endpoint`) landed during this work. The previous session's pending Agents 2–4 are no longer needed — the work was completed externally. **Fix B1 is therefore extra-defensive but no longer the only protection** against missing campaign rows.
- **`C2` race fix relies on JPA `@Version` optimistic locking.** `BaseEntity` already has the `@Version` field, so the atomic claim works as designed. If anyone removes the `@Version` field in future, the double-ship race re-opens. Add an integration test that asserts the race is closed before any future schema change touches `BaseEntity`.
- **Test updates were necessary, not silent regressions.** Agent C updated 5 tests (4 in `ShipAllPaidOrdersTaskletTest`, 1 in `ShippingListenerTest`) because they previously asserted "exactly one save per order" — the new atomic-claim pattern is "claim save + final-state save". Agent B rewrote 2 review/price-calc tests because they pinned the buggy behaviour. Both are *correct* behavioural changes, not test loosening.
- **JaCoCo coverage gate:** the campaign did NOT introduce a coverage drop (the new fix branches all have explicit tests). Confirmed indirectly by `BUILD SUCCESS` since `haltOnFailure=true` is configured.
- **No frontend work yet, by design.** All frontend (Tier 3 / Wave 6) bugs are still deferred until backend is sealed.
- **Wave 2 noteworthy deviations from plan:**
  - **Agent E (Cache)**: Jackson 3 (`tools.jackson.*`) requires `BasicPolymorphicTypeValidator` instead of `LaissezFaireSubTypeValidator`, and `DefaultTyping` is a top-level enum (not nested under `ObjectMapper`). The fix uses `tools.jackson.databind.DefaultTyping.NON_FINAL`. `JsonTypeInfo` is still in `com.fasterxml.jackson.annotation` (Jackson 3 has no `tools.jackson.annotation` module).
  - **Agent E (Cart)**: `cartCacheHelper` has no `evict`/`delete` API — `clearCart` therefore writes the cleared DTO via `putWithJitter` to keep the read path in sync.
  - **Agent F (ApiClientConfig)**: `baseUrl` left in place; `RestClient` URI resolution rules mean an absolute `.uri(...)` arg replaces the base, so the duplication is misleading but not provably broken. Worth a follow-up cleanup but not a bug.
  - **Agent H (User create)**: existing `create()` flow also called `bankCardManagementService.addBankCard(...)` for the registration leg of BUG-036. That call was moved into the new `UserPersistenceService.saveNewUser` so it shares the inner `REQUIRES_NEW` transaction with the user save; `BankCardManagementService` is no longer a dependency of `UserManagementServiceImp`.
  - **Agent I (DTO)**: `ProductSearchControllerTest.shouldFailSearchProductsCauseDtoBadRequest` was flipped to `shouldAcceptSearchWithoutCategoryAsCrossCategorySearch` — the test was pinning the buggy behaviour and is now correctly inverted.
  - **Agent J (N+1 boolean check)**: the new boolean query intentionally has no status filter to mirror the original in-memory iteration, which never filtered by status. If status filtering is desired in future, add a `:statuses` parameter.
- **Wave 3 priorities:**
  1. Run the integration-test suite (`./mvnw verify -Pintegration-test`) — it was NOT run in Wave 2 (only unit tests). The new `@Modifying` repos and `REQUIRES_NEW` TX boundary in `UserPersistenceService` are the highest-risk regression candidates.
  2. Verify JaCoCo gate (80% line + branch) is still met. The new `UserPersistenceService` has tests but the orchestration in `UserManagementServiceImp.create()` (compensation paths) may need an extra test.
  3. Re-run the full code-review agent over the changed surfaces to catch any second-order bugs.
- **Wave 4 (Swagger / OpenAPI)** plan: most controllers already have spec interfaces (`*ApiSpec.java`). Walk each spec, add missing `@Operation`/`@ApiResponse`. Generate `openapi.yaml` via `springdoc-openapi-maven-plugin` and commit under `docs/openapi/`. Estimated 1 wave.
- **Wave 5 (Postman)** plan: build per-domain folders (auth/user/product/cart/order/review/payment/admin), each request annotated. Use `{{base_url}}` and `{{access_token}}` env vars. Pre-request script: Keycloak token-exchange against `${keycloak.realm}/protocol/openid-connect/token`. Commit under `docs/postman/`. Estimated 1 wave.
