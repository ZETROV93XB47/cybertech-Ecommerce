package com.novatech.cybertech.services.implementation.shopping;

import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.entities.BankCardEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.BankCardType;
import com.novatech.cybertech.exceptions.BankCardExpiredException;
import com.novatech.cybertech.exceptions.BankCardNotFoundException;
import com.novatech.cybertech.exceptions.NoDefaultBankCartSetException;
import com.novatech.cybertech.exceptions.UnauthorizedBankCardAccessException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link BankCardManagementServiceImp}.
 *
 * <p><b>SA-BankCard-v2:</b> BUG-036 (PAN encryption + masking), BUG-037 (expiry guard), and
 * BUG-038 (default-card surface) are now closed. The previously {@code @Disabled} pinning
 * tests are re-enabled and now verify the <i>fixed</i> contract.</p>
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
         * BUG-037 (re-enabled by SA-BankCard-v2): expiry in the past now throws
         * {@link BankCardExpiredException} <i>before</i> any repository or mapper call.
         */
        @Test
        @DisplayName("BUG-037: expired card should throw BankCardExpiredException before save")
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
        @DisplayName("BUG-037: malformed expiry -> IllegalArgumentException before save")
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
    @DisplayName("BUG-036 — card masking / encryption")
    class CardMaskingBug036 {

        /**
         * BUG-036 (re-enabled): the saved entity carries an encrypted envelope plus the last four
         * digits — and the legacy {@code cardNumber} column is NOT populated with the raw PAN.
         */
        @Test
        @DisplayName("saved entity is encrypted + masked (last4 + encryptedNumber), legacy cardNumber blanked")
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
            // No full PAN in the legacy column.
            assertThat(persisted.getCardNumber()).isNull();
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
    @DisplayName("BUG-038 — default-card support")
    class DefaultCardBug038 {

        @Test
        @DisplayName("BankCardManagementServiceImp exposes setDefault + getDefaultCard methods")
        void defaultCardMethodsExist() {
            final List<String> methodNames = Arrays.stream(BankCardManagementServiceImp.class.getDeclaredMethods())
                    .map(Method::getName)
                    .toList();
            assertThat(methodNames).contains("setDefault", "getDefaultCard");
        }

        @Test
        @DisplayName("BankCardEntity has an `isDefault` field (BUG-038)")
        void hasIsDefaultField() {
            boolean hasIsDefault = Arrays.stream(BankCardEntity.class.getDeclaredFields())
                    .anyMatch(f -> f.getName().equalsIgnoreCase("isDefault"));
            assertThat(hasIsDefault).isTrue();
        }

        @Test
        @DisplayName("setDefault: ownership enforced — mismatched keycloakId -> UnauthorizedBankCardAccessException")
        void setDefault_enforcesOwnership() {
            final UUID cardUuid = UUID.randomUUID();
            final UserEntity owner = UserEntityBuilder.aValidUserBuilder().keycloakId("other-kc").build();
            final BankCardEntity target = BankCardEntityBuilder.aValidBankCardBuilder().uuid(cardUuid).userEntity(owner).build();
            when(bankCardRepository.findByUuid(cardUuid)).thenReturn(Optional.of(target));

            assertThatThrownBy(() -> service.setDefault(cardUuid, keycloakId))
                    .isInstanceOf(UnauthorizedBankCardAccessException.class);
            verify(bankCardRepository, never()).save(any());
        }

        @Test
        @DisplayName("setDefault: flips isDefault on target, clears on siblings")
        void setDefault_flipsFlag() {
            final UUID targetUuid = UUID.randomUUID();
            final UUID siblingUuid = UUID.randomUUID();
            final UserEntity owner = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final BankCardEntity target = BankCardEntityBuilder.aValidBankCardBuilder()
                    .uuid(targetUuid).userEntity(owner).isDefault(Boolean.FALSE).build();
            final BankCardEntity sibling = BankCardEntityBuilder.aValidBankCardBuilder()
                    .uuid(siblingUuid).userEntity(owner).isDefault(Boolean.TRUE).build();
            when(bankCardRepository.findByUuid(targetUuid)).thenReturn(Optional.of(target));
            when(bankCardRepository.findAllByUserEntity_KeycloakId(keycloakId)).thenReturn(List.of(target, sibling));

            service.setDefault(targetUuid, keycloakId);

            assertThat(target.getIsDefault()).isTrue();
            assertThat(sibling.getIsDefault()).isFalse();
            verify(bankCardRepository).save(sibling);
            verify(bankCardRepository).save(target);
        }

        @Test
        @DisplayName("setDefault: unknown card -> BankCardNotFoundException")
        void setDefault_unknownCard_throws() {
            final UUID cardUuid = UUID.randomUUID();
            when(bankCardRepository.findByUuid(cardUuid)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.setDefault(cardUuid, keycloakId))
                    .isInstanceOf(BankCardNotFoundException.class);
        }

        @Test
        @DisplayName("getDefaultCard: returns masked DTO of the default card")
        void getDefaultCard_happy() {
            final BankCardEntity defaultCard = BankCardEntityBuilder.aValidBankCardBuilder().isDefault(Boolean.TRUE).build();
            final BankCardResponseDto dto = new BankCardResponseDto();
            when(bankCardRepository.findByUserEntity_KeycloakIdAndIsDefaultTrue(keycloakId)).thenReturn(Optional.of(defaultCard));
            when(bankCardMapper.mapFromEntityToResponseDto(defaultCard)).thenReturn(dto);

            assertThat(service.getDefaultCard(keycloakId)).isSameAs(dto);
        }

        @Test
        @DisplayName("getDefaultCard: no default -> NoDefaultBankCartSetException")
        void getDefaultCard_noneSet_throws() {
            when(bankCardRepository.findByUserEntity_KeycloakIdAndIsDefaultTrue(keycloakId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getDefaultCard(keycloakId))
                    .isInstanceOf(NoDefaultBankCartSetException.class);
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
            // BUG-036 fix: admin create() now calls applyPciStorageRules -> encrypt must be stubbed
            when(cardEncryptionService.encrypt(anyString())).thenReturn("ENC:4242");
            when(bankCardRepository.save(mapped)).thenReturn(saved);
            when(bankCardMapper.mapFromEntityToResponseDto(saved)).thenReturn(resp);

            assertThat(service.create(dto)).isSameAs(resp);
            assertThat(mapped.getUserEntity()).isSameAs(user);
        }

        @Test
        @DisplayName("FIX BUG-036 remaining: admin create() path encrypts PAN via applyPciStorageRules")
        void create_adminPath_shouldEncryptPan() {
            // Arrange
            final UUID userUuid = UUID.randomUUID();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder()
                    .uuid(userUuid)
                    .keycloakId("kc-admin")
                    .bankCardEntity(null)
                    .build();

            final BankCardCreationRequestDto dto = BankCardCreationRequestDto.builder()
                    .userUuid(userUuid)
                    .cardNumber("4111111111111111")
                    .expiryDate("12/2099")
                    .cardHolderName("Admin User")
                    .cardType(BankCardType.VISA)
                    .build();

            final BankCardEntity entity = BankCardEntity.builder()
                    .cardNumber("4111111111111111")
                    .expiryDate("12/2099")
                    .build();

            when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
            when(bankCardMapper.mapFromCreationRequestToEntity(dto)).thenReturn(entity);
            when(cardEncryptionService.encrypt("4111111111111111")).thenReturn("ENCRYPTED_PAN");
            when(bankCardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(bankCardMapper.mapFromEntityToResponseDto(any(BankCardEntity.class))).thenReturn(new BankCardResponseDto());

            // Act
            service.create(dto);

            // Assert: encryption service was called, last 4 digits stored, cardNumber cleared
            verify(cardEncryptionService).encrypt("4111111111111111");
            assertThat(entity.getEncryptedNumber()).isEqualTo("ENCRYPTED_PAN");
            assertThat(entity.getLastFourDigits()).isEqualTo("1111");
            assertThat(entity.getCardNumber()).isNull();
        }

        @Test
        @DisplayName("FIX BUG-036 remaining: admin create() path rejects expired cards")
        void create_adminPath_expiredCard_shouldThrowBankCardExpiredException() {
            final UUID userUuid = UUID.randomUUID();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder()
                    .uuid(userUuid)
                    .bankCardEntity(null)
                    .build();

            final BankCardCreationRequestDto dto = BankCardCreationRequestDto.builder()
                    .userUuid(userUuid)
                    .cardNumber("4111111111111111")
                    .expiryDate("01/2000")   // expired
                    .cardHolderName("User")
                    .cardType(BankCardType.VISA)
                    .build();

            final BankCardEntity entity = BankCardEntity.builder()
                    .cardNumber("4111111111111111")
                    .build();

            when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
            when(bankCardMapper.mapFromCreationRequestToEntity(dto)).thenReturn(entity);

            assertThatThrownBy(() -> service.create(dto))
                    .isInstanceOf(BankCardExpiredException.class);

            verify(bankCardRepository, never()).save(any());
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
        @DisplayName("BUG-161 fixed: deleteByUUID(uuid, keycloakId) deletes when caller owns the card")
        void deleteByUuidWithKeycloakId_ownerMatches_deletes() {
            final UUID cardUuid = UUID.randomUUID();
            final UserEntity owner = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final BankCardEntity card = BankCardEntityBuilder.aValidBankCardBuilder()
                    .uuid(cardUuid)
                    .userEntity(owner)
                    .build();
            when(bankCardRepository.findByUuid(cardUuid)).thenReturn(Optional.of(card));

            service.deleteByUUID(cardUuid, keycloakId);

            verify(bankCardRepository).deleteByUuid(cardUuid);
        }

        @Test
        @DisplayName("BUG-161 fixed: deleteByUUID(uuid, keycloakId) rejects with UnauthorizedBankCardAccessException when caller is not owner")
        void deleteByUuidWithKeycloakId_callerIsNotOwner_throwsUnauthorized() {
            final UUID cardUuid = UUID.randomUUID();
            final UserEntity owner = UserEntityBuilder.aValidUserBuilder().keycloakId("kc-owner").build();
            final BankCardEntity card = BankCardEntityBuilder.aValidBankCardBuilder()
                    .uuid(cardUuid)
                    .userEntity(owner)
                    .build();
            when(bankCardRepository.findByUuid(cardUuid)).thenReturn(Optional.of(card));

            assertThatThrownBy(() -> service.deleteByUUID(cardUuid, "kc-not-owner"))
                    .isInstanceOf(UnauthorizedBankCardAccessException.class);

            verify(bankCardRepository, never()).deleteByUuid(any(UUID.class));
        }

        @Test
        @DisplayName("BUG-161 fixed: deleteByUUID(uuid, keycloakId) raises BankCardNotFoundException when the card is missing")
        void deleteByUuidWithKeycloakId_missingCard_throwsNotFound() {
            final UUID cardUuid = UUID.randomUUID();
            when(bankCardRepository.findByUuid(cardUuid)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteByUUID(cardUuid, keycloakId))
                    .isInstanceOf(BankCardNotFoundException.class);

            verify(bankCardRepository, never()).deleteByUuid(any(UUID.class));
        }
    }

    // =================================================================
    @Nested
    @DisplayName("findAllMine — Frontend-gap #4 list every card belonging to the caller")
    class FindAllMine {

        @Test
        @DisplayName("happy path: user has 1 card -> repo result mapped to single-element list")
        void findAllMine_oneCard_returnsMappedSingleton() {
            final BankCardEntity card = BankCardEntityBuilder.aValidBankCard();
            final BankCardResponseDto responseDto = new BankCardResponseDto();
            when(bankCardRepository.findAllByUserEntity_KeycloakId(keycloakId)).thenReturn(List.of(card));
            when(bankCardMapper.mapFromEntityToResponseDto(card)).thenReturn(responseDto);

            final List<BankCardResponseDto> result = service.findAllMine(keycloakId);

            assertThat(result).hasSize(1).first().isSameAs(responseDto);
            verify(bankCardRepository).findAllByUserEntity_KeycloakId(keycloakId);
        }

        @Test
        @DisplayName("user with no card -> empty list, mapper never called")
        void findAllMine_noCard_returnsEmpty() {
            when(bankCardRepository.findAllByUserEntity_KeycloakId(keycloakId)).thenReturn(List.of());

            final List<BankCardResponseDto> result = service.findAllMine(keycloakId);

            assertThat(result).isEmpty();
            verify(bankCardMapper, never()).mapFromEntityToResponseDto(any(BankCardEntity.class));
        }

        @Test
        @DisplayName("forwards keycloakId to the repository verbatim — no normalisation, no transformation")
        void findAllMine_forwardsKeycloakIdVerbatim() {
            when(bankCardRepository.findAllByUserEntity_KeycloakId(keycloakId)).thenReturn(List.of());

            service.findAllMine(keycloakId);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(bankCardRepository).findAllByUserEntity_KeycloakId(captor.capture());
            assertThat(captor.getValue()).isEqualTo(keycloakId);
        }
    }
}
