package com.novatech.cybertech.services.implementation.catalog;

import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.Role;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.dto.UserDtoFixtures;
import com.novatech.cybertech.mappers.entity.UserMapper;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.BankCardManagementService;
import com.novatech.cybertech.services.implementation.KeycloakUserManagementService;
import com.novatech.cybertech.services.implementation.UserManagementServiceImp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * <p>SA-W3.5 wave — services/catalog. Pins:
 * <ul>
 *   <li>auto-admin guard: {@code create()} hardcodes {@link Role#USER} — request payload cannot
 *       elevate.</li>
 *   <li>compensating Keycloak delete when MySQL save fails after a successful Keycloak create.</li>
 *   <li>email-change push to Keycloak with no verify-new-email step (tech debt — pinned).</li>
 *   <li>BankCard save relies on outer {@code @Transactional} for MySQL rollback (no manual catch).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserManagementServiceImpTest {

    @Mock UserMapper userMapper;
    @Mock UserRepository userRepository;
    @Mock BankCardManagementService bankCardManagementService;
    @Mock KeycloakUserManagementService keycloakUserManagementService;

    @InjectMocks UserManagementServiceImp service;

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("happy path — Keycloak create, then SQL user, then delegates card add via BankCardManagementService, returns mapped DTO")
        void create_happyPath() {
            UserCreateRequestDto req = UserDtoFixtures.aValidCreateRequest();
            String kcId = "kc-123";
            when(keycloakUserManagementService.createUser(req.getEmail(), req.getFirstName(),
                    req.getLastName(), req.getPassword(), Role.USER)).thenReturn(kcId);
            UserEntity savedUser = UserEntityBuilder.aValidUser();
            when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
            UserResponseDto expected = UserDtoFixtures.aSampleUserResponse();
            when(userMapper.mapFromEntityToResponseDto(savedUser)).thenReturn(expected);

            UserResponseDto result = service.create(req);

            assertThat(result).isSameAs(expected);
            InOrder order = inOrder(keycloakUserManagementService, userRepository, bankCardManagementService);
            order.verify(keycloakUserManagementService).createUser(req.getEmail(), req.getFirstName(),
                    req.getLastName(), req.getPassword(), Role.USER);
            order.verify(userRepository).save(any(UserEntity.class));
            order.verify(bankCardManagementService).addBankCard(eq(kcId), any(BankCardCreationRequestDto.class));
        }

        @Test
        @DisplayName("auto-admin guard: Role.USER is hardcoded regardless of payload (cannot elevate)")
        void create_autoAdminGuard_hardcodesRoleUSER() {
            UserCreateRequestDto req = UserDtoFixtures.aValidCreateRequest();
            String kcId = "kc-x";
            when(keycloakUserManagementService.createUser(any(), any(), any(), any(), any())).thenReturn(kcId);
            UserEntity savedUser = UserEntityBuilder.aValidUser();
            when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
            when(userMapper.mapFromEntityToResponseDto(savedUser)).thenReturn(UserDtoFixtures.aSampleUserResponse());

            service.create(req);

            verify(keycloakUserManagementService).createUser(req.getEmail(), req.getFirstName(),
                    req.getLastName(), req.getPassword(), Role.USER);
            ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.USER);
        }

        @Test
        @DisplayName("MySQL user.save fails after Keycloak success → compensating Keycloak delete")
        void create_mysqlFailAfterKeycloak_compensatesKeycloakDelete() {
            UserCreateRequestDto req = UserDtoFixtures.aValidCreateRequest();
            String kcId = "kc-rollback";
            when(keycloakUserManagementService.createUser(any(), any(), any(), any(), any())).thenReturn(kcId);
            when(userRepository.save(any(UserEntity.class))).thenThrow(new RuntimeException("db down"));

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("db down");

            verify(keycloakUserManagementService).deleteUser(kcId);
            verify(bankCardManagementService, never()).addBankCard(any(), any());
        }

        @Test
        @DisplayName("Keycloak create itself fails → no compensating delete (no kcId yet)")
        void create_keycloakFails_noCompensation() {
            UserCreateRequestDto req = UserDtoFixtures.aValidCreateRequest();
            when(keycloakUserManagementService.createUser(any(), any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("kc 500"));

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("kc 500");

            verify(keycloakUserManagementService, never()).deleteUser(any());
            verifyNoInteractions(userRepository);
            verifyNoInteractions(bankCardManagementService);
        }

        @Test
        @DisplayName("BankCard add fails — Keycloak compensated, exception propagates")
        void create_bankCardFails_keycloakDeletedAndPropagates() {
            UserCreateRequestDto req = UserDtoFixtures.aValidCreateRequest();
            String kcId = "kc-bank-fail";
            when(keycloakUserManagementService.createUser(any(), any(), any(), any(), any())).thenReturn(kcId);
            UserEntity savedUser = UserEntityBuilder.aValidUser();
            when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
            doThrow(new RuntimeException("constraint violation"))
                    .when(bankCardManagementService).addBankCard(eq(kcId), any(BankCardCreationRequestDto.class));

            assertThatThrownBy(() -> service.create(req)).isInstanceOf(RuntimeException.class);

            verify(keycloakUserManagementService).deleteUser(kcId);
        }

        @Test
        @DisplayName("BankCard add receives the request DTO and the new keycloakId — PCI rules applied by addBankCard")
        void create_bankCardDelegatedWithKeycloakId() {
            UserCreateRequestDto req = UserDtoFixtures.aValidCreateRequest();
            String kcId = "kc-link-test";
            when(keycloakUserManagementService.createUser(any(), any(), any(), any(), any())).thenReturn(kcId);
            UserEntity savedUser = UserEntityBuilder.aValidUser();
            when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
            when(userMapper.mapFromEntityToResponseDto(savedUser)).thenReturn(UserDtoFixtures.aSampleUserResponse());

            service.create(req);

            ArgumentCaptor<String> kcCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<BankCardCreationRequestDto> dtoCaptor = ArgumentCaptor.forClass(BankCardCreationRequestDto.class);
            verify(bankCardManagementService).addBankCard(kcCaptor.capture(), dtoCaptor.capture());
            assertThat(kcCaptor.getValue()).isEqualTo(kcId);
            assertThat(dtoCaptor.getValue()).isSameAs(req.getBankCardCreationRequestDto());
        }

        @Test
        @DisplayName("Bank card is optional — null bankCardCreationRequestDto skips the addBankCard call entirely")
        void create_bankCardOptional_skipsCardWhenNull() {
            UserCreateRequestDto req = UserDtoFixtures.aValidCreateRequest();
            req.setBankCardCreationRequestDto(null);
            String kcId = "kc-no-card";
            when(keycloakUserManagementService.createUser(any(), any(), any(), any(), any())).thenReturn(kcId);
            UserEntity savedUser = UserEntityBuilder.aValidUser();
            when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
            when(userMapper.mapFromEntityToResponseDto(savedUser)).thenReturn(UserDtoFixtures.aSampleUserResponse());

            service.create(req);

            verify(bankCardManagementService, never()).addBankCard(any(), any());
            verify(keycloakUserManagementService, never()).deleteUser(any());
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
        @DisplayName("happy path — updates Keycloak then patches local entity then saves")
        void update_happyPath() {
            UserUpdateRequestDto dto = UserDtoFixtures.aValidUpdateRequest();
            UserEntity user = UserEntityBuilder.aValidUserBuilder().uuid(dto.getUuid()).keycloakId("kc-1").build();
            UserEntity saved = UserEntityBuilder.aValidUserBuilder().uuid(dto.getUuid()).build();
            UserResponseDto expected = UserDtoFixtures.aSampleUserResponse();

            when(userRepository.findByUuid(dto.getUuid())).thenReturn(Optional.of(user));
            when(userRepository.save(user)).thenReturn(saved);
            when(userMapper.mapFromEntityToResponseDto(saved)).thenReturn(expected);

            UserResponseDto result = service.update(dto);

            assertThat(result).isSameAs(expected);
            InOrder order = inOrder(keycloakUserManagementService, userMapper, userRepository);
            order.verify(keycloakUserManagementService).updateUser("kc-1", dto);
            order.verify(userMapper).updateEntityFromDto(dto, user);
            order.verify(userRepository).save(user);
        }

        @Test
        @DisplayName("missing user throws UserNotFoundException — Keycloak not touched")
        void update_notFound_throws() {
            UserUpdateRequestDto dto = UserDtoFixtures.aValidUpdateRequest();
            when(userRepository.findByUuid(dto.getUuid())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(dto))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(dto.getUuid().toString());

            verifyNoInteractions(keycloakUserManagementService);
        }

        @Test
        @DisplayName("email change pushed to Keycloak — but no verify-new-email step (tech-debt pin)")
        void update_emailChange_pushedToKeycloak_noVerifyEmail() {
            UserUpdateRequestDto dto = UserDtoFixtures.aValidUpdateRequest();
            dto.setEmail("changed@example.com");
            UserEntity user = UserEntityBuilder.aValidUserBuilder().uuid(dto.getUuid()).keycloakId("kc-9").build();

            when(userRepository.findByUuid(dto.getUuid())).thenReturn(Optional.of(user));
            when(userRepository.save(user)).thenReturn(user);
            when(userMapper.mapFromEntityToResponseDto(user)).thenReturn(UserDtoFixtures.aSampleUserResponse());

            service.update(dto);

            ArgumentCaptor<UserUpdateRequestDto> dtoCaptor = ArgumentCaptor.forClass(UserUpdateRequestDto.class);
            verify(keycloakUserManagementService).updateUser(org.mockito.ArgumentMatchers.eq("kc-9"), dtoCaptor.capture());
            assertThat(dtoCaptor.getValue().getEmail()).isEqualTo("changed@example.com");
            // No verify-new-email API exists — pin: KeycloakUserManagementService is the only outbound call.
        }

        @Test
        @DisplayName("Keycloak update failure aborts — local save not invoked")
        void update_keycloakFails_localSaveSkipped() {
            UserUpdateRequestDto dto = UserDtoFixtures.aValidUpdateRequest();
            UserEntity user = UserEntityBuilder.aValidUserBuilder().uuid(dto.getUuid()).keycloakId("kc-2").build();
            when(userRepository.findByUuid(dto.getUuid())).thenReturn(Optional.of(user));
            doThrow(new RuntimeException("kc fail")).when(keycloakUserManagementService).updateUser("kc-2", dto);

            assertThatThrownBy(() -> service.update(dto)).isInstanceOf(RuntimeException.class);

            verify(userRepository, never()).save(any());
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("deleteByUUID — happy path: Keycloak then SQL")
        void deleteByUUID_happyPath() {
            UUID id = UUID.randomUUID();
            UserEntity user = UserEntityBuilder.aValidUserBuilder().uuid(id).keycloakId("kc-d").build();
            when(userRepository.findByUuid(id)).thenReturn(Optional.of(user));

            service.deleteByUUID(id);

            InOrder order = inOrder(keycloakUserManagementService, userRepository);
            order.verify(keycloakUserManagementService).deleteUser("kc-d");
            order.verify(userRepository).deleteByUuid(id);
        }

        @Test
        @DisplayName("deleteByUUID — missing user throws UserNotFoundException, Keycloak not touched")
        void deleteByUUID_notFound_throws() {
            UUID id = UUID.randomUUID();
            when(userRepository.findByUuid(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteByUUID(id))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining(id.toString());

            verifyNoInteractions(keycloakUserManagementService);
        }

        @Test
        @DisplayName("deleteByUUIDs — fans out Keycloak deletes per user, then SQL bulk delete")
        void deleteByUUIDs_happyPath() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            UserEntity ua = UserEntityBuilder.aValidUserBuilder().uuid(a).keycloakId("kc-a").build();
            UserEntity ub = UserEntityBuilder.aValidUserBuilder().uuid(b).keycloakId("kc-b").build();
            when(userRepository.findAllByUuidIn(List.of(a, b))).thenReturn(List.of(ua, ub));

            service.deleteByUUIDs(List.of(a, b));

            verify(keycloakUserManagementService).deleteUser("kc-a");
            verify(keycloakUserManagementService).deleteUser("kc-b");
            verify(userRepository).deleteAllByUuidIn(List.of(a, b));
        }

        @Test
        @DisplayName("deleteByUUIDs — empty list still calls SQL bulk delete (no Keycloak calls)")
        void deleteByUUIDs_emptyList() {
            when(userRepository.findAllByUuidIn(List.of())).thenReturn(List.of());

            service.deleteByUUIDs(List.of());

            verifyNoInteractions(keycloakUserManagementService);
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
}
