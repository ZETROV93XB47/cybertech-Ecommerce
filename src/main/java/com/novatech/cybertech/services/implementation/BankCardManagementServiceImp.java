package com.novatech.cybertech.services.implementation;


import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.entities.BankCardEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.exceptions.BankCardAlreadyExistsException;
import com.novatech.cybertech.exceptions.BankCardExpiredException;
import com.novatech.cybertech.exceptions.BankCardNotFoundException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.mappers.entity.BankCardMapper;
import com.novatech.cybertech.repositories.BankCardRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.BankCardManagementService;
import com.novatech.cybertech.services.core.CardEncryptionService;
import com.novatech.cybertech.utils.LogSafetyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankCardManagementServiceImp implements BankCardManagementService {

    /** Canonical expiry format. Matches {@code BankCardCreationRequestDto} validation. */
    private static final DateTimeFormatter EXPIRY_FORMATTER = DateTimeFormatter.ofPattern("MM/yyyy");

    private final BankCardMapper bankCardMapper;
    private final UserRepository userRepository;
    private final BankCardRepository bankCardRepository;
    private final CardEncryptionService cardEncryptionService;

    // --- Méthodes Spécifiques (User Context) ---

    /**
     * Creates a card for the authenticated user.
     *
     * <p>Flow:
     * <ol>
     *   <li>Resolve the user by {@code keycloakId}.</li>
     *   <li>Reject with {@link BankCardAlreadyExistsException} (409) if a card already exists
     *       (one-card-per-user business rule); replacing goes through {@link #replaceBankCard}.</li>
     *   <li>Parse the expiry as {@code MM/yyyy}; reject past-dated cards
     *       with {@link BankCardExpiredException} <i>before</i> any persistence.</li>
     *   <li>Encrypt the PAN through {@link CardEncryptionService},
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
            log.warn("Add bank card rejected: user {} already has card {}", user.getUuid(), user.getBankCardEntity().getUuid());
            throw new BankCardAlreadyExistsException("User already has a bank card.");
        }

        final BankCardEntity savedCard = saveNewCard(user, buildCard(user, dto));
        log.info("Bank card {} (**** {}) added for user {}", savedCard.getUuid(), savedCard.getLastFourDigits(), user.getUuid());
        return bankCardMapper.mapFromEntityToResponseDto(savedCard);
    }

    /**
     * Swaps the caller's card for a new one in a single transaction: the new card is validated
     * (expiry, PAN) BEFORE the old one is touched, and a failure anywhere rolls the removal back,
     * so the account is never left without a card. Without a current card it simply adds one.
     */
    @Override
    @Transactional
    public BankCardResponseDto replaceBankCard(final String keycloakId, final BankCardCreationRequestDto dto) {
        final UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));

        final BankCardEntity newCard = buildCard(user, dto);

        final BankCardEntity oldCard = user.getBankCardEntity();
        if (oldCard != null) {
            detachAndDelete(user, oldCard);
            // Hibernate flushes INSERTs before DELETEs: without this the new row would clash with
            // the old one on the unique user_id column.
            bankCardRepository.flush();
        }

        final BankCardEntity savedCard = saveNewCard(user, newCard);
        log.info("Bank card of user {} replaced: {} -> {} (**** {})", user.getUuid(),
                oldCard == null ? null : oldCard.getUuid(), savedCard.getUuid(), savedCard.getLastFourDigits());
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

        detachAndDelete(user, bankCard);
        log.info("Bank card {} (**** {}) deleted by its owner {}", bankCard.getUuid(), bankCard.getLastFourDigits(), user.getUuid());
    }

    /**
     * Updates the editable fields of the caller's bank card: holder name and expiry date only.
     * The PAN is WRITE-ONCE — {@link BankCardUpdateRequestDto} no longer carries {@code cardNumber},
     * so the encrypted-at-rest ciphertext can never be mutated through this path. To
     * change the card number, delete the card and add a new one.
     */
    @Override
    @Transactional
    public BankCardResponseDto updateBankCard(final String keycloakId, final BankCardUpdateRequestDto dto) {
        UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));

        BankCardEntity bankCard = user.getBankCardEntity();
        if (bankCard == null) {
            throw new BankCardNotFoundException("No bank card found for this user.");
        }

        // Expiry guard — a card may only be updated to a non-past expiry.
        if (dto.getExpiryDate() != null && !dto.getExpiryDate().isBlank()) {
            validateExpiryNotInThePast(dto.getExpiryDate());
        }

        bankCardMapper.updateEntityFromDto(dto, bankCard);

        BankCardEntity savedCard = bankCardRepository.save(bankCard);
        log.info("Bank card {} updated by its owner (holder name / expiry)", savedCard.getUuid());
        return bankCardMapper.mapFromEntityToResponseDto(savedCard);
    }

    // --- Admin back-office (list / read / delete) ---

    @Override
    @Transactional(readOnly = true)
    public Page<BankCardResponseDto> getAll(final Pageable pageable) {
        return bankCardRepository.findAll(pageable).map(bankCardMapper::mapFromEntityToResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public BankCardResponseDto getByUUID(final UUID uuid) {
        BankCardEntity entity = bankCardRepository.findByUuid(uuid)
                .orElseThrow(() -> new BankCardNotFoundException("Bank card not found with UUID: " + uuid));
        return bankCardMapper.mapFromEntityToResponseDto(entity);
    }

    /**
     * Admin delete path: an admin has full authority over any user's data by design. We do NOT
     * gate this on ownership, but we DO emit a PCI audit trail recording which admin removed which
     * user's card, so the destructive write on PCI data has a traceable actor — {@code keycloakId}
     * is now the acting admin's identity, passed in rather than re-read from the security context.
     */
    @Override
    @Transactional
    public void deleteByUUID(final UUID uuid, final String keycloakId) {
        bankCardRepository.findByUuid(uuid).ifPresentOrElse(
                card -> {
                    final String ownerKeycloakId = card.getUserEntity() == null ? null : card.getUserEntity().getKeycloakId();
                    log.info("ADMIN-AUDIT: admin '{}' deleting bank card {} owned by user '{}'",
                            LogSafetyUtils.maskUuid(keycloakId),
                            uuid,
                            LogSafetyUtils.maskUuid(ownerKeycloakId));
                    if (card.getUserEntity() != null) {
                        detachAndDelete(card.getUserEntity(), card);
                    } else {
                        bankCardRepository.delete(card);
                    }
                },
                () -> log.warn("ADMIN-AUDIT: admin '{}' attempted to delete non-existent bank card {}",
                        LogSafetyUtils.maskUuid(keycloakId), uuid));
    }

        /**
     * Returns the user's single bank card, already masked for safe API exposure.
     *
     * @throws BankCardNotFoundException if the user has no bank card.
     */
    @Override
    @Transactional(readOnly = true)
    public BankCardResponseDto getCard(final String keycloakId) {
        final BankCardEntity card = bankCardRepository.findAllByUserEntity_KeycloakId(keycloakId).stream()
                .findFirst()
                .orElseThrow(() -> new BankCardNotFoundException("No bank card set for the user"));

        return bankCardMapper.mapFromEntityToResponseDto(card);
    }

    // --- helpers ----------------------------------------------------------------------

    /**
     * Validates the request and builds the encrypted, not-yet-saved card. Expiry and PAN checks
     * run here, before any persistence and before an existing card is removed.
     */
    private BankCardEntity buildCard(final UserEntity user, final BankCardCreationRequestDto dto) {
        validateExpiryNotInThePast(dto.getExpiryDate());

        final BankCardEntity bankCardEntity = bankCardMapper.mapFromCreationRequestToEntity(dto);
        bankCardEntity.setUserEntity(user);

        // Encrypt at rest; keep only the last four digits for display.
        applyPciStorageRules(bankCardEntity, dto.getCardNumber());
        return bankCardEntity;
    }

    /** Saves the card and links it back on the user, so the managed user never points at a stale card. */
    private BankCardEntity saveNewCard(final UserEntity user, final BankCardEntity card) {
        final BankCardEntity savedCard = bankCardRepository.save(card);
        user.setBankCardEntity(savedCard);
        return savedCard;
    }

    /**
     * Unlinks the card from its user before deleting it. {@code UserEntity.bankCardEntity} cascades
     * ALL: left in place, the reference re-persists the card at flush and the delete is silently
     * undone (the endpoint answered 204 while the card stayed).
     */
    private void detachAndDelete(final UserEntity user, final BankCardEntity card) {
        user.setBankCardEntity(null);
        bankCardRepository.delete(card);
    }

    /**
     * Enforces that the expiry string is a valid {@code MM/yyyy} month that has
     * not already passed. Called before any mapping/save on the add-path so we do not waste
     * a round-trip to the database for obviously-invalid input.
     */
    private void validateExpiryNotInThePast(final String expiryDate) {
        // Defense in depth: callers are expected to validate non-blank upstream (@Valid on the
        // request DTOs), but this method has no local guarantee of that — YearMonth.parse(null, ...)
        // throws a raw NullPointerException (not DateTimeParseException), which the catch below
        // would not have caught.
        if (expiryDate == null || expiryDate.isBlank()) {
            throw new IllegalArgumentException("Invalid expiryDate format; expected MM/yyyy, got: " + expiryDate);
        }
        final YearMonth expiry;
        try {
            expiry = YearMonth.parse(expiryDate, EXPIRY_FORMATTER);
        } catch (final DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid expiryDate format; expected MM/yyyy, got: " + expiryDate, e);
        }
        if (expiry.isBefore(YearMonth.now())) {
            throw new BankCardExpiredException("Card is expired (expiry=" + expiryDate + ")");
        }
    }

    /**
     * Applies PCI-DSS storage rules to a freshly-mapped entity.
     *
     * <ul>
     *   <li>{@code encryptedNumber} gets the AES/GCM ciphertext envelope — the only place
     *       the PAN is persisted.</li>
     *   <li>{@code lastFourDigits} caches the last 4 digits for display masking.</li>
     * </ul>
     */
    private void applyPciStorageRules(final BankCardEntity entity, final String rawPan) {
        if (rawPan == null || rawPan.length() < 4) {
            throw new IllegalArgumentException("PAN must be at least 4 digits long");
        }
        entity.setEncryptedNumber(cardEncryptionService.encrypt(rawPan));
        entity.setLastFourDigits(rawPan.substring(rawPan.length() - 4));
    }
}
