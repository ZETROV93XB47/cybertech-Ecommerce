# User USER/ADMIN Controller Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Move the single ADMIN-only endpoint `registerAuto` (`POST /register/auto/single`) out of the MIXED `UserManagementController` into the EXISTING `UserManagementAdminController`, leaving a pure-(public+USER) controller. This is the last MIXED controller; it completes the controller-split effort (BankCard + Order already done).

**Architecture:** Controllers-only, behavior-preserving. Replicates the Order pass. The moved endpoint keeps calling the unchanged shared `UserManagementService`. Path relocated under the admin base `/api/v1/services/admin/user`; verb unchanged (POST). One extra wrinkle vs Order: the 4 JSON response-key literals (`RESPONSE_KEY_*`) are shared by `register` (stays) and `registerAuto` (moves), so they are lifted to `CyberTechAppConstants` to avoid cross-class duplication (per CLAUDE.md).

**Tech Stack:** Spring Boot 4 MVC, `@PreAuthorize` method security, springdoc `*ApiSpec`, JUnit 5 + MockMvc `@WebMvcTest` + `TestSecurityConfig` + `JwtTestUtils`.

**Build command:** `JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw <args>` (Bash, never offline). "Tests run:" prints without `-q` on success.

---

## Current state (exact)

`UserManagementController` (`/api/v1/services/user`) has `registerAuto()` — `@PreAuthorize("hasRole('ADMIN')")`, `POST /register/auto/single`, **no `@Override`** (it is NOT declared in `UserControllerApiSpec`). It returns `ResponseEntity<Map<String, Object>>` of `{id, keycloakId, email, username}` and calls `userManagementServiceImp.create(generateUserCreateRequestDto())` (static import `com.novatech.cybertech.utils.DataGenerator.generateUserCreateRequestDto`).

It defines 4 local constants (lines 71–74):
```java
private static final String RESPONSE_KEY_ID = "id";
private static final String RESPONSE_KEY_KEYCLOAK_ID = "keycloakId";
private static final String RESPONSE_KEY_EMAIL = "email";
private static final String RESPONSE_KEY_USERNAME = "username";
```
`register()` (public signup, STAYS) uses `RESPONSE_KEY_ID/EMAIL/USERNAME`. `registerAuto()` uses all 4 (the only user of `RESPONSE_KEY_KEYCLOAK_ID`).

`UserManagementAdminController` already exists: `/api/v1/services/admin/user`, class-level `@PreAuthorize("hasRole('ADMIN')")`, injects `UserManagementService userManagementServiceImp`, has `getAllUsers`/`createUser`/`updateUser`/`deleteUserByUuid` (all `@Override` on `UserManagementAdminApiSpec`). Uses `import org.springframework.web.bind.annotation.*` and already imports `HttpStatus`, `APPLICATION_JSON_VALUE`, `UserResponseDto`.

The 3 tests to move live in `UserManagementControllerTest` under `// ---------- POST /register/auto/single ----------`: `shouldRejectRegisterAutoSingleWhenAnonymousAfterBug201Fix`, `shouldRejectRegisterAutoSingleAsRoleUserReturning403`, `shouldRegisterAutoSingleAsAdminReturning201`. Both test classes use the SAME mock field name `userManagementServiceImp` (no rename needed). The source test uses constant `KEYCLOAK_ID`; the admin test uses `ADMIN_KEYCLOAK_ID` / `USER_KEYCLOAK_ID`.

---

## File Structure

**Modify (add to):**
- `.../constants/CyberTechAppConstants.java` — add 4 `RESPONSE_KEY_*` constants.
- `.../api/controllers/spec/UserManagementAdminApiSpec.java` — add `registerAuto` declaration.
- `.../api/controllers/implementation/UserManagementAdminController.java` — add `registerAuto` endpoint.
- `.../test/.../user/UserManagementAdminControllerTest.java` — add 3 moved tests at new path.

