package com.novatech.cybertech.services.implementation;


import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.entities.BankCardEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.exceptions.BankCardExpiredException;
import com.novatech.cybertech.exceptions.BankCardNotFoundException;
import com.novatech.cybertech.exceptions.NoDefaultBankCartSetException;
import com.novatech.cybertech.exceptions.UnauthorizedBankCardAccessException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.mappers.entity.BankCardMapper;
import com.novatech.cybertech.repositories.BankCardRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.BankCardManagementService;
import com.novatech.cybertech.services.core.CardEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankCardManagementServiceImp implements BankCardManagementService {

    /** BUG-037: canonical expiry format. Matches {@code BankCardCreationRequestDto} validation. */
    private static final DateTimeFormatter EXPIRY_FORMATTER = DateTimeFormatter.ofPattern("MM/yyyy");

    private final BankCardMapper bankCardMapper;
    private final UserRepository userRepository;
    private final BankCardRepository bankCardRepository;
    private final CardEncryptionService cardEncryptionService;

    // --- Méthodes Spécifiques (User Context) ---

    /**
     * BUG-036 + BUG-037: creates a card for the authenticated user.
     *
     * <p>Flow:
     * <ol>
     *   <li>Resolve the user by {@code keycloakId}.</li>
     *   <li>Reject if a card already exists (one-card-per-user business rule).</li>
     *   <li><b>BUG-037</b>: parse the expiry as {@code MM/yyyy}; reject past-dated cards
     *       with {@link BankCardExpiredException} <i>before</i> any persistence.</li>
     *   <li><b>BUG-036</b>: encrypt the PAN through {@link CardEncryptionService},
     *       compute {@code lastFourDigits}, and clear the legacy plaintext field so a
     *       newly-created row never holds the raw PAN.</li>
     * </ol>
     * </p>
     */
    @Override
    @Transactional
    public BankCardResponseDto addBankCard(String keycloakId, BankCardCreationRequestDto dto) {
        UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));

        if (user.getBankCardEntity() != null) {
            throw new IllegalStateException("User already has a bank card.");
        }

        // BUG-037: expiry validation happens before any mapper/repository touch.
        validateExpiryNotInThePast(dto.getExpiryDate());

        BankCardEntity bankCardEntity = bankCardMapper.mapFromCreationRequestToEntity(dto);
        bankCardEntity.setUserEntity(user);

        // BUG-036: encrypt at rest; keep only the last four digits for display.
        applyPciStorageRules(bankCardEntity, dto.getCardNumber());

        BankCardEntity savedCard = bankCardRepository.save(bankCardEntity);
        return bankCardMapper.mapFromEntityToResponseDto(savedCard);
    }

    @Override
    @Transactional
    public void deleteBankCard(String keycloakId) {
        UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));

        BankCardEntity bankCard = user.getBankCardEntity();
        if (bankCard == null) {
            throw new BankCardNotFoundException("No bank card found for this user.");
        }

        // Suppression via le repository pour déclencher les events JPA si besoin
        bankCardRepository.delete(bankCard);
    }

    @Override
    @Transactional
    public BankCardResponseDto updateBankCard(String keycloakId, BankCardUpdateRequestDto dto) {
        UserEntity user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        BankCardEntity bankCard = user.getBankCardEntity();
        if (bankCard == null) {
            throw new BankCardNotFoundException("No bank card found for this user.");
        }

        bankCardMapper.updateEntityFromDto(dto, bankCard);
        BankCardEntity savedCard = bankCardRepository.save(bankCard);
        return bankCardMapper.mapFromEntityToResponseDto(savedCard);
    }

    // --- Méthodes CRUD Base (Admin / Generic) ---

    @Override
    @Transactional(readOnly = true)
    public Collection<BankCardResponseDto> getAll() {
        return bankCardMapper.mapFromEntityToResponseDto(bankCardRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BankCardResponseDto> getAll(final Pageable pageable) {
        return bankCardRepository.findAll(pageable).map(bankCardMapper::mapFromEntityToResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public BankCardResponseDto getByUUID(UUID uuid) {
        BankCardEntity entity = bankCardRepository.findByUuid(uuid)
                .orElseThrow(() -> new BankCardNotFoundException("Bank card not found with UUID: " + uuid));
        return bankCardMapper.mapFromEntityToResponseDto(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<BankCardResponseDto> getByUUIDs(Collection<UUID> uuids) {
        return bankCardMapper.mapFromEntityToResponseDto(bankCardRepository.findAllByUuidIn(uuids));
    }

    @Override
    @Transactional
    public BankCardResponseDto create(BankCardCreationRequestDto dto) {
        // Pour le CRUD générique, on a besoin de lier un user.
        // On suppose que le DTO contient l'UUID du user (ajouté précédemment).
        if (dto.getUserUuid() == null) {
            throw new IllegalArgumentException("User UUID is required for generic bank card creation.");
        }

        UserEntity user = userRepository.findByUuid(dto.getUserUuid()).orElseThrow(() -> new UserNotFoundException("User not found with UUID: " + dto.getUserUuid()));

        if (user.getBankCardEntity() != null) {
            throw new IllegalStateException("User already has a bank card.");
        }

        BankCardEntity entity = bankCardMapper.mapFromCreationRequestToEntity(dto);
        entity.setUserEntity(user);

        return bankCardMapper.mapFromEntityToResponseDto(bankCardRepository.save(entity));
    }

    @Override
    @Transactional
    public BankCardResponseDto update(BankCardUpdateRequestDto dto) {
        BankCardEntity entity = bankCardRepository.findByUuid(dto.getUuid()).orElseThrow(() -> new BankCardNotFoundException("Bank card not found with UUID: " + dto.getUuid()));

        bankCardMapper.updateEntityFromDto(dto, entity);
        return bankCardMapper.mapFromEntityToResponseDto(bankCardRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteByUUID(UUID uuid) {
        bankCardRepository.deleteByUuid(uuid);
    }

    @Override
    @Transactional
    public void deleteByUUIDs(Collection<UUID> uuids) {
        bankCardRepository.deleteAllByUuidIn(uuids);
    }

    // --- BUG-038: default-card surface -------------------------------------------------

    /**
     * BUG-038: promotes a specific card to the user's default.
     *
     * <p>Steps:
     * <ol>
     *   <li>Load the target card or 404.</li>
     *   <li>Ownership check against the caller's {@code keycloakId}; mismatched owners
     *       are rejected with {@link UnauthorizedBankCardAccessException} (IDOR guard).</li>
     *   <li>Stream over every sibling card for that user, clearing their {@code isDefault}
     *       flag atomically within the transaction so only one card ends up as default.</li>
     *   <li>Flip the target to {@code isDefault=true} and persist.</li>
     * </ol>
     * </p>
     */
    @Override
    @Transactional
    public void setDefault(final UUID cardUuid, final String keycloakId) {
        final BankCardEntity target = bankCardRepository.findByUuid(cardUuid)
                .orElseThrow(() -> new BankCardNotFoundException("Bank card not found with UUID: " + cardUuid));

        if (target.getUserEntity() == null
                || target.getUserEntity().getKeycloakId() == null
                || !target.getUserEntity().getKeycloakId().equals(keycloakId)) {
            throw new UnauthorizedBankCardAccessException(
                    "Caller does not own the bank card: " + cardUuid);
        }

        final List<BankCardEntity> siblings = bankCardRepository.findAllByUserEntity_KeycloakId(keycloakId);
        siblings.stream()
                .filter(card -> !card.getUuid().equals(cardUuid))
                .filter(card -> Boolean.TRUE.equals(card.getIsDefault()))
                .forEach(card -> {
                    card.setIsDefault(false);
                    bankCardRepository.save(card);
                });

        target.setIsDefault(true);
        bankCardRepository.save(target);
    }

    /**
     * BUG-038: returns the user's default card, already masked for safe API exposure.
     *
     * @throws NoDefaultBankCartSetException if the user has not flagged any card as default.
     */
    @Override
    @Transactional(readOnly = true)
    public BankCardResponseDto getDefaultCard(final String keycloakId) {
        final BankCardEntity defaultCard = bankCardRepository
                .findByUserEntity_KeycloakIdAndIsDefaultTrue(keycloakId)
                .orElseThrow(() -> new NoDefaultBankCartSetException(
                        "No default bank card set for the user"));
        return bankCardMapper.mapFromEntityToResponseDto(defaultCard);
    }

    // --- helpers ----------------------------------------------------------------------

    /**
     * BUG-037: enforces that the expiry string is a valid {@code MM/yyyy} month that has
     * not already passed. Called before any mapping/save on the add-path so we do not waste
     * a round-trip to the database for obviously-invalid input.
     */
    private void validateExpiryNotInThePast(final String expiryDate) {
        final YearMonth expiry;
        try {
            expiry = YearMonth.parse(expiryDate, EXPIRY_FORMATTER);
        } catch (final DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid expiryDate format; expected MM/yyyy, got: " + expiryDate, e);
        }
        if (expiry.isBefore(YearMonth.now())) {
            throw new BankCardExpiredException("Card is expired (expiry=" + expiryDate + ")");
        }
    }

    /**
     * BUG-036: applies PCI-DSS storage rules to a freshly-mapped entity.
     *
     * <ul>
     *   <li>{@code encryptedNumber} gets the AES/GCM ciphertext envelope — the only place
     *       the PAN is persisted going forward.</li>
     *   <li>{@code lastFourDigits} caches the last 4 digits for display masking.</li>
     *   <li>The legacy {@code cardNumber} column is blanked so new rows never hold a
     *       plaintext PAN, even though the column survives for historical rows.</li>
     * </ul>
     */
    private void applyPciStorageRules(final BankCardEntity entity, final String rawPan) {
        if (rawPan == null || rawPan.length() < 4) {
            throw new IllegalArgumentException("BUG-036: PAN must be at least 4 digits long");
        }
        entity.setEncryptedNumber(cardEncryptionService.encrypt(rawPan));
        entity.setLastFourDigits(rawPan.substring(rawPan.length() - 4));
        entity.setCardNumber(null);
    }
}
