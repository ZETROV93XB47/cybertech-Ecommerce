package com.novatech.cybertech.services.implementation.catalog;

import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserSelfUpdateRequestDto;
import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.Role;
import com.novatech.cybertech.events.UserDeletedEvent;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.dto.UserDtoFixtures;
import com.novatech.cybertech.mappers.entity.UserMapper;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.implementation.KeycloakUserManagementService;
import com.novatech.cybertech.services.implementation.UserManagementServiceImp;
import com.novatech.cybertech.services.implementation.UserPersistenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link UserManagementServiceImp}.
 *
 * <p>SA-W3.5 wave — services/catalog. Pins (post H1 fix):
 * <ul>
 *   <li>auto-admin guard: {@code create()} hardcodes {@link Role#USER} — request payload cannot
 *       elevate.</li>
 *   <li>compensating Keycloak delete when DB persistence fails — runs OUTSIDE any transaction
 *       (delegated to {@link UserPersistenceService} for the SQL work).</li>
 *   <li>compensation failure is logged but the original cause still propagates.</li>
 *   <li>email-change push to Keycloak with no verify-new-email step (tech debt — pinned).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserManagementServiceImpTest {

    @Mock UserMapper userMapper;
    @Mock UserRepository userRepository;
    @Mock KeycloakUserManagementService keycloakUserManagementService;
    @Mock UserPersistenceService userPersistenceService;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks UserManagementServiceImp service;

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("happy path — Keycloak first, then delegates DB persistence to UserPersistenceService, returns mapped DTO")
        void create_happyPath() {
            UserCreateRequestDto req = UserDtoFixtures.aValidCreateRequest();
            String kcId = "kc-123";
            when(keycloakUserManagementService.createUser(req.getEmail(), req.getFirstName(),
                    req.getLastName(), req.getPassword(), Role.USER)).thenReturn(kcId);
            UserResponseDto expected = UserDtoFixtures.aSampleUserResponse();
            when(userPersistenceService.saveNewUser(req, kcId)).thenReturn(expected);

            UserResponseDto result = service.create(req);

            assertThat(result).isSameAs(expected);
            InOrder order = inOrder(keycloakUserManagementService, userPersistenceService);
            order.verify(keycloakUserManagementService).createUser(req.getEmail(), req.getFirstName(),
                    req.getLastName(), req.getPassword(), Role.USER);
            order.verify(userPersistenceService).saveNewUser(req, kcId);
            verify(keycloakUserManagementService, never()).deleteUser(any());
        }

        @Test
        @DisplayName("auto-admin guard: Role.USER is hardcoded regardless of payload (cannot elevate)")
        void create_autoAdminGuard_hardcodesRoleUSER() {
            UserCreateRequestDto req = UserDtoFixtures.aValidCreateRequest();
            String kcId = "kc-x";
            when(keycloakUserManagementService.createUser(any(), any(), any(), any(), any())).thenReturn(kcId);
            when(userPersistenceService.saveNewUser(any(), eq(kcId))).thenReturn(UserDtoFixtures.aSampleUserResponse());

            service.create(req);

            verify(keycloakUserManagementService).createUser(req.getEmail(), req.getFirstName(),
                    req.getLastName(), req.getPassword(), Role.USER);
        }

        @Test
        @DisplayName("DB persistence fails after Keycloak success → compensating Keycloak delete + propagates original cause")
        void create_persistenceFailAfterKeycloak_compensatesKeycloakDelete() {
            UserCreateRequestDto req = UserDtoFixtures.aValidCreateRequest();
            String kcId = "kc-rollback";
            when(keycloakUserManagementService.createUser(any(), any(), any(), any(), any())).thenReturn(kcId);
            when(userPersistenceService.saveNewUser(req, kcId)).thenThrow(new RuntimeException("db down"));

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("db down");

            verify(keycloakUserManagementService).deleteUser(kcId);
        }

        @Test
        @DisplayName("Keycloak create itself fails → no compensating delete (no kcId yet) and DB never touched")
        void create_keycloakFails_noCompensation() {
            UserCreateRequestDto req = UserDtoFixtures.aValidCreateRequest();
            when(keycloakUserManagementService.createUser(any(), any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("kc 500"));

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("kc 500");

            verify(keycloakUserManagementService, never()).deleteUser(any());
            verifyNoInteractions(userPersistenceService);
        }

        @Test
        @DisplayName("compensating delete itself fails → original cause still propagates (operator must reconcile)")
        void create_compensationFails_originalCausePropagates() {
            UserCreateRequestDto req = UserDtoFixtures.aValidCreateRequest();
            String kcId = "kc-double-fail";
            when(keycloakUserManagementService.createUser(any(), any(), any(), any(), any())).thenReturn(kcId);
            when(userPersistenceService.saveNewUser(req, kcId)).thenThrow(new RuntimeException("db down"));
            doThrow(new RuntimeException("kc delete 500"))
                    .when(keycloakUserManagementService).deleteUser(kcId);

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("db down");

            verify(keycloakUserManagementService).deleteUser(kcId);
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("getAll / getByUUID / getByUUIDs")
    class Reads {

        @Test
        @DisplayName("getAll maps repository.findAll() through the mapper")
        void getAll_happyPath() {
            List<UserEntity> entities = List.of(UserEntityBuilder.aValidUser(), UserEntityBuilder.aValidUser());
            List<UserResponseDto> expected = List.of(UserDtoFixtures.aSampleUserResponse(), UserDtoFixtures.aSampleUserResponse());
            when(userRepository.findAll()).thenReturn(entities);
            when(userMapper.mapFromEntityToResponseDto(entities)).thenReturn(expected);

            assertThat(service.getAll()).isEqualTo(expected);
        }

        @Test
        @DisplayName("getByUUID maps the entity when found")
        void getByUUID_happyPath() {
            UUID id = UUID.randomUUID();
            UserEntity entity = UserEntityBuilder.aValidUserBuilder().uuid(id).build();
            UserResponseDto expected = UserDtoFixtures.aSampleUserResponse();
            when(userRepository.findByUuid(id)).thenReturn(Optional.of(entity));
            when(userMapper.mapFromEntityToResponseDto(entity)).thenReturn(expected);

            assertThat(service.getByUUID(id)).isSameAs(expected);
        }

        @Test
        @DisplayName("getByUUID throws UserNotFoundException with the requested uuid")
        void getByUUID_notFound_throws() {
            UUID id = UUID.randomUUID();
            when(userRepository.findByUuid(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByUUID(id))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(id.toString());
        }

        @Test
        @DisplayName("getByUUIDs maps the bulk lookup")
        void getByUUIDs_happyPath() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            List<UUID> ids = List.of(a, b);
            List<UserEntity> entities = List.of(UserEntityBuilder.aValidUserBuilder().uuid(a).build());
            List<UserResponseDto> expected = List.of(UserDtoFixtures.aSampleUserResponse());
            when(userRepository.findAllByUuidIn(ids)).thenReturn(entities);
            when(userMapper.mapFromEntityToResponseDto(entities)).thenReturn(expected);

            assertThat(service.getByUUIDs(ids)).isEqualTo(expected);
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("Bug 3 (Option B) — Keycloak FIRST, THEN DB persisted via UserPersistenceService (REQUIRES_NEW); returns mapped DTO")
        void updateShouldCallKeycloakBeforePersistingInDb() {
            // Bug 3 fix (Option B): Keycloak is called BEFORE the DB write, so a Keycloak rejection
            // (the common failure mode) aborts before either system is mutated. The DB save runs
            // afterwards in UserPersistenceService.updateUser (REQUIRES_NEW).
            UserUpdateRequestDto dto = UserDtoFixtures.aValidUpdateRequest();
            UserEntity user = UserEntityBuilder.aValidUserBuilder().uuid(dto.getUuid()).keycloakId("kc-1").build();
            UserResponseDto expected = UserDtoFixtures.aSampleUserResponse();

            when(userRepository.findByUuid(dto.getUuid())).thenReturn(Optional.of(user));
            when(userPersistenceService.updateUser(dto, user)).thenReturn(expected);

            UserResponseDto result = service.update(dto);

            assertThat(result).isSameAs(expected);
            InOrder order = inOrder(keycloakUserManagementService, userPersistenceService);
            order.verify(keycloakUserManagementService).updateUser("kc-1", dto);
            order.verify(userPersistenceService).updateUser(dto, user);
        }

        @Test
        @DisplayName("missing user throws UserNotFoundException — Keycloak and persistence both untouched")
        void update_notFound_throws() {
            UserUpdateRequestDto dto = UserDtoFixtures.aValidUpdateRequest();
            when(userRepository.findByUuid(dto.getUuid())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(dto))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(dto.getUuid().toString());

            verifyNoInteractions(keycloakUserManagementService);
            verifyNoInteractions(userPersistenceService);
        }

        @Test
        @DisplayName("Bug 3 — email change still propagated to Keycloak (after DB save)")
        void update_emailChange_pushedToKeycloak_noVerifyEmail() {
            UserUpdateRequestDto dto = UserDtoFixtures.aValidUpdateRequest();
            dto.setEmail("changed@example.com");
            UserEntity user = UserEntityBuilder.aValidUserBuilder().uuid(dto.getUuid()).keycloakId("kc-9").build();

            when(userRepository.findByUuid(dto.getUuid())).thenReturn(Optional.of(user));
            when(userPersistenceService.updateUser(dto, user)).thenReturn(UserDtoFixtures.aSampleUserResponse());

            service.update(dto);

            ArgumentCaptor<UserUpdateRequestDto> dtoCaptor = ArgumentCaptor.forClass(UserUpdateRequestDto.class);
            verify(keycloakUserManagementService).updateUser(org.mockito.ArgumentMatchers.eq("kc-9"), dtoCaptor.capture());
            assertThat(dtoCaptor.getValue().getEmail()).isEqualTo("changed@example.com");
            // No verify-new-email API exists — pin: KeycloakUserManagementService is the only outbound call.
        }

        @Test
        @DisplayName("Bug 3 (Option B) — Keycloak fails → DB never touched (no orphan DB mutation)")
        void updateShouldNotPersistInDbWhenKeycloakFails() {
            UserUpdateRequestDto dto = UserDtoFixtures.aValidUpdateRequest();
            UserEntity user = UserEntityBuilder.aValidUserBuilder().uuid(dto.getUuid()).keycloakId("kc-1").build();
            when(userRepository.findByUuid(dto.getUuid())).thenReturn(Optional.of(user));
            doThrow(new RuntimeException("kc down")).when(keycloakUserManagementService).updateUser("kc-1", dto);

            assertThatThrownBy(() -> service.update(dto))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("kc down");

            verifyNoInteractions(userPersistenceService);
        }

        @Test
        @DisplayName("Bug 3 (Option B) — Keycloak OK then DB fails → original DB exception propagates (rare: Keycloak now ahead, logged)")
        void updateShouldPropagateWhenDbFailsAfterKeycloakSuccess() {
            UserUpdateRequestDto dto = UserDtoFixtures.aValidUpdateRequest();
            UserEntity user = UserEntityBuilder.aValidUserBuilder().uuid(dto.getUuid()).keycloakId("kc-2").build();
            when(userRepository.findByUuid(dto.getUuid())).thenReturn(Optional.of(user));
            when(userPersistenceService.updateUser(dto, user)).thenThrow(new RuntimeException("db down"));

            assertThatThrownBy(() -> service.update(dto))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("db down");

            // Keycloak was already updated (phase 1) before the DB write failed.
            verify(keycloakUserManagementService).updateUser("kc-2", dto);
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("Bug 4 — DB delete first then publishes UserDeletedEvent (Keycloak deferred to AFTER_COMMIT listener)")
        void deleteShouldDeleteFromDbAndPublishEvent() {
            UUID id = UUID.randomUUID();
            UserEntity user = UserEntityBuilder.aValidUserBuilder().uuid(id).keycloakId("kc-d").build();
            when(userRepository.findByUuid(id)).thenReturn(Optional.of(user));

            service.deleteByUUID(id);

            InOrder order = inOrder(userRepository, eventPublisher);
            order.verify(userRepository).deleteByUuid(id);
            ArgumentCaptor<UserDeletedEvent> evt = ArgumentCaptor.forClass(UserDeletedEvent.class);
            order.verify(eventPublisher).publishEvent(evt.capture());
            assertThat(evt.getValue().getKeycloakId()).isEqualTo("kc-d");
            // The service no longer calls Keycloak directly — that's the listener's job.
            verifyNoInteractions(keycloakUserManagementService);
        }

        @Test
        @DisplayName("Bug 4 — when DB delete throws, no event is published (no orphan Keycloak delete)")
        void deleteShouldNotPublishEventWhenDbDeleteFails() {
            UUID id = UUID.randomUUID();
            UserEntity user = UserEntityBuilder.aValidUserBuilder().uuid(id).keycloakId("kc-d").build();
            when(userRepository.findByUuid(id)).thenReturn(Optional.of(user));
            doThrow(new RuntimeException("FK violation")).when(userRepository).deleteByUuid(id);

            assertThatThrownBy(() -> service.deleteByUUID(id))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("FK violation");

            verifyNoInteractions(eventPublisher);
            verifyNoInteractions(keycloakUserManagementService);
        }

        @Test
        @DisplayName("deleteByUUID — missing user throws UserNotFoundException, no event, no Keycloak call")
        void deleteByUUID_notFound_throws() {
            UUID id = UUID.randomUUID();
            when(userRepository.findByUuid(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteByUUID(id))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(id.toString());

            verifyNoInteractions(keycloakUserManagementService);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("FIX(SAGA-INCONSISTENCY) — deleteByUUIDs runs SQL bulk delete then publishes one UserDeletedEvent per user (Keycloak deferred to AFTER_COMMIT listener)")
        void deleteByUUIDs_happyPath() {
            // FIX(SAGA-INCONSISTENCY): the previous implementation called Keycloak inside the
            // surrounding @Transactional BEFORE the SQL delete — a late SQL rollback would leave
            // orphan Keycloak deletions for rows still in the DB. We now mirror the singular
            // deleteByUUID pattern: SQL first, then publish events that the listener drains
            // under AFTER_COMMIT.
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UserEntity ua = UserEntityBuilder.aValidUserBuilder().uuid(a).keycloakId("kc-a").build();
            UserEntity ub = UserEntityBuilder.aValidUserBuilder().uuid(b).keycloakId("kc-b").build();
            when(userRepository.findAllByUuidIn(List.of(a, b))).thenReturn(List.of(ua, ub));

            service.deleteByUUIDs(List.of(a, b));

            // SQL delete runs first.
            InOrder order = inOrder(userRepository, eventPublisher);
            order.verify(userRepository).deleteAllByUuidIn(List.of(a, b));

            // One UserDeletedEvent per resolved user — captured to assert keycloakId fan-out.
            ArgumentCaptor<UserDeletedEvent> events = ArgumentCaptor.forClass(UserDeletedEvent.class);
            order.verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(events.capture());
            assertThat(events.getAllValues())
                    .extracting(UserDeletedEvent::getKeycloakId)
                    .containsExactly("kc-a", "kc-b");

            // The service no longer calls Keycloak directly — that is now the listener's job.
            verifyNoInteractions(keycloakUserManagementService);
        }

        @Test
        @DisplayName("FIX(SAGA-INCONSISTENCY) — empty list still calls SQL bulk delete and publishes no events")
        void deleteByUUIDs_emptyList() {
            when(userRepository.findAllByUuidIn(List.of())).thenReturn(List.of());

            service.deleteByUUIDs(List.of());

            verifyNoInteractions(keycloakUserManagementService);
            verifyNoInteractions(eventPublisher);
            verify(userRepository).deleteAllByUuidIn(List.of());
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("createAutomatically")
    class CreateAutomatically {

        @Test
        @DisplayName("saves each user via repository and returns mapped collection")
        void createAutomatically_happyPath() {
            UserEntity u1 = UserEntityBuilder.aValidUser();
            UserEntity u2 = UserEntityBuilder.aValidUser();
            when(userRepository.save(u1)).thenReturn(u1);
            when(userRepository.save(u2)).thenReturn(u2);
            List<UserResponseDto> expected = List.of(UserDtoFixtures.aSampleUserResponse(), UserDtoFixtures.aSampleUserResponse());
            when(userMapper.mapFromEntityToResponseDto(List.of(u1, u2))).thenReturn(expected);

            assertThat(service.createAutomatically(List.of(u1, u2))).containsExactlyElementsOf(expected);

            verify(userRepository).save(u1);
            verify(userRepository).save(u2);
        }

        @Test
        @DisplayName("empty input results in empty mapping")
        void createAutomatically_empty() {
            when(userMapper.mapFromEntityToResponseDto(List.<UserEntity>of())).thenReturn(List.of());

            assertThat(service.createAutomatically(List.of())).isEmpty();
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("updateMe — Frontend-gap #3 self-service profile update")
    class UpdateMe {

        private UserSelfUpdateRequestDto selfDto() {
            return UserSelfUpdateRequestDto.builder()
                    .firstName("Alice")
                    .lastName("Doe")
                    .phoneNumber("+33611111111")
                    .address("42 rue Selfservice")
                    .build();
        }

        @Test
        @DisplayName("happy path — Keycloak push first, then DB save via persistence service, returns mapped DTO")
        void updateMe_happyPath_keycloakThenDb() {
            final String keycloakId = "kc-self-1";
            final UserEntity user = UserEntityBuilder.aValidUserBuilder()
                    .uuid(UUID.randomUUID()).keycloakId(keycloakId).build();
            final UserResponseDto expected = UserDtoFixtures.aSampleUserResponse();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(userPersistenceService.updateUser(any(UserUpdateRequestDto.class), eq(user))).thenReturn(expected);

            final UserResponseDto result = service.updateMe(keycloakId, selfDto());

            assertThat(result).isSameAs(expected);
            // Option B: Keycloak MUST be called before the DB write (mirrors update() ordering).
            InOrder order = inOrder(keycloakUserManagementService, userPersistenceService);
            order.verify(keycloakUserManagementService).updateUser(eq(keycloakId), any(UserUpdateRequestDto.class));
            order.verify(userPersistenceService).updateUser(any(UserUpdateRequestDto.class), eq(user));
        }

        @Test
        @DisplayName("adapts UserSelfUpdateRequestDto into UserUpdateRequestDto carrying ONLY first/last name + address (admin fields stay untouched)")
        void updateMe_adaptsToUpdateRequestDto_minimalSurface() {
            final String keycloakId = "kc-self-2";
            final UUID userUuid = UUID.randomUUID();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().uuid(userUuid).keycloakId(keycloakId).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(userPersistenceService.updateUser(any(UserUpdateRequestDto.class), eq(user)))
                    .thenReturn(UserDtoFixtures.aSampleUserResponse());

            service.updateMe(keycloakId, selfDto());

            ArgumentCaptor<UserUpdateRequestDto> dtoCaptor = ArgumentCaptor.forClass(UserUpdateRequestDto.class);
            verify(userPersistenceService).updateUser(dtoCaptor.capture(), eq(user));
            final UserUpdateRequestDto adapted = dtoCaptor.getValue();
            assertThat(adapted.getUuid()).isEqualTo(userUuid);
            assertThat(adapted.getFirstName()).isEqualTo("Alice");
            assertThat(adapted.getLastName()).isEqualTo("Doe");
            assertThat(adapted.getAddress()).isEqualTo("42 rue Selfservice");
            // Privilege-elevation guard: email is admin-managed (would resync Keycloak login) so the
            // self-update path must never propagate it. The DTO surface itself has no role/status field,
            // so the only sensitive write the adapter could leak is email.
            assertThat(adapted.getEmail()).isNull();
            assertThat(adapted.getSex()).isNull();
            assertThat(adapted.getBirthDate()).isNull();
        }

        @Test
        @DisplayName("phone number patched directly on the entity (UserUpdateRequestDto has no phoneNumber field)")
        void updateMe_patchesPhoneNumberDirectlyOnEntity() {
            final String keycloakId = "kc-self-3";
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).phoneNumber("+33600000000").build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(userPersistenceService.updateUser(any(UserUpdateRequestDto.class), eq(user)))
                    .thenReturn(UserDtoFixtures.aSampleUserResponse());

            service.updateMe(keycloakId, selfDto());

            assertThat(user.getPhoneNumber()).isEqualTo("+33611111111");
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("null phone number short-circuits the side write (no extra repository.save)")
        void updateMe_nullPhoneNumberSkipsExtraSave() {
            final String keycloakId = "kc-self-no-phone";
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).phoneNumber("+33600000000").build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(userPersistenceService.updateUser(any(UserUpdateRequestDto.class), eq(user)))
                    .thenReturn(UserDtoFixtures.aSampleUserResponse());

            final UserSelfUpdateRequestDto noPhone = UserSelfUpdateRequestDto.builder()
                    .firstName("Alice").lastName("Doe").address("42 rue").build();
            service.updateMe(keycloakId, noPhone);

            verify(userRepository, never()).save(any(UserEntity.class));
            // Pin: the entity's phoneNumber stays whatever it was (impl must NOT overwrite with null).
            assertThat(user.getPhoneNumber()).isEqualTo("+33600000000");
        }

        @Test
        @DisplayName("missing user → UserNotFoundException; persistence service and Keycloak both untouched")
        void updateMe_missingUser_throws() {
            final String keycloakId = "kc-missing";
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateMe(keycloakId, selfDto()))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(keycloakId);

            verifyNoInteractions(userPersistenceService);
            verifyNoInteractions(keycloakUserManagementService);
        }

        @Test
        @DisplayName("Option B — Keycloak fails → DB never touched (no orphan DB mutation)")
        void updateMe_keycloakFails_dbNeverCalled() {
            final String keycloakId = "kc-self-kcfail";
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            doThrow(new RuntimeException("kc down"))
                    .when(keycloakUserManagementService).updateUser(eq(keycloakId), any(UserUpdateRequestDto.class));

            assertThatThrownBy(() -> service.updateMe(keycloakId, selfDto()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("kc down");

            verifyNoInteractions(userPersistenceService);
            verify(userRepository, never()).save(any(UserEntity.class));
        }

        @Test
        @DisplayName("Option B — Keycloak ok then DB fails → original DB exception propagates (rare: Keycloak now ahead)")
        void updateMe_dbFailsAfterKeycloakSuccess_propagates() {
            final String keycloakId = "kc-self-dbfail";
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(userPersistenceService.updateUser(any(UserUpdateRequestDto.class), eq(user)))
                    .thenThrow(new RuntimeException("db down"));

            assertThatThrownBy(() -> service.updateMe(keycloakId, selfDto()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("db down");

            // Keycloak was already updated (phase 1) before the DB write failed.
            verify(keycloakUserManagementService).updateUser(eq(keycloakId), any(UserUpdateRequestDto.class));
        }
    }
}
