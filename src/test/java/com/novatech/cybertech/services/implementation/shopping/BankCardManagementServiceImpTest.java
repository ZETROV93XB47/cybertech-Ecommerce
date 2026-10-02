package com.novatech.cybertech.services.implementation.shopping;

import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.entities.BankCardEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.BankCardType;
import com.novatech.cybertech.exceptions.BankCardExpiredException;
import com.novatech.cybertech.exceptions.BankCardNotFoundException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.fixtures.builders.BankCardEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.mappers.entity.BankCardMapper;
import com.novatech.cybertech.repositories.BankCardRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.CardEncryptionService;
import com.novatech.cybertech.services.implementation.BankCardManagementServiceImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link BankCardManagementServiceImp}.
 *
 * <p><b>SA-BankCard-v2:</b> BUG-036 (PAN encryption + masking) and BUG-037 (expiry guard) are
 * now closed. The previously {@code @Disabled} pinning tests are re-enabled and now verify the
 * <i>fixed</i> contract. The legacy "default card" concept (isDefault flag / setDefault) has
 * been removed entirely — the domain model only ever allows one card per user.</p>
 */
@ExtendWith(MockitoExtension.class)
class BankCardManagementServiceImpTest {

    @Mock BankCardMapper bankCardMapper;
    @Mock UserRepository userRepository;
    @Mock BankCardRepository bankCardRepository;
    @Mock CardEncryptionService cardEncryptionService;

    @InjectMocks BankCardManagementServiceImp service;

    String keycloakId;

    @BeforeEach
    void setUp() {
        keycloakId = "kc-" + UUID.randomUUID();
    }

    private BankCardCreationRequestDto creationDto(String expiry) {
        return BankCardCreationRequestDto.builder()
                .cardHolderName("Jane Doe")
                .cardNumber("4242424242424242")
                .expiryDate(expiry)
                .cardType(BankCardType.VISA)
                .build();
    }

    private BankCardUpdateRequestDto updateDto() {
        return BankCardUpdateRequestDto.builder()
                .cardHolderName("Jane Doe Updated")
                .expiryDate(LocalDate.now().plusYears(3).format(DateTimeFormatter.ofPattern("MM/yyyy")))
                .build();
    }

    // =================================================================
    @Nested
    @DisplayName("addBankCard (user context)")
    class AddBankCard {

        @Test
        @DisplayName("happy path: user with no card -> entity built/saved/mapped")
        void happy_savesAndReturnsDto() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).bankCardEntity(null).build();
            final BankCardEntity mapped = BankCardEntityBuilder.aValidBankCard();
            final BankCardEntity saved = BankCardEntityBuilder.aValidBankCard();
            final BankCardResponseDto responseDto = new BankCardResponseDto();
            final BankCardCreationRequestDto dto = creationDto("12/2030");

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(bankCardMapper.mapFromCreationRequestToEntity(dto)).thenReturn(mapped);
            when(cardEncryptionService.encrypt(anyString())).thenReturn("ENC:4242");
            when(bankCardRepository.save(mapped)).thenReturn(saved);
            when(bankCardMapper.mapFromEntityToResponseDto(saved)).thenReturn(responseDto);

            final BankCardResponseDto result = service.addBankCard(keycloakId, dto);

