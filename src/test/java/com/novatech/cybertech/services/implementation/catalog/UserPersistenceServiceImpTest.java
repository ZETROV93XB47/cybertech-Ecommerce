package com.novatech.cybertech.services.implementation.catalog;

import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.Role;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.dto.UserDtoFixtures;
import com.novatech.cybertech.mappers.entity.UserMapper;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.implementation.UserPersistenceServiceImp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link UserPersistenceServiceImp}.
 *
 * <p>Pins the H1 fix contract: this collaborator owns the SQL portion of user creation and runs
 * under {@code @Transactional(REQUIRES_NEW)}. Behaviours pinned here:
 * <ul>
 *   <li>maps {@link UserCreateRequestDto} into a {@link UserEntity} with {@link Role#USER}
 *       hardcoded, the supplied keycloakId, {@code isActive=true}, and zero hateful comments;</li>
 *   <li>creates no bank card: a card is saved afterwards through Stripe Elements;</li>
 *   <li>propagates persistence failures so the orchestrator can compensate.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserPersistenceServiceImpTest {

    @Mock UserRepository userRepository;
    @Mock UserMapper userMapper;

    @InjectMocks UserPersistenceServiceImp service;

    @Test
    @DisplayName("happy path — saves entity without any bank card, returns mapped DTO")
    void saveNewUser_happyPath_returnsMappedResponse() {
        UserCreateRequestDto req = UserDtoFixtures.aValidCreateRequest();
        String kcId = "kc-happy";
        UserEntity savedUser = UserEntityBuilder.aValidUser();
        UserResponseDto expected = UserDtoFixtures.aSampleUserResponse();
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
        when(userMapper.mapFromEntityToResponseDto(savedUser)).thenReturn(expected);

        UserResponseDto result = service.saveNewUser(req, kcId);

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        UserEntity built = userCaptor.getValue();
        assertThat(built.getEmail()).isEqualTo(req.getEmail());
        assertThat(built.getFirstName()).isEqualTo(req.getFirstName());
        assertThat(built.getLastName()).isEqualTo(req.getLastName());
        assertThat(built.getKeycloakId()).isEqualTo(kcId);
        assertThat(built.getRole()).isEqualTo(Role.USER);
        assertThat(built.getIsActive()).isTrue();
        assertThat(built.getNumberOfHatefulComments()).isZero();
        assertThat(built.getAddress()).isNotNull();
        assertThat(built.getAddress().getStreet()).isEqualTo(req.getStreet());
        assertThat(built.getAddress().getCity()).isEqualTo(req.getCity());
        assertThat(built.getAddress().getZipCode()).isEqualTo(req.getZipCode());
        assertThat(built.getAddress().getCountry()).isEqualTo(req.getCountry());
        // No card at sign-up: it is saved afterwards through Stripe Elements.
        assertThat(built.getBankCardEntity()).isNull();
    }

    @Test
    @DisplayName("repository.save failure propagates so the orchestrator can compensate Keycloak")
    void saveNewUser_repositoryFails_propagates() {
        UserCreateRequestDto req = UserDtoFixtures.aValidCreateRequest();
        when(userRepository.save(any(UserEntity.class)))
                .thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.saveNewUser(req, "kc-db-fail"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db down");
    }

    @Test
    @DisplayName("Bug 3 — updateUser patches entity via mapper, saves under REQUIRES_NEW, returns mapped DTO")
    void updateUserShouldSaveAndReturnDto() {
        // The orchestrator (UserManagementServiceImp.update) resolves the entity then hands it
        // off here. This collaborator owns the SQL portion exclusively — the keycloak call is
        // performed by the caller AFTER this returns.
        UserUpdateRequestDto dto = UserDtoFixtures.aValidUpdateRequest();
        UserEntity loadedUser = UserEntityBuilder.aValidUserBuilder().uuid(dto.getUuid()).keycloakId("kc-upd").build();
        UserEntity savedUser = UserEntityBuilder.aValidUserBuilder().uuid(dto.getUuid()).keycloakId("kc-upd").build();
        UserResponseDto expected = UserDtoFixtures.aSampleUserResponse();
        when(userRepository.save(loadedUser)).thenReturn(savedUser);
        when(userMapper.mapFromEntityToResponseDto(savedUser)).thenReturn(expected);

        UserResponseDto result = service.updateUser(dto, loadedUser);

        assertThat(result).isSameAs(expected);
        verify(userMapper).updateEntityFromDto(dto, loadedUser);
        verify(userRepository).save(loadedUser);
        verify(userMapper).mapFromEntityToResponseDto(savedUser);
    }
}