**Modify (remove from):**
- `.../api/controllers/implementation/UserManagementController.java` — remove `registerAuto` + the 4 local constants + the `generateUserCreateRequestDto` import; switch `register` to the global constants via static import.
- `.../test/.../user/UserManagementControllerTest.java` — remove the 3 tests + the `REGISTER_AUTO_SINGLE_ENDPOINT` constant + now-unused imports.

**Unchanged:** `UserControllerApiSpec` (never declared `registerAuto`), `UserManagementService`/`Imp`, all service tests.

---

## Task 1: Add the 3 admin tests (RED)

**Files:**
- Modify: `src/test/java/com/novatech/cybertech/api/controllers/implementation/user/UserManagementAdminControllerTest.java`

- [ ] **Step 1: Add a path constant** next to the existing endpoint constants:

```java
    private static final String REGISTER_AUTO_SINGLE_ENDPOINT = "/api/v1/services/admin/user/register/auto/single";
```

- [ ] **Step 2: Move the 3 test methods** from `UserManagementControllerTest` (the `// ---------- POST /register/auto/single ----------` section) into this class, verbatim, with these adaptations:
  - Repoint the URL constant `REGISTER_AUTO_SINGLE_ENDPOINT` (now the admin path defined in Step 1).
  - Replace `jwtUser(KEYCLOAK_ID)` → `jwtUser(USER_KEYCLOAK_ID)` and `jwtAdmin(KEYCLOAK_ID)` → `jwtAdmin(ADMIN_KEYCLOAK_ID)` (this class's existing constants).
  - Keep method names + assertions identical (incl. the `$.keycloakId` happy-path assertion).

The 3 methods: `shouldRejectRegisterAutoSingleWhenAnonymousAfterBug201Fix`, `shouldRejectRegisterAutoSingleAsRoleUserReturning403`, `shouldRegisterAutoSingleAsAdminReturning201`.

- [ ] **Step 3: Add any missing imports.** The admin test already imports `post`, `csrf`, `jwtAdmin`, `jwtUser`, `when`, `any`, `UserCreateRequestDto`, `UserResponseDto`, `UserDtoFixtures`, `jsonPath`, `status`, `content`, `APPLICATION_JSON`. Verify nothing else the 3 bodies reference is missing; add only what's absent (the compiler is the source of truth).

- [ ] **Step 4: Run the admin test → confirm RED**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw test -Dtest='UserManagementAdminControllerTest' -DfailIfNoTests=false`
Expected: `shouldRegisterAutoSingleAsAdminReturning201` FAILS (404 — endpoint not mapped on the admin controller yet). The two security-only cases (anonymous 401, user 403) may already pass (class-level guard fires before routing); fine. The meaningful RED is the admin happy path returning 404.

(Do NOT commit — Task 2 makes it green.)

---

## Task 2: Add the endpoint + constants + ApiSpec (GREEN)

**Files:**
- Modify: `src/main/java/com/novatech/cybertech/constants/CyberTechAppConstants.java`
- Modify: `src/main/java/com/novatech/cybertech/api/controllers/spec/UserManagementAdminApiSpec.java`
- Modify: `src/main/java/com/novatech/cybertech/api/controllers/implementation/UserManagementAdminController.java`

- [ ] **Step 1: Add the 4 response-key constants to `CyberTechAppConstants`** (place them in a sensible spot, e.g. near the pagination defaults, with this comment):

```java
    // Shared JSON response-body keys for the user register / register-auto endpoints (extracted
    // here so the same semantic literals are not duplicated across UserManagementController and
    // UserManagementAdminController — see CLAUDE.md "constantes obligatoires").
    public static final String RESPONSE_KEY_ID = "id";
    public static final String RESPONSE_KEY_KEYCLOAK_ID = "keycloakId";
    public static final String RESPONSE_KEY_EMAIL = "email";
    public static final String RESPONSE_KEY_USERNAME = "username";
```

- [ ] **Step 2: Add the `registerAuto` declaration to `UserManagementAdminApiSpec`** (append after `deleteUserByUuid`):

```java
    @Operation(summary = "Register an auto-generated synthetic user (Admin debug helper)",
            description = "Admin-only developer / load-test utility that mints a synthetic user (Keycloak + DB) from random data via the data generator. Returns a compact map including the generated keycloakId (which UserResponseDto @JsonIgnores) so the admin caller can identify the synthetic user.",
            security = @SecurityRequirement(name = "keycloak"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Synthetic user created; body = { id, keycloakId, email, username }"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - ADMIN role required", content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Map<String, Object>> registerAuto();
```

Add the import `import java.util.Map;` to this interface (the other annotations — `@Operation`, `@ApiResponse`, `@Content`, `@Schema`, `@SecurityRequirement`, `ErrorResponseDto`, `APPLICATION_JSON_VALUE` — are already imported).

- [ ] **Step 3: Add the `registerAuto` endpoint to `UserManagementAdminController`** (append after `deleteUserByUuid`):

```java
    @Override
    @PostMapping(value = "/register/auto/single", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> registerAuto() {
        final UserResponseDto created = userManagementServiceImp.create(generateUserCreateRequestDto());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                RESPONSE_KEY_ID, created.getUuid(),
                RESPONSE_KEY_KEYCLOAK_ID, created.getKeycloakId(),
                RESPONSE_KEY_EMAIL, created.getEmail(),
                RESPONSE_KEY_USERNAME, created.getUsername()
        ));
    }
```

Add these imports to the controller:

```java
import java.util.Map;
import static com.novatech.cybertech.utils.DataGenerator.generateUserCreateRequestDto;
import static com.novatech.cybertech.constants.CyberTechAppConstants.RESPONSE_KEY_ID;
import static com.novatech.cybertech.constants.CyberTechAppConstants.RESPONSE_KEY_KEYCLOAK_ID;
import static com.novatech.cybertech.constants.CyberTechAppConstants.RESPONSE_KEY_EMAIL;
import static com.novatech.cybertech.constants.CyberTechAppConstants.RESPONSE_KEY_USERNAME;
```

(`@PostMapping` is covered by the existing `import org.springframework.web.bind.annotation.*`; `HttpStatus`, `ResponseEntity`, `UserResponseDto`, `APPLICATION_JSON_VALUE` already imported. No method-level `@PreAuthorize` — the class is already ADMIN-gated.)

- [ ] **Step 4: Run the admin test → GREEN**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw test -Dtest='UserManagementAdminControllerTest' -DfailIfNoTests=false`
Expected: BUILD SUCCESS, all green (existing admin tests + the 3 moved).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/novatech/cybertech/constants/CyberTechAppConstants.java src/main/java/com/novatech/cybertech/api/controllers/spec/UserManagementAdminApiSpec.java src/main/java/com/novatech/cybertech/api/controllers/implementation/UserManagementAdminController.java src/test/java/com/novatech/cybertech/api/controllers/implementation/user/UserManagementAdminControllerTest.java
git commit -m "feat(user): move admin register/auto/single into UserManagementAdminController + lift response-key constants"
```

---

## Task 3: Remove `registerAuto` from the user controller

**Files:**
- Modify: `src/main/java/com/novatech/cybertech/api/controllers/implementation/UserManagementController.java`

- [ ] **Step 1: Delete the `registerAuto()` method** (with its full javadoc + `@PreAuthorize` + `@PostMapping`).

- [ ] **Step 2: Delete the 4 local constant declarations** (`RESPONSE_KEY_ID`, `RESPONSE_KEY_KEYCLOAK_ID`, `RESPONSE_KEY_EMAIL`, `RESPONSE_KEY_USERNAME`).

- [ ] **Step 3: Re-point `register()` to the global constants via static import.** Add:

```java
import static com.novatech.cybertech.constants.CyberTechAppConstants.RESPONSE_KEY_ID;
import static com.novatech.cybertech.constants.CyberTechAppConstants.RESPONSE_KEY_EMAIL;
import static com.novatech.cybertech.constants.CyberTechAppConstants.RESPONSE_KEY_USERNAME;
```

Because these static imports have the SAME names the local constants had, the body of `register()` (which references `RESPONSE_KEY_ID/EMAIL/USERNAME`) compiles unchanged. Do NOT import `RESPONSE_KEY_KEYCLOAK_ID` here — nothing in this class uses it any more.

- [ ] **Step 4: Remove the now-unused import** `import static com.novatech.cybertech.utils.DataGenerator.generateUserCreateRequestDto;` (only `registerAuto` used it).

- [ ] **Step 5: Verify compile**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw -q compile`
Expected: BUILD SUCCESS. (If the build flags any other now-unused import — e.g. nothing else does — remove it.)

(Do NOT commit — the user test still references the removed endpoint; Task 4 fixes it.)

---

## Task 4: Remove the 3 tests from the user controller test

**Files:**
- Modify: `src/test/java/com/novatech/cybertech/api/controllers/implementation/user/UserManagementControllerTest.java`

- [ ] **Step 1: Delete the 3 moved test methods** (the entire `// ---------- POST /register/auto/single ----------` section).

- [ ] **Step 2: Delete the now-unused constant** `REGISTER_AUTO_SINGLE_ENDPOINT`.

- [ ] **Step 3: Remove any import left unused** by the deletions (let the compiler confirm; e.g. if `jwtAdmin` is no longer used elsewhere in this file remove it — but verify, several register/get tests may still use it). A clean build must have zero unused imports.

- [ ] **Step 4: Run both user controller test slices**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw test -Dtest='UserManagementControllerTest,UserManagementAdminControllerTest' -DfailIfNoTests=false`
Expected: BUILD SUCCESS, no failures. User slice keeps `register`/`get`/`updateMe`/`health` tests (incl. `registerResponseShouldNotContainKeycloakId`); admin slice has the moved 3 plus its originals.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/novatech/cybertech/api/controllers/implementation/UserManagementController.java src/test/java/com/novatech/cybertech/api/controllers/implementation/user/UserManagementControllerTest.java
git commit -m "refactor(user): remove admin register/auto/single from user controller"
```

---

## Task 5: Doc sync + full-suite verification

- [ ] **Step 1: Sync docs.** Grep `docs/FRONTEND_SITREP.md` and `docs/postman/README.md` for `user/register/auto/single`. Where it appears at the old `/api/v1/services/user/register/auto/single` path (and any role label of USER), update to `/api/v1/services/admin/user/register/auto/single` (ADMIN). Commit any change: `docs: update register/auto/single endpoint to /admin/user path`.

- [ ] **Step 2: Run the full unit suite**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw test -DfailIfNoTests=false`
Expected: `BUILD SUCCESS`, `Failures: 0, Errors: 0`. Report the count. This closes the controller-split effort (BankCard + Order + User).

---

## Self-Review notes

- **Spec coverage:** move registerAuto (Tasks 1/2/3) ✓, add admin ApiSpec declaration (Task 2) ✓, lift shared constants to global per CLAUDE.md (Tasks 2/3) ✓, tests moved (Tasks 1/4) ✓, doc sync (Task 5) ✓, full-suite gate (Task 5) ✓, service untouched ✓, `UserControllerApiSpec` untouched (registerAuto was never on it) ✓.
- **Behavior preservation:** path gains the `/admin` prefix only; verb (POST) and response-map shape (`{id, keycloakId, email, username}`) unchanged; the method-level `@PreAuthorize("hasRole('ADMIN')")` is dropped on the move because the destination class enforces ADMIN at class level — net authorization identical.
- **Constant safety:** `register()` body is unchanged because the lifted constants are static-imported under the same names; `RESPONSE_KEY_KEYCLOAK_ID` is no longer imported into the user controller (only `registerAuto` used it, now in the admin controller).
- **Type consistency:** `registerAuto()` signature (`ResponseEntity<Map<String, Object>>`, no args) matches between the new ApiSpec declaration and the controller `@Override`; service call `create(generateUserCreateRequestDto())` unchanged.
- **Compile-safety ordering:** admin side gains the endpoint+tests first (old still present, no path clash — `/admin/user/...` vs `/user/...`, all green); old endpoint + tests removed together in Tasks 3–4.