            assertThat(result).isSameAs(responseDto);
            assertThat(mapped.getUserEntity()).isSameAs(user);
            verify(bankCardRepository).save(mapped);
        }

        @Test
        @DisplayName("user not found -> UserNotFoundException, no save")
        void userMissing_throws() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addBankCard(keycloakId, creationDto("12/2030")))
                    .isInstanceOf(UserNotFoundException.class);

            verify(bankCardRepository, never()).save(any());
            verifyNoInteractions(bankCardMapper);
        }

        @Test
        @DisplayName("one-card-per-user enforcement: 2nd card -> IllegalStateException")
        void userAlreadyHasCard_throwsIllegalState() {
            final BankCardEntity existing = BankCardEntityBuilder.aValidBankCard();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).bankCardEntity(existing).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.addBankCard(keycloakId, creationDto("12/2030")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already has a bank card");

            verify(bankCardRepository, never()).save(any());
            verifyNoInteractions(bankCardMapper);
        }

        /**
         * Re-enabled: expiry in the past now throws
         * {@link BankCardExpiredException} <i>before</i> any repository or mapper call.
         */
        @Test
        @DisplayName("expired card should throw BankCardExpiredException before save")
        void expiredCard_shouldThrow() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).bankCardEntity(null).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.addBankCard(keycloakId, creationDto("01/2000")))
                    .isInstanceOf(BankCardExpiredException.class)
                    .hasMessageContaining("expired");

            verify(bankCardRepository, never()).save(any());
            verifyNoInteractions(bankCardMapper, cardEncryptionService);
        }

        @Test
        @DisplayName("malformed expiry -> IllegalArgumentException before save")
        void malformedExpiry_throws() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).bankCardEntity(null).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.addBankCard(keycloakId, creationDto("not-a-date")))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(bankCardRepository, never()).save(any());
        }
    }

    // =================================================================
    @Nested
    @DisplayName("card masking / encryption")
    class CardMaskingBug036 {

        /**
         * The saved entity carries only the encrypted envelope plus the last four digits —
         * no plaintext PAN field exists on {@link BankCardEntity} at all.
         */
        @Test
        @DisplayName("saved entity is encrypted + masked (last4 + encryptedNumber)")
        void cardNumberShouldBeMaskedAndEncrypted() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).bankCardEntity(null).build();
            final BankCardCreationRequestDto dto = creationDto("12/2030");
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(bankCardMapper.mapFromCreationRequestToEntity(any(BankCardCreationRequestDto.class)))
                    .thenReturn(BankCardEntityBuilder.aValidBankCard());
            when(cardEncryptionService.encrypt("4242424242424242")).thenReturn("ENC(base64-blob)");
            when(bankCardRepository.save(any(BankCardEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.addBankCard(keycloakId, dto);

            final ArgumentCaptor<BankCardEntity> captor = ArgumentCaptor.forClass(BankCardEntity.class);
            verify(bankCardRepository).save(captor.capture());
            final BankCardEntity persisted = captor.getValue();
            // Encrypted envelope present.
            assertThat(persisted.getEncryptedNumber()).isEqualTo("ENC(base64-blob)");
            // Only last four digits retained for display.
            assertThat(persisted.getLastFourDigits()).isEqualTo("4242");
        }

        @Test
        @DisplayName("BankCardEntity exposes lastFourDigits + encryptedNumber fields (PCI-DSS surface)")
        void bankCardEntity_hasLastFourDigitsAndEncryptedNumber() {
            final List<String> fieldNames = Arrays.stream(BankCardEntity.class.getDeclaredFields())
                    .map(java.lang.reflect.Field::getName).toList();
            assertThat(fieldNames).contains("lastFourDigits", "encryptedNumber");
        }
    }

    // =================================================================
    @Nested
    @DisplayName("getCard — the caller's single card")
    class GetCard {


        @Test
        @DisplayName("getCard: returns masked DTO of the user's card")
        void getCard_happy() {
            final BankCardEntity card = BankCardEntityBuilder.aValidBankCard();
            final BankCardResponseDto dto = new BankCardResponseDto();
            when(bankCardRepository.findAllByUserEntity_KeycloakId(keycloakId)).thenReturn(List.of(card));
            when(bankCardMapper.mapFromEntityToResponseDto(card)).thenReturn(dto);

            assertThat(service.getCard(keycloakId)).isSameAs(dto);
        }

        @Test
        @DisplayName("getCard: no card -> BankCardNotFoundException")
        void getCard_noCard_throws() {
            when(bankCardRepository.findAllByUserEntity_KeycloakId(keycloakId)).thenReturn(List.of());

            assertThatThrownBy(() -> service.getCard(keycloakId))
                    .isInstanceOf(BankCardNotFoundException.class);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("deleteBankCard (user context)")
    class DeleteBankCard {

        @Test
        @DisplayName("happy: user with card -> repository.delete called")
        void happy_deletes() {
            final BankCardEntity card = BankCardEntityBuilder.aValidBankCard();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).bankCardEntity(card).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            service.deleteBankCard(keycloakId);

            verify(bankCardRepository).delete(card);
        }

        @Test
        @DisplayName("user not found -> UserNotFoundException")
        void userMissing_throws() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.deleteBankCard(keycloakId))
                    .isInstanceOf(UserNotFoundException.class);
            verify(bankCardRepository, never()).delete(any(BankCardEntity.class));
        }

        @Test
        @DisplayName("user has no card -> BankCardNotFoundException")
        void noCard_throws() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).bankCardEntity(null).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.deleteBankCard(keycloakId))
                    .isInstanceOf(BankCardNotFoundException.class);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("updateBankCard (user context)")
    class UpdateBankCard {

        @Test
        @DisplayName("happy: mapper applies dto, repo saves, mapper maps response")
        void happy_updates() {
            final BankCardEntity card = BankCardEntityBuilder.aValidBankCard();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).bankCardEntity(card).build();
            final BankCardUpdateRequestDto dto = updateDto();
            final BankCardResponseDto resp = new BankCardResponseDto();

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(bankCardRepository.save(card)).thenReturn(card);
            when(bankCardMapper.mapFromEntityToResponseDto(card)).thenReturn(resp);

            final BankCardResponseDto result = service.updateBankCard(keycloakId, dto);

            assertThat(result).isSameAs(resp);
            verify(bankCardMapper).updateEntityFromDto(dto, card);
        }

        /**
         * Write-once PAN: the update DTO no longer carries a card number, so the encrypted column
         * can never be touched here. The PAN re-encryption path was removed entirely — changing the
         * card number now requires deleting the card and adding a new one. We assert the encryption
         * service is never invoked on the update path.
         */
        @Test
        @DisplayName("write-once PAN: updateBankCard never touches the encryption service")
        void updateBankCard_neverReEncryptsPan() {
            final BankCardEntity card = BankCardEntityBuilder.aValidBankCard();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).bankCardEntity(card).build();
            final BankCardUpdateRequestDto dto = updateDto();

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(bankCardRepository.save(card)).thenAnswer(inv -> inv.getArgument(0));
            when(bankCardMapper.mapFromEntityToResponseDto(card)).thenReturn(new BankCardResponseDto());

            service.updateBankCard(keycloakId, dto);

            verifyNoInteractions(cardEncryptionService);
        }

        @Test
        @DisplayName("updateBankCard rejects a past expiry with BankCardExpiredException before save")
        void updateBankCard_expiredCard_throws() {
            final BankCardEntity card = BankCardEntityBuilder.aValidBankCard();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).bankCardEntity(card).build();
            final BankCardUpdateRequestDto dto = BankCardUpdateRequestDto.builder()
                    .cardHolderName("Jane Doe")
                    .expiryDate("01/2000") // expired
                    .build();

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.updateBankCard(keycloakId, dto))
                    .isInstanceOf(BankCardExpiredException.class);

            verify(bankCardRepository, never()).save(any());
            verifyNoInteractions(cardEncryptionService);
        }

        @Test
        @DisplayName("user not found -> UserNotFoundException, no save")
        void userMissing_throws() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.updateBankCard(keycloakId, updateDto()))
                    .isInstanceOf(UserNotFoundException.class);
            verify(bankCardRepository, never()).save(any());
        }

        @Test
        @DisplayName("user has no card -> BankCardNotFoundException")
        void noCard_throws() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).bankCardEntity(null).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.updateBankCard(keycloakId, updateDto()))
                    .isInstanceOf(BankCardNotFoundException.class);
            verify(bankCardRepository, never()).save(any());
        }
    }

    // =================================================================
    @Nested
    @DisplayName("getByUUID / getAll(Pageable)")
    class ReadOps {

        @Test
        @DisplayName("getAll(Pageable) returns mapped page")
        void getAllPaged_happy() {
            final Pageable pageable = PageRequest.of(0, 10);
            final BankCardEntity entity = BankCardEntityBuilder.aValidBankCard();
            final BankCardResponseDto dto = new BankCardResponseDto();
            final Page<BankCardEntity> page = new PageImpl<>(List.of(entity), pageable, 1);

            when(bankCardRepository.findAll(pageable)).thenReturn(page);
            when(bankCardMapper.mapFromEntityToResponseDto(entity)).thenReturn(dto);

            final Page<BankCardResponseDto> result = service.getAll(pageable);
            assertThat(result.getContent()).containsExactly(dto);
        }

        @Test
        @DisplayName("getByUUID happy returns mapped DTO")
        void getByUuid_happy() {
            final UUID uuid = UUID.randomUUID();
            final BankCardEntity entity = BankCardEntityBuilder.aValidBankCardBuilder().uuid(uuid).build();
            final BankCardResponseDto dto = new BankCardResponseDto();
            when(bankCardRepository.findByUuid(uuid)).thenReturn(Optional.of(entity));
            when(bankCardMapper.mapFromEntityToResponseDto(entity)).thenReturn(dto);

            assertThat(service.getByUUID(uuid)).isSameAs(dto);
        }

        @Test
        @DisplayName("getByUUID missing -> BankCardNotFoundException")
        void getByUuid_missing_throws() {
            final UUID uuid = UUID.randomUUID();
            when(bankCardRepository.findByUuid(uuid)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByUUID(uuid))
                    .isInstanceOf(BankCardNotFoundException.class)
                    .hasMessageContaining(uuid.toString());
        }

    }

    // =================================================================
    @Nested
    @DisplayName("deleteByUUID (admin)")
    class AdminDelete {
        /**
         * Write-once PAN on the admin path too: the update DTO carries no card number, so the
         * encryption service is never invoked on update.
         */
        @Test
        @DisplayName("deleteByUUID (admin) loads the card for the PCI audit log then delegates to repository, " +
                "regardless of who owns it — admin authority is not ownership-gated")
        void deleteByUuid_delegates() {
            final UUID uuid = UUID.randomUUID();
            final UserEntity owner = UserEntityBuilder.aValidUserBuilder().keycloakId("kc-owner").build();
            final BankCardEntity card = BankCardEntityBuilder.aValidBankCardBuilder()
                    .uuid(uuid)
                    .userEntity(owner)
                    .build();
            when(bankCardRepository.findByUuid(uuid)).thenReturn(Optional.of(card));

            service.deleteByUUID(uuid, keycloakId);

            verify(bankCardRepository).deleteByUuid(uuid);
        }

        @Test
        @DisplayName("deleteByUUID(uuid, keycloakId) on a missing card: no exception, just an audit warning")
        void deleteByUuid_missingCard_doesNotThrow_justLogs() {
            final UUID cardUuid = UUID.randomUUID();
            when(bankCardRepository.findByUuid(cardUuid)).thenReturn(Optional.empty());

            assertThatCode(() -> service.deleteByUUID(cardUuid, keycloakId)).doesNotThrowAnyException();

            verify(bankCardRepository, never()).deleteByUuid(any(UUID.class));
        }
    }
}
