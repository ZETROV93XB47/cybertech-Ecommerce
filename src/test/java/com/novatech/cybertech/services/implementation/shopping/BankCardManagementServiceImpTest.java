package com.novatech.cybertech.services.implementation.shopping;

import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.entities.BankCardEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.BankCardType;
import com.novatech.cybertech.exceptions.BankCardNotFoundException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.fixtures.builders.BankCardEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.mappers.entity.BankCardMapper;
import com.novatech.cybertech.repositories.BankCardRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.implementation.BankCardManagementServiceImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link BankCardManagementServiceImp}.
 *
 * <p><b>SA-W3.4 — F1 claim verification:</b> the F1 wave claimed BUG-036 (card masking) and
 * BUG-037 (expired-card validation) were closed via a new {@code CardEncryptionService}.
 * Source verification on this branch shows {@code CardEncryptionService} does NOT exist
 * (search returned no files), {@code BankCardEntity} stores raw {@code cardNumber} as a 25-char
 * String with no {@code lastFourDigits} column, and {@code addBankCard} performs no expiry check.
 * Therefore BUG-036 and BUG-037 are <b>OPEN</b>; pinned via passing tests below.</p>
 *
 * <p>BUG-038 (set/get/isDefault for default bank card) — methods do not exist on the service or
 * entity (verified). Pinned with a reflection-based passing assertion documenting absence.</p>
 *
 * <p>BUG-431 (new): the user-context {@code addBankCard} short-circuits if {@code user.bankCardEntity}
 * is non-null, but the generic {@code create(dto)} duplicates the same check using
 * {@code IllegalStateException}. Both paths reject a 2nd card — pinned both ways.</p>
 */
@ExtendWith(MockitoExtension.class)
class BankCardManagementServiceImpTest {

    @Mock BankCardMapper bankCardMapper;
    @Mock UserRepository userRepository;
    @Mock BankCardRepository bankCardRepository;

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

