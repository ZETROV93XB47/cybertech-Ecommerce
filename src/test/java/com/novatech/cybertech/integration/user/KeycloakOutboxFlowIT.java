package com.novatech.cybertech.integration.user;

import com.novatech.cybertech.TestcontainersConfiguration;
import com.novatech.cybertech.batch.base.BaseTasklet;
import com.novatech.cybertech.batch.task.KeycloakOutboxReconciliationTasklet;
import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.entities.KeycloakOutboxEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OutboxOperationType;
import com.novatech.cybertech.entities.enums.OutboxStatus;
import com.novatech.cybertech.entities.enums.Role;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.dto.UserDtoFixtures;
import com.novatech.cybertech.fixtures.support.TestDataCleaner;
import com.novatech.cybertech.fixtures.support.stubs.KeycloakAdminStub;
import com.novatech.cybertech.repositories.KeycloakOutboxRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.UserManagementService;
import com.novatech.cybertech.services.implementation.KeycloakUserManagementService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end IT for the Keycloak outbox saga (CREATE/DELETE legs + tasklet crash recovery)
 * against the real Testcontainers stack. The Keycloak admin client is swapped for the W0
 * {@link KeycloakAdminStub} and the {@link KeycloakUserManagementService} collaborator is spied
 * so no live Keycloak is ever reached (same pattern as {@link UserRegistrationFlowIT}).
 *
 * <p>Pinned end-to-end contracts:
 * <ul>
 *   <li>register happy path leaves exactly one CREATE outbox row, terminal DONE, keycloakId
 *       backfilled — proof the breadcrumb rides the real TX boundaries;</li>
 *   <li>a stale PENDING CREATE row whose email resolves to a Keycloak orphan is compensated
 *       (deleteUser) and flipped FAILED by the real tasklet bean;</li>
 *   <li>the delete flow co-commits a DELETE row that the AFTER_COMMIT listener immediately
 *       reconciles to DONE — no in-memory-only intent left anywhere.</li>
 * </ul>
 */
@Slf4j
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, KeycloakAdminStub.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("KeycloakOutboxFlowIT — outbox saga end-to-end against real containers")
class KeycloakOutboxFlowIT {

    private static final String REGISTER_ENDPOINT = "/api/v1/services/user/register";
    private static final long DEFAULT_STALENESS_MINUTES = 5L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private KeycloakOutboxRepository keycloakOutboxRepository;
    @Autowired private UserManagementService userManagementService;
    @Autowired private KeycloakOutboxReconciliationTasklet tasklet;
    @Autowired private TestDataCleaner testDataCleaner;

    @MockitoSpyBean
    private KeycloakUserManagementService keycloakUserManagementService;

    @BeforeEach
    void wipeAndStubKeycloak() {
        testDataCleaner.wipe();
        doReturn("kc-stub-" + UUID.randomUUID())
                .when(keycloakUserManagementService)
                .createUser(anyString(), anyString(), anyString(), anyString(), any(Role.class));
        doNothing().when(keycloakUserManagementService).deleteUser(anyString());
    }

    @AfterEach
    void restoreStalenessWindow() {
        // The tasklet is a singleton bean shared with the scheduled job — never leave a mutated window behind.
        ReflectionTestUtils.setField(tasklet, "stalenessMinutes", DEFAULT_STALENESS_MINUTES);
    }

