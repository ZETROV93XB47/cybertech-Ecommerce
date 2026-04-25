package com.novatech.cybertech.services.implementation.catalog;

import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.Role;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.dto.UserDtoFixtures;
import com.novatech.cybertech.mappers.entity.UserMapper;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.BankCardManagementService;
import com.novatech.cybertech.services.implementation.UserPersistenceService;
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
 * Mockito unit tests for {@link UserPersistenceService}.
 *
 * <p>Pins the H1 fix contract: this collaborator owns the SQL portion of user creation and runs
 * under {@code @Transactional(REQUIRES_NEW)}. Behaviours pinned here:
 * <ul>
 *   <li>maps {@link UserCreateRequestDto} into a {@link UserEntity} with {@link Role#USER}
 *       hardcoded, the supplied keycloakId, {@code isActive=true}, and zero hateful comments;</li>
 *   <li>delegates optional bank-card creation to
 *       {@link BankCardManagementService#addBankCard(String, BankCardCreationRequestDto)} so the
 *       PCI-DSS rules are applied;</li>
 *   <li>skips the bank-card delegation when the optional DTO is {@code null};</li>
 *   <li>propagates persistence/bank-card failures so the orchestrator can compensate.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserPersistenceServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserMapper userMapper;
    @Mock BankCardManagementService bankCardManagementService;

    @InjectMocks UserPersistenceService service;

    @Test
    @DisplayName("happy path — saves entity, delegates bank-card, returns mapped DTO")
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

        verify(bankCardManagementService)
                .addBankCard(eq(kcId), eq(req.getBankCardCreationRequestDto()));
    }

    @Test
    @DisplayName("null bankCardCreationRequestDto skips the addBankCard delegation entirely")
    void saveNewUser_noBankCard_skipsBankCardCall() {
        UserCreateRequestDto req = UserDtoFixtures.aValidCreateRequest();
        req.setBankCardCreationRequestDto(null);
        String kcId = "kc-no-card";
        UserEntity savedUser = UserEntityBuilder.aValidUser();
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
        when(userMapper.mapFromEntityToResponseDto(savedUser))
                .thenReturn(UserDtoFixtures.aSampleUserResponse());

        service.saveNewUser(req, kcId);

        verify(bankCardManagementService, never()).addBankCard(any(), any());
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

        verify(bankCardManagementService, never()).addBankCard(any(), any());
    }

    @Test
    @DisplayName("bank-card delegation failure propagates (rollback covers the user save in REQUIRES_NEW TX)")
    void saveNewUser_bankCardFails_propagates() {
        UserCreateRequestDto req = UserDtoFixtures.aValidCreateRequest();
        String kcId = "kc-bank-fail";
        UserEntity savedUser = UserEntityBuilder.aValidUser();
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
        org.mockito.Mockito.doThrow(new RuntimeException("constraint violation"))
                .when(bankCardManagementService)
                .addBankCard(eq(kcId), any(BankCardCreationRequestDto.class));

        assertThatThrownBy(() -> service.saveNewUser(req, kcId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("constraint violation");
    }
}
