# BankCard USER/ADMIN Controller Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the 5 ADMIN endpoints out of the MIXED `BankCardManagementController` into a dedicated `BankCardAdminController`, leaving a pure-USER controller — with no service-layer changes.

**Architecture:** Controllers-only separation. A new `BankCardAdminController` (class-level `@PreAuthorize("hasRole('ADMIN')")`, base path `/api/v1/services/admin/bank-card`) injects the **unchanged** `BankCardManagementService` — identical to how `OrderManagementAdminController` injects the shared `OrderManagementService`. The admin endpoint paths are normalised to the `*Admin` convention (`/get/all`, `/get/{uuid}`, `/create`, `/update`, `/delete/{uuid}`). The user controller and its ApiSpec are slimmed to the 6 user endpoints.

**Tech Stack:** Spring Boot 4 MVC, Spring Security method security (`@PreAuthorize`), springdoc-openapi (`*ApiSpec` interfaces), JUnit 5 + MockMvc `@WebMvcTest` + `TestSecurityConfig` + `JwtTestUtils`.

**Build command (this project):** all `mvn` calls run with JDK 26:
`JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw <args>` (Bash tool). Never offline mode.

---

## File Structure

**Create:**
- `src/main/java/com/novatech/cybertech/api/controllers/spec/BankCardAdminControllerApiSpec.java` — OpenAPI contract for the 5 admin endpoints.
- `src/main/java/com/novatech/cybertech/api/controllers/implementation/BankCardAdminController.java` — admin REST controller.
- `src/test/java/com/novatech/cybertech/api/controllers/implementation/bankcard/BankCardAdminControllerTest.java` — `@WebMvcTest` slice for the admin controller.

**Modify:**
- `src/main/java/com/novatech/cybertech/constants/CyberTechAppConstants.java` — add `BANK_CARD_ADMIN_CONTROLLER_BASE_PATH`.
- `src/main/java/com/novatech/cybertech/api/controllers/implementation/BankCardManagementController.java` — remove the 5 admin endpoints + now-unused imports.
- `src/main/java/com/novatech/cybertech/api/controllers/spec/BankCardControllerApiSpec.java` — remove the 5 admin method declarations + now-unused imports.
- `src/test/java/com/novatech/cybertech/api/controllers/implementation/bankcard/BankCardManagementControllerTest.java` — remove the 16 admin-endpoint test cases + now-unused constants/imports.

**Unchanged (do NOT touch):** `BankCardManagementService`, `BankCardManagementServiceImp`, `BankCardManagementServiceImpTest`, `BankCardRepository`, `BankCardMapper`, `CardEncryptionService`, `UserPersistenceService`, all DTOs/exceptions.

---

## Task 1: Add the admin base-path constant

**Files:**
- Modify: `src/main/java/com/novatech/cybertech/constants/CyberTechAppConstants.java:18`

- [ ] **Step 1: Add the constant**

In `CyberTechAppConstants.java`, immediately after the existing line (line 18):

```java
    public static final String BANK_CARD_CRUD_CONTROLLER_BASE_PATH = API_BASE_PATH + "/services/bank-card";
```

add:

```java
    /** Admin-only bank-card back-office. Mirrors the USER_MANAGEMENT_ADMIN_CONTROLLER_BASE_PATH convention. */
    public static final String BANK_CARD_ADMIN_CONTROLLER_BASE_PATH = API_BASE_PATH + "/services/admin/bank-card";
```

- [ ] **Step 2: Verify it compiles**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/novatech/cybertech/constants/CyberTechAppConstants.java
git commit -m "refactor(bankcard): add admin controller base-path constant"
```

---

## Task 2: Write the admin controller test (RED)

This is the new behaviour contract: the 5 admin endpoints live under `/api/v1/services/admin/bank-card`, are ADMIN-gated at the class level (USER → 403), and forward to the same service methods. The 16 cases below are ported from the current `BankCardManagementControllerTest` admin section, retargeted to the new paths, with the admin `update` verb changed from PUT to PATCH.

**Files:**
- Create: `src/test/java/com/novatech/cybertech/api/controllers/implementation/bankcard/BankCardAdminControllerTest.java`

- [ ] **Step 1: Write the failing test file**

```java
package com.novatech.cybertech.api.controllers.implementation.bankcard;

