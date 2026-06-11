# Order USER/ADMIN Controller Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Move the 2 ADMIN-only endpoints (`POST /place/auto`, `DELETE /delete/{uuid}`) out of the MIXED `OrderManagementController` into the EXISTING `OrderManagementAdminController`, leaving a pure-USER controller.

**Architecture:** Controllers-only, behavior-preserving. Replicates the validated BankCard pilot (`docs/superpowers/specs/2026-06-11-bankcard-admin-controller-split-design.md`), except the admin controller already exists — so we MOVE 2 endpoints into it rather than create one. Both endpoints keep calling the unchanged shared `OrderManagementService`. Paths are relocated under the admin base `/api/v1/services/admin/management/order`; verbs unchanged.

**Tech Stack:** Spring Boot 4 MVC, `@PreAuthorize` method security, springdoc `*ApiSpec` interfaces, JUnit 5 + MockMvc `@WebMvcTest` + `TestSecurityConfig` + `JwtTestUtils`.

**Build command:** `JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw <args>` (Bash, never offline). Surefire "Tests run:" prints without `-q` on success.

---

## Current state (exact)

`OrderManagementController` (`/api/v1/services/management/order`, no class-level `@PreAuthorize`) has these two ADMIN endpoints (the move targets):

```java
// placeOrder2 — POST /place/auto, @PreAuthorize("hasRole('ADMIN')")
//   calls orderManagementService.placeOrder(orderGenerator(), jwt)
//   uses static import: com.novatech.cybertech.utils.DataGenerator.orderGenerator
// deleteOrderByUuid — DELETE /delete/{uuid}, @PreAuthorize("hasRole('ADMIN')")
//   calls orderManagementService.deleteByUUID(uuid, jwt)
```

`OrderManagementAdminController` already exists: `/api/v1/services/admin/management/order`, class-level `@PreAuthorize("hasRole('ADMIN')")`, injects `OrderManagementService orderManagementService`, currently has only `getAllOrders`. Its ApiSpec is `OrderManagementAdminControllerApiSpec` (currently declares only `getAllOrders`). Its test `OrderManagementAdminControllerTest` has 5 tests and uses `@Import({TestSecurityConfig.class, ErrorManagementController.class})`, mock field `orderManagementService`, helpers `jwtAdmin`/`jwtUser`.

The 2 ApiSpec declarations to move live in `OrderManagementControllerApiSpec` lines 162–193 (`placeOrder2`, `deleteOrderByUuid`).

The 6 tests to move live in `OrderManagementControllerTest` (mock field is named `orderService` there):
- `// ---------- POST /place/auto ----------`: `shouldPlaceOrderViaAutoEndpointAsAdmin`, `shouldRejectPlaceAutoEndpointAsRoleUserReturning403`
- `// ---------- DELETE /delete/{uuid} ----------`: `shouldDeleteOrderByUuidAsAdmin`, `shouldFailDeletingOrderByUuidAsUserCauseForbidden`, `shouldFailDeletingOrderByUuidWhenAnonymousCauseUnauthorized`, `shouldFailDeletingOrderByUuidWhenOrderNotFound`

---

## File Structure

**Modify (add to):**
- `.../api/controllers/implementation/OrderManagementAdminController.java` — add 2 endpoints.
- `.../api/controllers/spec/OrderManagementAdminControllerApiSpec.java` — add 2 declarations.
- `.../test/.../order/OrderManagementAdminControllerTest.java` — add 6 moved tests at new paths.

**Modify (remove from):**
- `.../api/controllers/implementation/OrderManagementController.java` — remove 2 endpoints + now-unused `orderGenerator` import.
- `.../api/controllers/spec/OrderManagementControllerApiSpec.java` — remove 2 declarations.
- `.../test/.../order/OrderManagementControllerTest.java` — remove 6 tests + now-unused constants/imports.

**Unchanged (do NOT touch):** `OrderManagementService`, `OrderManagementServiceImp`, all service tests, DTOs, entities.

---

## Task 1: Add the 6 admin tests (RED)

**Files:**
- Modify: `src/test/java/com/novatech/cybertech/api/controllers/implementation/order/OrderManagementAdminControllerTest.java`

- [ ] **Step 1: Add two path constants** next to the existing `GET_ALL_ENDPOINT`:

```java
    private static final String PLACE_AUTO_ENDPOINT = "/api/v1/services/admin/management/order/place/auto";
    private static final String DELETE_BY_UUID_ENDPOINT = "/api/v1/services/admin/management/order/delete/{uuid}";
```

- [ ] **Step 2: Move the 6 test methods** from `OrderManagementControllerTest` into this class. Copy the bodies verbatim, then apply these mechanical adaptations:
  - Rename the mock reference `orderService` → `orderManagementService` (this class's `@MockitoBean` field name).
  - Repoint URLs: `PLACE_AUTO` → `PLACE_AUTO_ENDPOINT`; `DELETE_BY_UUID` → `DELETE_BY_UUID_ENDPOINT` (the new admin paths).
  - Keep the test method names identical.

The 6 methods: `shouldPlaceOrderViaAutoEndpointAsAdmin`, `shouldRejectPlaceAutoEndpointAsRoleUserReturning403`, `shouldDeleteOrderByUuidAsAdmin`, `shouldFailDeletingOrderByUuidAsUserCauseForbidden`, `shouldFailDeletingOrderByUuidWhenAnonymousCauseUnauthorized`, `shouldFailDeletingOrderByUuidWhenOrderNotFound`.

- [ ] **Step 3: Add the imports these moved tests need** (only those not already present in this file). Likely additions:

```java
import com.novatech.cybertech.exceptions.OrderNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import static com.novatech.cybertech.utils.TestUtils.asJsonString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
```

(Cross-check the source test `OrderManagementControllerTest` import block for any other symbol the 6 bodies reference — e.g. `ArgumentCaptor` (already imported here), `OrderDtoFixtures` (already imported). Add only what's missing; the compiler is the source of truth.)

- [ ] **Step 4: Run the admin test to confirm RED**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw test -Dtest='OrderManagementAdminControllerTest' -DfailIfNoTests=false`
Expected: the two happy-path / not-found cases FAIL because `OrderManagementAdminController` does not yet map `/place/auto` or `/delete/{uuid}` (404 instead of 201/204/404-domain). (Security-only cases — anonymous 401, user 403 — may already pass because the class-level guard fires before routing; that's fine. The meaningful RED is the happy paths returning 404.)

(Do NOT commit — Task 2 makes it green.)

---

## Task 2: Add the 2 endpoints to the admin controller + ApiSpec (GREEN)

**Files:**
- Modify: `src/main/java/com/novatech/cybertech/api/controllers/spec/OrderManagementAdminControllerApiSpec.java`
- Modify: `src/main/java/com/novatech/cybertech/api/controllers/implementation/OrderManagementAdminController.java`

- [ ] **Step 1: Add the 2 declarations to `OrderManagementAdminControllerApiSpec`** (move them verbatim from `OrderManagementControllerApiSpec` lines 162–193). Append inside the interface, after `getAllOrders`:

```java
    @Operation(summary = "Place an auto-generated order (admin debug helper)",
            description = "Admin-only debug / load-test utility that forges an order from synthetic cart data via the data generator. Restricted to ADMIN to avoid letting any authenticated user spam orders against another's cart state (BUG-IDOR-D4).",
            security = @SecurityRequirement(name = "keycloak"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Auto-generated order placed successfully", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = OrderResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Underlying resource (e.g., generated user/product) not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<OrderResponseDto> placeOrder2(@Parameter(hidden = true) final Jwt jwt);

    @Operation(summary = "Delete an Order by UUID (Admin)",
            description = "Deletes an order based on its unique UUID.",
            security = @SecurityRequirement(name = "keycloak"),
            parameters = {
                    @Parameter(name = "uuid", description = "The UUID of the order to delete", required = true, schema = @Schema(implementation = UUID.class))
            },
            responses = {
                    @ApiResponse(responseCode = "204", description = "Order deleted successfully (No Content)"),
                    @ApiResponse(responseCode = "400", description = "Bad request (e.g., invalid UUID format)", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - ADMIN role required", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Order not found", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "500", description = "Internal server error during order deletion", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Void> deleteOrderByUuid(final UUID orderUuid, final Jwt jwt);
```

Add the imports this interface now needs (if missing): `org.springframework.security.oauth2.jwt.Jwt`, `java.util.UUID`, `io.swagger.v3.oas.annotations.parameters` is not needed; `io.swagger.v3.oas.annotations.Parameter` is already imported. (`@Parameter`, `@Operation`, `@ApiResponse`, `@Content`, `@Schema`, `@SecurityRequirement`, `ErrorResponseDto`, `OrderResponseDto` already imported.)

- [ ] **Step 2: Add the 2 endpoints to `OrderManagementAdminController`** (move verbatim from `OrderManagementController`, keeping the `@Override` + `@PostMapping`/`@DeleteMapping` but DROPPING the now-redundant method-level `@PreAuthorize("hasRole('ADMIN')")` since the class is already `@PreAuthorize("hasRole('ADMIN')")`). Append after `getAllOrders`:

```java
    @Override
    @PostMapping(value = "/place/auto", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponseDto> placeOrder2(@AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderManagementService.placeOrder(orderGenerator(), jwt));
    }

    @Override
    @DeleteMapping(value = "/delete/{uuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteOrderByUuid(@PathVariable("uuid") final UUID uuid, @AuthenticationPrincipal final Jwt jwt) {
        orderManagementService.deleteByUUID(uuid, jwt);
        return ResponseEntity.noContent().build();
    }
```

Add the imports this controller now needs:

```java
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import java.util.UUID;
import static com.novatech.cybertech.constants.CyberTechAppConstants.APPLICATION_JSON_VALUE; // NO — already static-imported from MediaType below
import static com.novatech.cybertech.utils.DataGenerator.orderGenerator;
```

(Correction: `APPLICATION_JSON_VALUE` is already statically imported from `org.springframework.http.MediaType` in this file — do NOT add it again. The genuinely-new imports are: `HttpStatus`, `AuthenticationPrincipal`, `Jwt`, `DeleteMapping`, `PathVariable`, `PostMapping`, `java.util.UUID`, and the static `DataGenerator.orderGenerator`. `GetMapping`, `RequestMapping`, `RestController`, `ResponseEntity`, `OrderResponseDto` already imported.)

- [ ] **Step 3: Run the admin test → GREEN**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw test -Dtest='OrderManagementAdminControllerTest' -DfailIfNoTests=false`
Expected: BUILD SUCCESS, `Tests run: 11, Failures: 0, Errors: 0` (5 existing + 6 moved).

> Note: the OLD endpoints still exist on `OrderManagementController` at `/management/order/...` (removed in Task 3). No path clash (admin paths are under `/admin/management/order`). Both old and new tests green.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/novatech/cybertech/api/controllers/implementation/OrderManagementAdminController.java src/main/java/com/novatech/cybertech/api/controllers/spec/OrderManagementAdminControllerApiSpec.java src/test/java/com/novatech/cybertech/api/controllers/implementation/order/OrderManagementAdminControllerTest.java
git commit -m "feat(order): move admin place/auto + delete endpoints into OrderManagementAdminController"
```

---

## Task 3: Remove the 2 endpoints from the user controller + ApiSpec

**Files:**
- Modify: `src/main/java/com/novatech/cybertech/api/controllers/implementation/OrderManagementController.java`
- Modify: `src/main/java/com/novatech/cybertech/api/controllers/spec/OrderManagementControllerApiSpec.java`

- [ ] **Step 1: Remove from `OrderManagementController`** the `placeOrder2` method (with its `// BUG-IDOR-D4 ...` comment block + `@PreAuthorize` + `@PostMapping`) and the `deleteOrderByUuid` method (with its `@PreAuthorize` + `@DeleteMapping`). Then remove the now-unused static import:

```java
import static com.novatech.cybertech.utils.DataGenerator.orderGenerator;
```

Keep everything else (`HttpStatus`, `UUID`, etc. are still used by the remaining 7 user endpoints: `placeOrder`, `cancelOrder`, `updateOrder`, `retryPayment`, `getOrderByUuid`, `getOrderStatusByUuid`, `getMyOrders`).

- [ ] **Step 2: Remove from `OrderManagementControllerApiSpec`** the two `@Operation`-annotated declarations `placeOrder2` (lines ~162–171) and `deleteOrderByUuid` (lines ~174–193). After removal, check whether any import became unused (the remaining methods still use `Jwt`, `UUID`, `Page`, `Pageable`, `OrderResponseDto`, `OrderStatusDto`, etc. — most likely nothing becomes unused, but verify and remove any that did).

- [ ] **Step 3: Verify compile**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw -q compile`
Expected: BUILD SUCCESS. (A leftover `@Override` on the controller with no matching spec method, or vice-versa, will fail — both files must drop the same two methods.)

(Do NOT commit — `OrderManagementControllerTest` still references the removed endpoints; Task 4 fixes it.)

---

## Task 4: Remove the 6 tests from the user controller test

**Files:**
- Modify: `src/test/java/com/novatech/cybertech/api/controllers/implementation/order/OrderManagementControllerTest.java`

- [ ] **Step 1: Delete the 6 moved test methods** (the `// ---------- POST /place/auto ----------` section: 2 methods; the `// ---------- DELETE /delete/{uuid} ----------` section: 4 methods) along with their section comments.

- [ ] **Step 2: Remove the now-unused path constants:**

```java
    private static final String PLACE_AUTO = BASE + "/place/auto";
    private static final String DELETE_BY_UUID = BASE + "/delete/{uuid}";
```

- [ ] **Step 3: Remove any import left unused by the deletions.** After removing the delete tests, check `delete` (MockMvcRequestBuilders) and `doNothing` — these were likely used only by the deleted delete tests; remove them if now unused. KEEP `jwtAdmin` (still used by `shouldGetOrderByUuidAsAdminBypassingOwnershipCheck`), `doThrow`, `post`, `get`, `csrf`, etc. if still referenced. Let the compiler/build confirm: a clean build must have zero unused imports here.

- [ ] **Step 4: Run both order controller test slices**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw test -Dtest='OrderManagementControllerTest,OrderManagementAdminControllerTest' -DfailIfNoTests=false`
Expected: BUILD SUCCESS, no failures. The user slice keeps its USER-endpoint tests (incl. the USER-or-ADMIN `getOrderByUuid` ones); the admin slice has 11.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/novatech/cybertech/api/controllers/implementation/OrderManagementController.java src/main/java/com/novatech/cybertech/api/controllers/spec/OrderManagementControllerApiSpec.java src/test/java/com/novatech/cybertech/api/controllers/implementation/order/OrderManagementControllerTest.java
git commit -m "refactor(order): remove admin place/auto + delete endpoints from user controller + ApiSpec"
```

---

## Task 5: Full-suite verification + doc sync

**Files:**
- Modify (if needed): `docs/FRONTEND_SITREP.md` — if it lists the order admin endpoints at the old `/management/order/place/auto` or `/management/order/delete/{uuid}` paths, update them to `/admin/management/order/...`.

- [ ] **Step 1: Sync the frontend SITREP doc**

Grep `docs/FRONTEND_SITREP.md` for `management/order/place/auto` and `management/order/delete`. If present at the non-admin path, update the admin rows to `/api/v1/services/admin/management/order/place/auto` and `/api/v1/services/admin/management/order/delete/{uuid}`. If the doc doesn't list them, skip. Commit any change with `docs(frontend-sitrep): update order admin endpoints to /admin/management/order paths`.

- [ ] **Step 2: Run the full unit suite**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw test -DfailIfNoTests=false`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`. Report the final test count.

---

## Self-Review notes

- **Spec coverage:** move place/auto (Tasks 1/2) ✓, move delete/{uuid} (Tasks 1/2) ✓, ApiSpec declarations moved (Tasks 2/3) ✓, user controller + ApiSpec slimmed (Task 3) ✓, tests moved (Tasks 1/4) ✓, doc sync (Task 5) ✓, full-suite gate (Task 5) ✓, service untouched ✓.
- **Behavior preservation:** paths only gain the `/admin` prefix (relocated under the existing admin base); verbs unchanged (POST/DELETE); the method-level `@PreAuthorize("hasRole('ADMIN')")` is dropped from the moved methods only because the destination class already enforces ADMIN at class level — net authorization is identical.
- **Type consistency:** moved method signatures (`placeOrder2(Jwt)`, `deleteOrderByUuid(UUID, Jwt)`) match between the ApiSpec declarations and the controller `@Override`s; service calls (`placeOrder(orderGenerator(), jwt)`, `deleteByUUID(uuid, jwt)`) are unchanged.
- **Compile-safety ordering:** admin side gains the endpoints+tests first (old still present, no path clash, all green); old endpoints + their tests removed together in Tasks 3–4.