    private BankCardUpdateRequestDto updateDto(UUID uuid) {
        return BankCardUpdateRequestDto.builder()
                .uuid(uuid)
                .cardHolderName("Jane Doe Updated")
                .cardNumber("4242424242424242")
                .expiryDate(LocalDate.now().plusYears(3).format(DateTimeFormatter.ofPattern("MM/yyyy")))
                .cardType(BankCardType.VISA)
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

        @Test
        @Disabled("BUG-037: addBankCard accepts past-dated expiry. F1 claim of fix REFUTED — " +
                "no CardEncryptionService.java in src/main; no expiry validation in service. Re-enable when service rejects expired cards with ExpiredCardException.")
        @DisplayName("BUG-037: expired card should throw before save")
        void expiredCard_shouldThrow_disabled() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).bankCardEntity(null).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            // Expected behavior: throw on past expiry. Currently the service happily saves it.
            assertThatThrownBy(() -> service.addBankCard(keycloakId, creationDto("01/2000")))
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("BUG-037 PIN: expired card is currently accepted and saved (no validation)")
        void expiredCard_currentlyAccepted_PIN() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).bankCardEntity(null).build();
            final BankCardEntity mapped = BankCardEntityBuilder.aValidBankCardBuilder().expiryDate("01/2000").build();
            final BankCardEntity saved = BankCardEntityBuilder.aValidBankCardBuilder().expiryDate("01/2000").build();
            final BankCardCreationRequestDto dto = creationDto("01/2000");

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(bankCardMapper.mapFromCreationRequestToEntity(dto)).thenReturn(mapped);
            when(bankCardRepository.save(mapped)).thenReturn(saved);
            when(bankCardMapper.mapFromEntityToResponseDto(saved)).thenReturn(new BankCardResponseDto());

            // PIN: no exception thrown, save is called
            assertThat(service.addBankCard(keycloakId, dto)).isNotNull();
            verify(bankCardRepository).save(mapped);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("BUG-036 — card masking / encryption")
    class CardMaskingBug036 {

        @Test
        @Disabled("BUG-036: card numbers stored in plaintext. F1 claim of fix via CardEncryptionService " +
                "REFUTED — no such class exists in src/main. BankCardEntity has no lastFourDigits column. " +
                "Re-enable once entity adds maskedNumber/lastFourDigits and storage is encrypted.")
        @DisplayName("saved entity should mask cardNumber to last4 + encrypt full PAN at rest")
        void cardNumberShouldBeMaskedAndEncrypted_disabled() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).bankCardEntity(null).build();
            final BankCardCreationRequestDto dto = creationDto("12/2030");
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(bankCardMapper.mapFromCreationRequestToEntity(any(BankCardCreationRequestDto.class))).thenReturn(BankCardEntityBuilder.aValidBankCard());
            when(bankCardRepository.save(any(BankCardEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.addBankCard(keycloakId, dto);

            final ArgumentCaptor<BankCardEntity> captor = ArgumentCaptor.forClass(BankCardEntity.class);
            verify(bankCardRepository).save(captor.capture());
            final BankCardEntity persisted = captor.getValue();
            // Expectation when fixed: full PAN must NOT be stored as-is
            assertThat(persisted.getCardNumber()).doesNotContain("4242424242424242");
        }

        @Test
        @DisplayName("BUG-036 PIN: full card number is currently stored as-is (no masking, no encryption)")
        void cardNumberStoredPlaintext_PIN() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).bankCardEntity(null).build();
            final BankCardCreationRequestDto dto = creationDto("12/2030");
            // Mapper returns entity with the full PAN copied across
            final BankCardEntity mapped = BankCardEntityBuilder.aValidBankCardBuilder().cardNumber("4242424242424242").build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(bankCardMapper.mapFromCreationRequestToEntity(dto)).thenReturn(mapped);
            when(bankCardRepository.save(mapped)).thenAnswer(inv -> inv.getArgument(0));
            when(bankCardMapper.mapFromEntityToResponseDto(any(BankCardEntity.class))).thenReturn(new BankCardResponseDto());

            service.addBankCard(keycloakId, dto);

            final ArgumentCaptor<BankCardEntity> captor = ArgumentCaptor.forClass(BankCardEntity.class);
            verify(bankCardRepository).save(captor.capture());
            // PIN current insecure behavior
            assertThat(captor.getValue().getCardNumber()).isEqualTo("4242424242424242");
        }

        @Test
        @DisplayName("BUG-036 PIN: BankCardEntity has no `lastFourDigits` getter (no masked-display column)")
        void bankCardEntity_lacksLastFourDigitsField_PIN() {
            // Reflection: confirm absence so we know when prod adds the field (test will fail and prompt update).
            boolean hasLastFour = Arrays.stream(BankCardEntity.class.getDeclaredFields())
                    .anyMatch(f -> f.getName().equalsIgnoreCase("lastFourDigits") || f.getName().equalsIgnoreCase("maskedNumber"));
            assertThat(hasLastFour)
                    .as("BUG-036: lastFourDigits/maskedNumber column missing — masking not implemented")
                    .isFalse();
        }
    }

    // =================================================================
    @Nested
    @DisplayName("BUG-038 — default-card support")
    class DefaultCardBug038 {

        @Test
        @DisplayName("BUG-038 PIN: BankCardManagementServiceImp exposes no setDefault/getDefault/isDefault method")
        void noDefaultCardMethods_PIN() {
            final List<String> methodNames = Arrays.stream(BankCardManagementServiceImp.class.getDeclaredMethods())
                    .map(Method::getName)
                    .toList();
            assertThat(methodNames)
                    .as("BUG-038: setDefault/getDefault/isDefault not implemented")
                    .doesNotContain("setDefault", "getDefault", "isDefault", "setAsDefault", "getDefaultBankCard");
        }

        @Test
        @DisplayName("BUG-038 PIN: BankCardEntity has no `isDefault` field")
        void noIsDefaultField_PIN() {
            boolean hasIsDefault = Arrays.stream(BankCardEntity.class.getDeclaredFields())
                    .anyMatch(f -> f.getName().equalsIgnoreCase("isDefault") || f.getName().equalsIgnoreCase("defaultCard"));
            assertThat(hasIsDefault)
                    .as("BUG-038: isDefault column missing on BankCardEntity")
                    .isFalse();
        }

        @Test
        @Disabled("BUG-038: enable once setDefault/getDefault/isDefault are implemented on BankCardManagementService.")
        @DisplayName("BUG-038: setDefault should mark a card as default and clear others")
        void setDefault_shouldFlipFlag_disabled() {
            // Placeholder for future contract
            assertThat(true).isTrue();
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
            final BankCardUpdateRequestDto dto = updateDto(card.getUuid());
            final BankCardResponseDto resp = new BankCardResponseDto();

            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));
            when(bankCardRepository.save(card)).thenReturn(card);
            when(bankCardMapper.mapFromEntityToResponseDto(card)).thenReturn(resp);

            final BankCardResponseDto result = service.updateBankCard(keycloakId, dto);

            assertThat(result).isSameAs(resp);
            verify(bankCardMapper).updateEntityFromDto(dto, card);
        }

        @Test
        @DisplayName("user not found -> UserNotFoundException, no save")
        void userMissing_throws() {
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.updateBankCard(keycloakId, updateDto(UUID.randomUUID())))
                    .isInstanceOf(UserNotFoundException.class);
            verify(bankCardRepository, never()).save(any());
        }

        @Test
        @DisplayName("user has no card -> BankCardNotFoundException")
        void noCard_throws() {
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).bankCardEntity(null).build();
            when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.updateBankCard(keycloakId, updateDto(UUID.randomUUID())))
                    .isInstanceOf(BankCardNotFoundException.class);
            verify(bankCardRepository, never()).save(any());
        }
    }

    // =================================================================
    @Nested
    @DisplayName("getAll / getByUUID / getByUUIDs / getAll(Pageable)")
    class ReadOps {

        @Test
        @DisplayName("getAll() returns mapped collection")
        void getAll_happy() {
            final List<BankCardEntity> entities = List.of(BankCardEntityBuilder.aValidBankCard());
            final List<BankCardResponseDto> dtos = List.of(new BankCardResponseDto());
            when(bankCardRepository.findAll()).thenReturn(entities);
            when(bankCardMapper.mapFromEntityToResponseDto(entities)).thenReturn(dtos);

            assertThat(service.getAll()).isEqualTo(dtos);
        }

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

        @Test
        @DisplayName("getByUUIDs delegates to repository.findAllByUuidIn and maps")
        void getByUuids_happy() {
            final List<UUID> uuids = List.of(UUID.randomUUID(), UUID.randomUUID());
            final List<BankCardEntity> entities = List.of(BankCardEntityBuilder.aValidBankCard());
            final List<BankCardResponseDto> dtos = List.of(new BankCardResponseDto());
            when(bankCardRepository.findAllByUuidIn(uuids)).thenReturn(entities);
            when(bankCardMapper.mapFromEntityToResponseDto(entities)).thenReturn(dtos);

            Collection<BankCardResponseDto> result = service.getByUUIDs(uuids);
            assertThat(result).isEqualTo(dtos);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("create / update / deleteByUUID / deleteByUUIDs (admin)")
    class AdminCrud {

        @Test
        @DisplayName("create happy: dto.userUuid present, user has no card -> save")
        void create_happy() {
            final UUID userUuid = UUID.randomUUID();
            final BankCardCreationRequestDto dto = creationDto("12/2030");
            dto.setUserUuid(userUuid);
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().uuid(userUuid).bankCardEntity(null).build();
            final BankCardEntity mapped = BankCardEntityBuilder.aValidBankCard();
            final BankCardEntity saved = BankCardEntityBuilder.aValidBankCard();
            final BankCardResponseDto resp = new BankCardResponseDto();

            when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
            when(bankCardMapper.mapFromCreationRequestToEntity(dto)).thenReturn(mapped);
            when(bankCardRepository.save(mapped)).thenReturn(saved);
            when(bankCardMapper.mapFromEntityToResponseDto(saved)).thenReturn(resp);

            assertThat(service.create(dto)).isSameAs(resp);
            assertThat(mapped.getUserEntity()).isSameAs(user);
        }

        @Test
        @DisplayName("create with null userUuid -> IllegalArgumentException")
        void create_nullUserUuid_throws() {
            final BankCardCreationRequestDto dto = creationDto("12/2030");
            dto.setUserUuid(null);

            assertThatThrownBy(() -> service.create(dto))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(userRepository, bankCardRepository, bankCardMapper);
        }

        @Test
        @DisplayName("create when user already has a card -> IllegalStateException")
        void create_userAlreadyHasCard_throws() {
            final UUID userUuid = UUID.randomUUID();
            final BankCardCreationRequestDto dto = creationDto("12/2030");
            dto.setUserUuid(userUuid);
            final BankCardEntity existing = BankCardEntityBuilder.aValidBankCard();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().uuid(userUuid).bankCardEntity(existing).build();

            when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.create(dto))
                    .isInstanceOf(IllegalStateException.class);
            verify(bankCardRepository, never()).save(any());
        }

        @Test
        @DisplayName("update(dto) happy: resolves entity by UUID, applies, saves, maps")
        void update_happy() {
            final UUID uuid = UUID.randomUUID();
            final BankCardUpdateRequestDto dto = updateDto(uuid);
            final BankCardEntity entity = BankCardEntityBuilder.aValidBankCardBuilder().uuid(uuid).build();
            final BankCardResponseDto resp = new BankCardResponseDto();

            when(bankCardRepository.findByUuid(uuid)).thenReturn(Optional.of(entity));
            when(bankCardRepository.save(entity)).thenReturn(entity);
            when(bankCardMapper.mapFromEntityToResponseDto(entity)).thenReturn(resp);

            assertThat(service.update(dto)).isSameAs(resp);
            verify(bankCardMapper).updateEntityFromDto(dto, entity);
        }

        @Test
        @DisplayName("update missing uuid -> BankCardNotFoundException")
        void update_missing_throws() {
            final BankCardUpdateRequestDto dto = updateDto(UUID.randomUUID());
            when(bankCardRepository.findByUuid(dto.getUuid())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(dto))
                    .isInstanceOf(BankCardNotFoundException.class);
        }

        @Test
        @DisplayName("deleteByUUID delegates to repository")
        void deleteByUuid_delegates() {
            final UUID uuid = UUID.randomUUID();
            service.deleteByUUID(uuid);
            verify(bankCardRepository).deleteByUuid(uuid);
        }

        @Test
        @DisplayName("deleteByUUIDs delegates to repository")
        void deleteByUuids_delegates() {
            final List<UUID> uuids = List.of(UUID.randomUUID(), UUID.randomUUID());
            service.deleteByUUIDs(uuids);
            verify(bankCardRepository).deleteAllByUuidIn(uuids);
        }

        @Test
        @Disabled("BUG-161 (cart/admin IDOR): no ownership check on deleteByUUID — same shape as the open " +
                "BUG-161 on cart deleteByUUID. Pinned across F1 wave too. Re-enable when service verifies " +
                "the bank card belongs to the caller before deletion.")
        @DisplayName("BUG-161-shape: deleteByUUID should reject when card belongs to a different user")
        void deleteByUuid_shouldEnforceOwnership_disabled() {
            // placeholder
        }
    }
}