import com.novatech.cybertech.api.controllers.TestSecurityConfig;
import com.novatech.cybertech.api.controllers.implementation.BankCardAdminController;
import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.exceptions.BankCardNotFoundException;
import com.novatech.cybertech.fixtures.dto.UserDtoFixtures;
import com.novatech.cybertech.services.core.BankCardManagementService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.FUNCTIONAL;
import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.TECHNICAL;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtAdmin;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtUser;
import static com.novatech.cybertech.utils.TestUtils.asJsonString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice for {@link BankCardAdminController}. Verifies the admin-only bank-card
 * endpoints at their new /admin/bank-card paths: ADMIN reaches them, USER is forbidden (class-level
 * @PreAuthorize), anonymous is unauthorized, and the F2 exception handlers still map domain
 * exceptions to the proper 4xx codes. Ported from BankCardManagementControllerTest's admin section.
 */
@Slf4j
@Import({TestSecurityConfig.class})
@WebMvcTest(value = BankCardAdminController.class)
class BankCardAdminControllerTest {

    private static final String BASE = "/api/v1/services/admin/bank-card";
    private static final String GET_ALL = BASE + "/get/all";
    private static final String GET_BY_UUID = BASE + "/get/{uuid}";
    private static final String CREATE = BASE + "/create";
    private static final String UPDATE = BASE + "/update";
    private static final String DELETE_BY_UUID = BASE + "/delete/{uuid}";

    private static final String KEYCLOAK_ID = "keycloak-subject-id";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BankCardManagementService bankCardService;

    // ---------- GET /get/all ----------

    @Test
    void shouldGetAllBankCardsSuccessfully() throws Exception {
        BankCardResponseDto a = UserDtoFixtures.aSampleBankCardResponse();
        BankCardResponseDto b = UserDtoFixtures.aSampleBankCardResponse();
        Page<BankCardResponseDto> page = new PageImpl<>(List.of(a, b), PageRequest.of(0, 10), 2);

        when(bankCardService.getAll(any())).thenReturn(page);

        mockMvc.perform(get(GET_ALL)
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].uuid").value(a.getUuid().toString()))
                .andExpect(jsonPath("$.content[1].uuid").value(b.getUuid().toString()));
    }

