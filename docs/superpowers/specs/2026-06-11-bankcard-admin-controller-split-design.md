# BankCard — USER/ADMIN controller split (pilot)

**Date:** 2026-06-11
**Scope:** Controllers only. No service-layer changes.
**Status:** Validated design, ready for implementation plan.

## Goal

Split the MIXED `BankCardManagementController` (6 user endpoints + 5 admin endpoints in one
class) into a clean USER controller + a dedicated ADMIN controller, replicating the established
precedent (`OrderManagementAdminController`, `UserManagementAdminController`,
`ProductManagementAdminController`, `DiscountAdminController`).

This is the **pilot** of a broader "separate USER controllers from ADMIN controllers" effort.
It establishes the template; later passes replicate it on the remaining MIXED controllers
(`OrderManagementController` → move `place/auto` + `delete/{uuid}`; `UserManagementController`
→ move `register/auto/single`).

### Explicitly OUT of scope (decided)

- **No service split.** The god-service decomposition (`BankCardManagementService` →
  user service + `BankCardAdminService`, PCI-helper extraction) is deferred. The new admin
  controller injects the **existing, unchanged** `BankCardManagementService` — exactly as
  `OrderManagementAdminController` injects the shared `OrderManagementService` today.
- The JWT role model (`{USER, ADMIN}` via `KeycloakRoleConverter` + `@PreAuthorize`) is already
  clean and is not touched.

## Current state

`BankCardManagementController` (`/api/v1/services/bank-card`), no class-level `@PreAuthorize`:

| # | Verb + path | Method | `@PreAuthorize` | Class |
|---|---|---|---|---|
| 1 | POST `/add` | addBankCard | USER or ADMIN | USER |
| 2 | DELETE `/delete` | deleteBankCard | USER or ADMIN | USER |
| 3 | PUT `/update` | updateBankCard | USER or ADMIN | USER |
| 4 | PATCH `/set-default/{cardUuid}` | setDefaultBankCard | USER or ADMIN | USER |
| 5 | GET `/default` | getDefaultBankCard | USER or ADMIN | USER |
| 6 | GET `/all-mine` | getAllMine | USER or ADMIN | USER |
| 7 | GET (root) | getAllBankCards | ADMIN | **ADMIN → move** |
| 8 | GET `/{uuid}` | getBankCardByUuid | ADMIN | **ADMIN → move** |
| 9 | POST (root) | createBankCard | ADMIN | **ADMIN → move** |
| 10 | PUT (root) | updateBankCardAdmin | ADMIN | **ADMIN → move** |
| 11 | DELETE `/{uuid}` | deleteBankCardByUuid | ADMIN | **ADMIN → move** |

All 5 admin endpoints call service methods used by **no one else** (verified: only caller is this
controller; `UserPersistenceService.saveNewUser` calls the USER method `addBankCard`). So moving
them is safe and requires no service change.

## Target state

### New: `BankCardAdminController`

- Base path constant `BANK_CARD_ADMIN_CONTROLLER_BASE_PATH = "/api/v1/services/admin/bank-card"`.
- Class-level `@PreAuthorize("hasRole('ADMIN')")` (like `UserManagementAdminController`).
- `@RequiredArgsConstructor`, injects the existing `BankCardManagementService` (unchanged).
- Implements new ApiSpec `BankCardAdminControllerApiSpec`.
- `@Tag(name = "BankCardAdminController", description = "API for Bank Card management (Admin)")`.

Endpoints (paths **normalised** to the `*Admin` convention):

| Verb + path | Method | Service call |
|---|---|---|
| GET `/get/all` | getAllBankCards(Pageable) | `getAll(pageable)` |
| GET `/get/{uuid}` | getBankCardByUuid(uuid) | `getByUUID(uuid)` |
| POST `/create` | createBankCard(dto) | `create(dto)` |
| PATCH `/update` | updateBankCardAdmin(dto) | `update(dto)` |
| DELETE `/delete/{uuid}` | deleteBankCardByUuid(uuid) | `deleteByUUID(uuid)` |

Pagination default reused: `@PageableDefault(size = DEFAULT_PAGE_SIZE_BANK_CARD, sort = DEFAULT_SORT_FIELD, direction = DESC)`.

### New: `BankCardAdminControllerApiSpec`

OpenAPI contract interface for the 5 admin endpoints. Mirrors
`UserManagementAdminApiSpec` style: `@Operation` + `@ApiResponse` incl. `403 — ADMIN role required`,
`@SecurityRequirement(name = "keycloak")`. The 5 admin method declarations move here out of
`BankCardControllerApiSpec`.

### Modified: `BankCardManagementController`

- Remove endpoints 7–11.
- Keep endpoints 1–6 unchanged (paths, verbs, `@PreAuthorize`, JWT-subject delegation).
- Still injects `BankCardManagementService`.

### Modified: `BankCardControllerApiSpec`

- Remove the 5 admin method declarations (now on `BankCardAdminControllerApiSpec`).
- Keep the 6 user method declarations.

### Unchanged

`BankCardManagementService` (interface — still `extends CrudBaseService`, keeps every method),
`BankCardManagementServiceImp`, `BankCardRepository`, `BankCardMapper`, `CardEncryptionService`,
`UserPersistenceService`, all DTOs, all exceptions.

## Behaviour change

- **Admin endpoint paths change** (breaking for admin clients only):
  `/api/v1/services/bank-card` (root GET/POST/PUT, `/{uuid}`) →
  `/api/v1/services/admin/bank-card/{get/all,get/{uuid},create,update,delete/{uuid}}`.
  Accepted (portfolio project; aligns with sibling admin controllers).
- **User endpoint paths unchanged.**
- Authorization semantics unchanged: admin endpoints remain ADMIN-only (now enforced at class
  level instead of per-method), user endpoints remain USER-or-ADMIN.
- No service logic changes → no functional change to request/response bodies.

## Testing

Behaviour-preserving relocation. Existing tests are the safety net; keep them green.

- **New `BankCardAdminControllerTest`** (`@WebMvcTest(BankCardAdminController.class)` +
  `@Import(TestSecurityConfig.class)` + `@MockitoBean BankCardManagementService`, MockMvc, JSON
  STRICT, csrf + JWT via `JwtTestUtils`) — port the 5 admin-endpoint test cases from the current
  `BankCardManagementControllerTest`, retargeted to the new paths; add a USER→403 assertion per
  endpoint to pin the class-level ADMIN gate.
- **Modify `BankCardManagementControllerTest`** — remove the admin-endpoint cases (and the
  `getAllBankCards/getByUuid/create/updateAdmin/deleteByUuid` references); keep the 6 user cases.
- **No service-layer test changes** (`BankCardManagementServiceImpTest` untouched — the service is
  untouched).
- Run `mvn test` (full unit suite) after; gate on green.

## Replication note (for later passes)

This produces a reusable template: `*AdminController` + `*AdminControllerApiSpec` +
`*_ADMIN_CONTROLLER_BASE_PATH` constant + class-level `@PreAuthorize(ADMIN)` + shared service,
with the user controller/ApiSpec slimmed. Apply verbatim to `Order` (2 endpoints, admin controller
already exists → just move) and `User` (1 endpoint, admin controller already exists → just move).