    // -----------------------------------------------------------------------------------
    // 1. Happy path — POST /register leaves a terminal DONE CREATE outbox row.
    // -----------------------------------------------------------------------------------
    @Test
    @DisplayName("POST /register happy path → user persisted AND outbox CREATE row is DONE with keycloakId backfilled")
    void registerWritesDoneOutboxRow() throws Exception {
        final String uniqueEmail = "outbox-it+" + UUID.randomUUID() + "@example.com";
        final UserCreateRequestDto request = UserDtoFixtures.aValidCreateRequestBuilder()
                .email(uniqueEmail)
                .bankCardCreationRequestDto(null) // same caveat as UserRegistrationFlowIT
                .build();

        mockMvc.perform(post(REGISTER_ENDPOINT)
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmail(uniqueEmail)).isPresent();

        final List<KeycloakOutboxEntity> rows = keycloakOutboxRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getOperationType()).isEqualTo(OutboxOperationType.CREATE);
        assertThat(rows.get(0).getStatus()).isEqualTo(OutboxStatus.DONE);
        assertThat(rows.get(0).getEmail()).isEqualTo(uniqueEmail);
        assertThat(rows.get(0).getKeycloakId()).as("audit backfill at markDone").isNotBlank();
        assertThat(rows.get(0).getPayload()).as("CREATE rows never carry a payload (no secret at rest)").isNull();
    }

    // -----------------------------------------------------------------------------------
    // 2. Crash recovery — stale PENDING CREATE with a Keycloak orphan → compensation.
    // -----------------------------------------------------------------------------------
    @Test
    @DisplayName("stale PENDING CREATE with a Keycloak orphan → tasklet compensates (deleteUser) and marks FAILED")
    void taskletCompensatesOrphan() throws Exception {
        final String orphanEmail = "orphan-it+" + UUID.randomUUID() + "@example.com";
        final KeycloakOutboxEntity pending = keycloakOutboxRepository.save(KeycloakOutboxEntity.builder()
                .operationType(OutboxOperationType.CREATE)
                .status(OutboxStatus.PENDING)
                .email(orphanEmail)
                .attempts(0)
                .build());
        final UUID rowUuid = pending.getUuid();

        // Keycloak knows the email (the crash happened AFTER the Keycloak create)…
        doReturn(Optional.of("kc-orphan")).when(keycloakUserManagementService).searchByEmail(orphanEmail);
        // …and no DB row exists for kc-orphan (wipe() guaranteed that) → orphan to compensate.

        // @LastModifiedDate stamps updatedAt at save-time, so the row is too fresh for the default
        // window — widen the window (negative => threshold in the future) instead of back-dating,
        // exactly as the unit repo test selects rows deterministically.
        ReflectionTestUtils.setField(tasklet, "stalenessMinutes", -1L);

        tasklet.execute(Mockito.mock(StepContribution.class),
                new BaseTasklet.StepArguments("it-job", LocalDateTime.now(), null, null));

        verify(keycloakUserManagementService).deleteUser("kc-orphan");
        final KeycloakOutboxEntity reconciled = keycloakOutboxRepository.findByUuid(rowUuid).orElseThrow();
        assertThat(reconciled.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(reconciled.getLastError()).contains("compensated");
    }

    // -----------------------------------------------------------------------------------
    // 3. Delete flow — DELETE row co-commits with the SQL delete, listener reconciles to DONE.
    // -----------------------------------------------------------------------------------
    @Test
    @DisplayName("deleteByUUID → DELETE outbox row co-committed and reconciled DONE by the AFTER_COMMIT listener")
    void deleteFlowLeavesDoneOutboxRow() {
        final UserEntity user = userRepository.save(UserEntityBuilder.aValidUserBuilder()
                .keycloakId("kc-it-del-" + UUID.randomUUID())
                .build());

        userManagementService.deleteByUUID(user.getUuid());

        assertThat(userRepository.findByUuid(user.getUuid())).isEmpty();
        verify(keycloakUserManagementService).deleteUser(user.getKeycloakId());

        final List<KeycloakOutboxEntity> rows = keycloakOutboxRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getOperationType()).isEqualTo(OutboxOperationType.DELETE);
        assertThat(rows.get(0).getKeycloakId()).isEqualTo(user.getKeycloakId());
        assertThat(rows.get(0).getStatus())
                .as("AFTER_COMMIT listener reconciles the co-committed row immediately")
                .isEqualTo(OutboxStatus.DONE);
    }
}