    @Test
    void shouldFailGettingAllBankCardsWhenAnonymousCauseUnauthorized() throws Exception {
        mockMvc.perform(get(GET_ALL)
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldFailGettingAllBankCardsAsNonAdminCauseForbidden() throws Exception {
        mockMvc.perform(get(GET_ALL)
                        .with(jwtUser(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // ---------- GET /get/{uuid} ----------

    @Test
    void shouldGetBankCardByUuidSuccessfully() throws Exception {
        UUID cardUuid = UUID.randomUUID();
        BankCardResponseDto response = UserDtoFixtures.aSampleBankCardResponseBuilder().uuid(cardUuid).build();

        when(bankCardService.getByUUID(cardUuid)).thenReturn(response);

        mockMvc.perform(get(GET_BY_UUID, cardUuid)
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));
    }

    @Test
    void shouldFailGettingBankCardByUuidWhenNotFound() throws Exception {
        UUID cardUuid = UUID.randomUUID();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Bank card not found by UUID")
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        when(bankCardService.getByUUID(cardUuid))
                .thenThrow(new BankCardNotFoundException("Bank card not found by UUID"));

        mockMvc.perform(get(GET_BY_UUID, cardUuid)
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldFailGettingBankCardByUuidWhenPathUuidMalformed() throws Exception {
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Invalid value for parameter 'uuid'")
                .httpStatusCode(400)
                .errorCodeType(TECHNICAL)
                .build();

        mockMvc.perform(get(BASE + "/get/not-a-uuid")
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldFailGettingBankCardByUuidAsNonAdminCauseForbidden() throws Exception {
        UUID cardUuid = UUID.randomUUID();
        mockMvc.perform(get(GET_BY_UUID, cardUuid)
                        .with(jwtUser(KEYCLOAK_ID))
                        .accept(APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // ---------- POST /create ----------

    @Test
    void shouldCreateBankCardAdminSuccessfully() throws Exception {
        BankCardCreationRequestDto request = UserDtoFixtures.aValidBankCardCreationRequest();
        BankCardResponseDto response = UserDtoFixtures.aSampleBankCardResponse();

        when(bankCardService.create(any(BankCardCreationRequestDto.class))).thenReturn(response);

        mockMvc.perform(post(CREATE)
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));
    }

    @Test
    void shouldFailCreatingBankCardAdminWhenDtoBadRequest() throws Exception {
        BankCardCreationRequestDto bad = new BankCardCreationRequestDto();

        mockMvc.perform(post(CREATE)
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.message", startsWith("Validation failed:")))
                .andExpect(jsonPath("$.httpStatusCode").value(400))
                .andExpect(jsonPath("$.errorCodeType").value("TECHNICAL"));
    }

    @Test
    void shouldFailCreatingBankCardAdminAsNonAdminCauseForbidden() throws Exception {
        BankCardCreationRequestDto request = UserDtoFixtures.aValidBankCardCreationRequest();
        mockMvc.perform(post(CREATE)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isForbidden());
    }

    // ---------- PATCH /update ----------

    @Test
    void shouldUpdateBankCardAdminSuccessfully() throws Exception {
        BankCardUpdateRequestDto request = UserDtoFixtures.aValidBankCardUpdateRequest();
        BankCardResponseDto response = UserDtoFixtures.aSampleBankCardResponse();

        when(bankCardService.update(any(BankCardUpdateRequestDto.class))).thenReturn(response);

        mockMvc.perform(patch(UPDATE)
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(response), STRICT));
    }

    @Test
    void shouldFailUpdatingBankCardAdminAsNonAdminCauseForbidden() throws Exception {
        BankCardUpdateRequestDto request = UserDtoFixtures.aValidBankCardUpdateRequest();
        mockMvc.perform(patch(UPDATE)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .accept(APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isForbidden());
    }

    // ---------- DELETE /delete/{uuid} ----------

    @Test
    void shouldDeleteBankCardByUuidAdminSuccessfully() throws Exception {
        UUID cardUuid = UUID.randomUUID();
        doNothing().when(bankCardService).deleteByUUID(cardUuid);

        mockMvc.perform(delete(DELETE_BY_UUID, cardUuid)
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(bankCardService).deleteByUUID(cardUuid);
    }

    @Test
    void shouldFailDeletingBankCardByUuidWhenNotFound() throws Exception {
        UUID cardUuid = UUID.randomUUID();
        ErrorResponseDto errorResponseDto = ErrorResponseDto.builder()
                .message("Bank card not found for delete")
                .httpStatusCode(404)
                .errorCodeType(FUNCTIONAL)
                .build();

        doThrow(new BankCardNotFoundException("Bank card not found for delete"))
                .when(bankCardService).deleteByUUID(cardUuid);

        mockMvc.perform(delete(DELETE_BY_UUID, cardUuid)
                        .with(jwtAdmin(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(content().json(asJsonString(errorResponseDto), STRICT));
    }

    @Test
    void shouldFailDeletingBankCardByUuidWhenAnonymousCauseUnauthorized() throws Exception {
        UUID cardUuid = UUID.randomUUID();

        mockMvc.perform(delete(DELETE_BY_UUID, cardUuid)
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldFailDeletingBankCardByUuidAsNonAdminCauseForbidden() throws Exception {
        UUID cardUuid = UUID.randomUUID();
        mockMvc.perform(delete(DELETE_BY_UUID, cardUuid)
                        .with(jwtUser(KEYCLOAK_ID))
                        .with(csrf())
                        .accept(APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (compile failure — classes missing)**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw -q test -Dtest='BankCardAdminControllerTest' -DfailIfNoTests=false`
Expected: COMPILE FAILURE — `cannot find symbol: class BankCardAdminController`. This is the correct RED: the controller does not exist yet.

(Do NOT commit yet — the tree does not compile. Tasks 3–4 make it green.)

---

## Task 3: Create the admin ApiSpec

**Files:**
- Create: `src/main/java/com/novatech/cybertech/api/controllers/spec/BankCardAdminControllerApiSpec.java`

- [ ] **Step 1: Write the ApiSpec interface**

```java
package com.novatech.cybertech.api.controllers.spec;

import com.novatech.cybertech.api.error.model.ErrorResponseDto;
import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Tag(name = "Bank Card Admin", description = "Admin-only endpoints for bank card CRUD")
public interface BankCardAdminControllerApiSpec {

    @Operation(summary = "Get all bank cards (Admin)",
            description = "Retrieves every bank card in the system (paginated).",
            security = @SecurityRequirement(name = "keycloak"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Page of bank cards",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, array = @ArraySchema(schema = @Schema(implementation = BankCardResponseDto.class)))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - JWT token missing or invalid",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - ADMIN role required",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Page<BankCardResponseDto>> getAllBankCards(final Pageable pageable);

    @Operation(summary = "Get bank card by UUID (Admin)",
            description = "Retrieves a specific bank card by its UUID.",
            security = @SecurityRequirement(name = "keycloak"),
            parameters = {@Parameter(name = "uuid", description = "UUID of the bank card")},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Bank card found",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - ADMIN role required",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Bank card not found",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> getBankCardByUuid(UUID uuid);

    @Operation(summary = "Create a bank card (Admin)",
            description = "Creates a bank card directly linked to a user UUID.",
            security = @SecurityRequirement(name = "keycloak"),
            requestBody = @RequestBody(content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardCreationRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Bank card created",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input data / Validation error",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - ADMIN role required",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> createBankCard(BankCardCreationRequestDto dto);

    @Operation(summary = "Update a bank card (Admin)",
            description = "Updates a bank card by UUID (UUID carried in the request body).",
            security = @SecurityRequirement(name = "keycloak"),
            requestBody = @RequestBody(content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardUpdateRequestDto.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Bank card updated",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = BankCardResponseDto.class))),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - ADMIN role required",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Bank card not found",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<BankCardResponseDto> updateBankCardAdmin(BankCardUpdateRequestDto dto);

    @Operation(summary = "Delete a bank card by UUID (Admin)",
            description = "Deletes a bank card by its UUID.",
            security = @SecurityRequirement(name = "keycloak"),
            parameters = {@Parameter(name = "uuid", description = "UUID of the bank card")},
            responses = {
                    @ApiResponse(responseCode = "204", description = "Bank card deleted"),
                    @ApiResponse(responseCode = "403", description = "Operation forbidden - ADMIN role required",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Bank card not found",
                            content = @Content(mediaType = APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponseDto.class)))
            })
    ResponseEntity<Void> deleteBankCardByUuid(UUID uuid);
}
```

- [ ] **Step 2: Verify it compiles**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw -q compile`
Expected: BUILD SUCCESS.

---

## Task 4: Create the admin controller (GREEN)

**Files:**
- Create: `src/main/java/com/novatech/cybertech/api/controllers/implementation/BankCardAdminController.java`

- [ ] **Step 1: Write the controller**

```java
package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.BankCardAdminControllerApiSpec;
import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.services.core.BankCardManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.BANK_CARD_ADMIN_CONTROLLER_BASE_PATH;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_PAGE_SIZE_BANK_CARD;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_SORT_FIELD;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Admin-only bank-card back-office. Hosts the cross-user CRUD endpoints that used to live on
 * {@link BankCardManagementController}. Injects the same {@link BankCardManagementService} — the
 * service was intentionally left unchanged in this controllers-only split (mirrors how
 * {@code OrderManagementAdminController} shares {@code OrderManagementService}).
 *
 * <p>OpenAPI documentation lives on {@link BankCardAdminControllerApiSpec}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping(version = APP_API_VERSION, value = BANK_CARD_ADMIN_CONTROLLER_BASE_PATH)
@Tag(name = "BankCardAdminController", description = "API for Bank Card management (Admin)")
public class BankCardAdminController implements BankCardAdminControllerApiSpec {

    private final BankCardManagementService bankCardService;

    @Override
    @GetMapping(value = "/get/all", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<BankCardResponseDto>> getAllBankCards(
            @PageableDefault(size = DEFAULT_PAGE_SIZE_BANK_CARD, sort = DEFAULT_SORT_FIELD, direction = Sort.Direction.DESC) final Pageable pageable
    ) {
        return ResponseEntity.ok(bankCardService.getAll(pageable));
    }

    @Override
    @GetMapping(value = "/get/{uuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<BankCardResponseDto> getBankCardByUuid(@PathVariable UUID uuid) {
        return ResponseEntity.ok(bankCardService.getByUUID(uuid));
    }

    @Override
    @PostMapping(value = "/create", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<BankCardResponseDto> createBankCard(@Valid @RequestBody BankCardCreationRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bankCardService.create(dto));
    }

    @Override
    @PatchMapping(value = "/update", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<BankCardResponseDto> updateBankCardAdmin(@Valid @RequestBody BankCardUpdateRequestDto dto) {
        return ResponseEntity.ok(bankCardService.update(dto));
    }

    @Override
    @DeleteMapping(value = "/delete/{uuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteBankCardByUuid(@PathVariable UUID uuid) {
        bankCardService.deleteByUUID(uuid);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Run the admin test to verify it passes**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw -q test -Dtest='BankCardAdminControllerTest' -DfailIfNoTests=false`
Expected: PASS — `Tests run: 16, Failures: 0, Errors: 0`.

> Note: at this point the OLD admin endpoints still exist on `BankCardManagementController` (at the old `/bank-card` root paths). There is no Spring mapping conflict because the new controller's paths are all under `/admin/bank-card`. Both old and new tests are green. Task 5 removes the old endpoints.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/novatech/cybertech/api/controllers/spec/BankCardAdminControllerApiSpec.java src/main/java/com/novatech/cybertech/api/controllers/implementation/BankCardAdminController.java src/test/java/com/novatech/cybertech/api/controllers/implementation/bankcard/BankCardAdminControllerTest.java
git commit -m "feat(bankcard): add dedicated BankCardAdminController + ApiSpec + test"
```

---

## Task 5: Slim the user controller + its ApiSpec (remove admin endpoints)

**Files:**
- Modify: `src/main/java/com/novatech/cybertech/api/controllers/implementation/BankCardManagementController.java`
- Modify: `src/main/java/com/novatech/cybertech/api/controllers/spec/BankCardControllerApiSpec.java`

- [ ] **Step 1: Remove the 5 admin endpoints from `BankCardManagementController`**

Delete the entire block from the comment `// --- Admin CRUD endpoints — BUG-161: now require ROLE_ADMIN ---` through the end of `deleteBankCardByUuid(...)` — i.e. these 5 methods and their annotations:
`getAllBankCards`, `getBankCardByUuid`, `createBankCard`, `updateBankCardAdmin`, `deleteBankCardByUuid`.

Then remove the now-unused imports from that file:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_PAGE_SIZE_BANK_CARD;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_SORT_FIELD;
```

Keep `import java.util.List;` and `import java.util.UUID;` (still used by `getAllMine` / `setDefaultBankCard`). The class still `implements BankCardControllerApiSpec` and keeps the 6 user methods (`addBankCard`, `deleteBankCard`, `updateBankCard`, `setDefaultBankCard`, `getDefaultBankCard`, `getAllMine`).

- [ ] **Step 2: Remove the 5 admin method declarations from `BankCardControllerApiSpec`**

Delete the block under `// --- Endpoints CRUD Basiques (Non sécurisés comme demandé, ou Admin) ---` containing the 5 declarations: `getAllBankCards`, `getBankCardByUuid`, `createBankCard`, `updateBankCardAdmin`, `deleteBankCardByUuid` (the comment line may be removed too). Then remove the now-unused imports:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
```

Keep all other imports (`ArraySchema` is still used by `getAllMine`; `Parameter` still used by `setDefaultBankCard`; `Jwt`, `List`, `UUID` still used). Update the type-level `@Tag` description to drop the "admin CRUD" mention:

```java
@Tag(name = "Bank Card", description = "Endpoints to manage the authenticated user's bank card")
```

- [ ] **Step 3: Verify compile**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw -q compile`
Expected: BUILD SUCCESS. (If it fails on an unused-import or a leftover `@Override` with no matching spec method, fix per the message — every admin method must be gone from BOTH files.)

(Do NOT commit yet — `BankCardManagementControllerTest` still references the removed endpoints and will fail to compile / fail at runtime. Task 6 fixes it in the same logical change.)

---

## Task 6: Slim the user controller test (remove admin cases)

**Files:**
- Modify: `src/test/java/com/novatech/cybertech/api/controllers/implementation/bankcard/BankCardManagementControllerTest.java`

- [ ] **Step 1: Remove the 16 admin-endpoint test methods**

Delete every test method under these section comments (they now live in `BankCardAdminControllerTest`):
- `// ---------- GET / (admin/all ...) ----------` → `shouldGetAllBankCardsSuccessfully`, `shouldFailGettingAllBankCardsWhenAnonymousCauseUnauthorized`, `shouldFailGettingAllBankCardsAsNonAdminCauseForbidden`
- `// ---------- GET /{uuid} ----------` → `shouldGetBankCardByUuidSuccessfully`, `shouldFailGettingBankCardByUuidWhenNotFound`, `shouldFailGettingBankCardByUuidWhenPathUuidMalformed`, `shouldFailGettingBankCardByUuidAsNonAdminCauseForbidden`
- `// ---------- POST / (admin create) ----------` → `shouldCreateBankCardAdminSuccessfully`, `shouldFailCreatingBankCardAdminWhenDtoBadRequest`, `shouldFailCreatingBankCardAdminAsNonAdminCauseForbidden`
- `// ---------- PUT / (admin update) ----------` → `shouldUpdateBankCardAdminSuccessfully`, `shouldFailUpdatingBankCardAdminAsNonAdminCauseForbidden`
- `// ---------- DELETE /{uuid} (admin) ----------` → `shouldDeleteBankCardByUuidAdminSuccessfully`, `shouldFailDeletingBankCardByUuidWhenNotFound`, `shouldFailDeletingBankCardByUuidWhenAnonymousCauseUnauthorized`, `shouldFailDeletingBankCardByUuidAsNonAdminCauseForbidden`

Keep the user sections: `POST /add`, `DELETE /delete`, `PUT /update`, `GET /all-mine`.

- [ ] **Step 2: Remove the now-unused path constants and imports**

Delete these constants (now unused — they pointed at the admin endpoints):

```java
    private static final String GET_ALL = BASE;
    private static final String GET_BY_UUID = BASE + "/{uuid}";
    private static final String CREATE_ADMIN = BASE;
    private static final String UPDATE_ADMIN = BASE;
    private static final String DELETE_ADMIN_BY_UUID = BASE + "/{uuid}";
```

Then remove any import left unused by the deletions. After removing the admin tests, these become unused and MUST be deleted to keep the build clean:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import static com.novatech.cybertech.api.error.enumpackage.ErrorCodeType.TECHNICAL;
import static com.novatech.cybertech.fixtures.support.JwtTestUtils.jwtAdmin;
```

Keep `jwtUser`, `get`, `post`, `put`, `delete`, `FUNCTIONAL`, `startsWith`, etc. (still used by the user tests). If `mvn` later flags any other import as unused, remove it too.

- [ ] **Step 3: Run both bankcard controller test slices**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw -q test -Dtest='BankCardManagementControllerTest,BankCardAdminControllerTest' -DfailIfNoTests=false`
Expected: PASS — user slice keeps its user-endpoint tests, admin slice keeps its 16.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/novatech/cybertech/api/controllers/implementation/BankCardManagementController.java src/main/java/com/novatech/cybertech/api/controllers/spec/BankCardControllerApiSpec.java src/test/java/com/novatech/cybertech/api/controllers/implementation/bankcard/BankCardManagementControllerTest.java
git commit -m "refactor(bankcard): remove admin endpoints from user controller + ApiSpec"
```

---

## Task 7: Full suite verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full unit suite**

Run: `JAVA_HOME="/c/Program Files/Java/jdk-26" ./mvnw test -DfailIfNoTests=false`
Expected: `BUILD SUCCESS`, `Tests run: <N>, Failures: 0, Errors: 0` (N ≈ prior count + 16 new admin tests − any duplicate-count artifacts). No failures, no compile errors.

- [ ] **Step 2: If green, the pilot is done.**

Report the final test count. The pilot template (`*AdminController` + `*AdminControllerApiSpec` + `*_ADMIN_CONTROLLER_BASE_PATH` + class-level `@PreAuthorize(ADMIN)` injecting the shared service; user controller/ApiSpec/test slimmed) is now ready to replicate on `Order` (move `place/auto` + `delete/{uuid}` into the existing `OrderManagementAdminController`) and `User` (move `register/auto/single` into the existing `UserManagementAdminController`) in follow-up passes.

---

## Self-Review notes

- **Spec coverage:** new admin controller (Task 4) ✓, new admin ApiSpec (Task 3) ✓, base-path constant (Task 1) ✓, slim user controller (Task 5) ✓, slim user ApiSpec (Task 5) ✓, new admin test + slim user test (Tasks 2/6) ✓, normalised paths ✓, service untouched ✓, full-suite gate (Task 7) ✓.
- **Path/verb normalisation:** admin `update` is `PATCH /update` (was `PUT /` root) to match `UserManagementAdminController`; the user-facing `PUT /update` is untouched. Admin reads use `/get/all` and `/get/{uuid}`; create `/create`; delete `/delete/{uuid}`.
- **Type consistency:** service method names referenced (`getAll(Pageable)`, `getByUUID(UUID)`, `create(dto)`, `update(dto)`, `deleteByUUID(UUID)`) match `BankCardManagementService` exactly; ApiSpec method names match the controller `@Override`s exactly (`getAllBankCards`, `getBankCardByUuid`, `createBankCard`, `updateBankCardAdmin`, `deleteBankCardByUuid`).
- **Compile-safety ordering:** the new controller is added while the old endpoints still exist (no path clash, both green); old endpoints + their tests are removed together in Tasks 5–6 so no intermediate state references a missing handler.
