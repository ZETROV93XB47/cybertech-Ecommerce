package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.StripeCardDetailsDto;
import com.novatech.cybertech.dto.data.StripeSetupIntentDto;
import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.dto.response.user.BankCardSetupIntentResponseDto;
import com.novatech.cybertech.entities.BankCardEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.BankCardType;
import com.novatech.cybertech.events.StripePaymentMethodReleasedEvent;
import com.novatech.cybertech.exceptions.BankCardExpiredException;
import com.novatech.cybertech.exceptions.BankCardNotFoundException;
import com.novatech.cybertech.exceptions.InvalidCardSetupException;
import com.novatech.cybertech.exceptions.PaymentProviderNotConfiguredException;
import com.novatech.cybertech.exceptions.UnauthorizedBankCardAccessException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.mappers.entity.BankCardMapper;
import com.novatech.cybertech.repositories.BankCardRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.BankCardManagementService;
import com.novatech.cybertech.services.core.StripeCardGateway;
import com.novatech.cybertech.utils.LogSafetyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Saved-card management on top of Stripe.
 *
 * <p>The browser enters the card in Stripe Elements and confirms a SetupIntent created here; the
 * backend then reads the resulting PaymentMethod from Stripe and keeps only its id, brand, last
 * four digits, expiry and holder name. No card number is ever received, logged or stored.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankCardManagementServiceImp implements BankCardManagementService {

    /** Canonical expiry format, as stored and as accepted by {@link BankCardUpdateRequestDto}. */
    private static final DateTimeFormatter EXPIRY_FORMATTER = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final String SETUP_INTENT_SUCCEEDED = "succeeded";
    private static final String NO_CARD_MESSAGE = "No bank card found for this user.";
    private static final String USER_NOT_FOUND_MESSAGE = "User not found";
    private static final String FOREIGN_SETUP_MESSAGE = "This card setup does not belong to the authenticated user";

    private final BankCardMapper bankCardMapper;
    private final UserRepository userRepository;
    private final BankCardRepository bankCardRepository;
    private final StripeCardGateway stripeCardGateway;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${stripe.publishable-key:}")
    private String publishableKey;

    // --- Méthodes Spécifiques (User Context) ---

    @Override
    @Transactional
    public BankCardSetupIntentResponseDto createSetupIntent(final String keycloakId) {
        if (publishableKey == null || publishableKey.isBlank()) {
            throw new PaymentProviderNotConfiguredException("stripe.publishable-key is not configured");
        }
        final UserEntity user = findUser(keycloakId);
        final StripeSetupIntentDto setupIntent = stripeCardGateway.createSetupIntent(ensureStripeCustomer(user));
        log.info("Stripe setup intent {} created for user {}", setupIntent.id(), user.getUuid());
        return BankCardSetupIntentResponseDto.builder()
                .setupIntentId(setupIntent.id())
                .clientSecret(setupIntent.clientSecret())
                .publishableKey(publishableKey)
                .build();
    }

    @Override
    @Transactional
    public BankCardResponseDto addBankCard(final String keycloakId, final BankCardCreationRequestDto dto) {
        final UserEntity user = findUser(keycloakId);
        final String customerId = user.getStripeCustomerId();
        if (customerId == null) {
            // No Customer yet means no SetupIntent was ever created for this user.
            throw new UnauthorizedBankCardAccessException(FOREIGN_SETUP_MESSAGE);
        }

        final StripeCardDetailsDto card = resolveSucceededSetupCard(dto.getSetupIntentId(), customerId);
        validateExpiryNotInThePast(YearMonth.of(card.expYear(), card.expMonth()));
        if (card.customerId() == null) {
            stripeCardGateway.attachPaymentMethod(card.paymentMethodId(), customerId);
        }
        stripeCardGateway.setDefaultPaymentMethod(customerId, card.paymentMethodId());

        final BankCardEntity bankCard = user.getBankCardEntity() == null
                ? BankCardEntity.builder().userEntity(user).build()
                : user.getBankCardEntity();
        final String previousPaymentMethodId = bankCard.getStripePaymentMethodId();
        applyStripeCard(bankCard, card, user);
        final BankCardEntity savedCard = bankCardRepository.save(bankCard);
        user.setBankCardEntity(savedCard);

        if (previousPaymentMethodId != null && !previousPaymentMethodId.equals(card.paymentMethodId())) {
            eventPublisher.publishEvent(new StripePaymentMethodReleasedEvent(previousPaymentMethodId));
        }
        log.info("Bank card {} (**** {}) saved for user {}", savedCard.getUuid(), savedCard.getLastFourDigits(), user.getUuid());
        return bankCardMapper.mapFromEntityToResponseDto(savedCard);
    }

    @Override
    @Transactional
    public void deleteBankCard(final String keycloakId) {
        final UserEntity user = findUser(keycloakId);
        final BankCardEntity bankCard = user.getBankCardEntity();
        if (bankCard == null) {
            throw new BankCardNotFoundException(NO_CARD_MESSAGE);
        }

        user.setBankCardEntity(null);
        bankCardRepository.delete(bankCard);
        releaseAtStripe(bankCard);
        log.info("Bank card {} (**** {}) deleted by its owner {}", bankCard.getUuid(), bankCard.getLastFourDigits(), user.getUuid());
    }

    /**
     * Updates the holder name and expiry date of the caller's card, at Stripe first (so the
     * PaymentMethod charged by the next payment carries them) then locally. The card number cannot
     * change: to use another card, save a new one.
     */
    @Override
    @Transactional
    public BankCardResponseDto updateBankCard(final String keycloakId, final BankCardUpdateRequestDto dto) {
        final UserEntity user = findUser(keycloakId);
        final BankCardEntity bankCard = user.getBankCardEntity();
        if (bankCard == null || !bankCard.isChargeable()) {
            throw new BankCardNotFoundException(NO_CARD_MESSAGE);
        }

        final YearMonth expiry = parseExpiry(dto.getExpiryDate());
        validateExpiryNotInThePast(expiry);
        stripeCardGateway.updatePaymentMethod(bankCard.getStripePaymentMethodId(), dto.getCardHolderName(),
                expiry.getMonthValue(), expiry.getYear());

        bankCard.setCardHolderName(dto.getCardHolderName());
        bankCard.setExpiryDate(expiry.format(EXPIRY_FORMATTER));
        final BankCardEntity savedCard = bankCardRepository.save(bankCard);
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
                    bankCardRepository.deleteByUuid(uuid);
                    releaseAtStripe(card);
                },
                () -> log.warn("ADMIN-AUDIT: admin '{}' attempted to delete non-existent bank card {}",
                        LogSafetyUtils.maskUuid(keycloakId), uuid));
    }

    /**
     * Returns the user's single chargeable bank card, already masked for safe API exposure. A card
     * saved before the Stripe flow cannot be charged and is reported as absent, so the frontend
     * offers to add one.
     *
     * @throws BankCardNotFoundException if the user has no chargeable card.
     */
    @Override
    @Transactional(readOnly = true)
    public BankCardResponseDto getCard(final String keycloakId) {
        final BankCardEntity card = bankCardRepository.findAllByUserEntity_KeycloakId(keycloakId).stream()
                .filter(BankCardEntity::isChargeable)
                .findFirst()
                .orElseThrow(() -> new BankCardNotFoundException("No bank card set for the user"));

        return bankCardMapper.mapFromEntityToResponseDto(card);
    }

    // --- helpers ----------------------------------------------------------------------

    private UserEntity findUser(final String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND_MESSAGE));
    }

    /** Returns the user's Stripe Customer, creating (and storing) it on first use. */
    private String ensureStripeCustomer(final UserEntity user) {
        if (user.getStripeCustomerId() != null) {
            return user.getStripeCustomerId();
        }
        final String customerId = stripeCardGateway.createCustomer(user.getUuid(), user.getEmail(), fullName(user));
        user.setStripeCustomerId(customerId);
        userRepository.save(user);
        log.info("Stripe customer {} created for user {}", customerId, user.getUuid());
        return customerId;
    }

    /**
     * Reads the card saved by the SetupIntent, after checking that the SetupIntent and its
     * PaymentMethod belong to the caller's Customer and that Stripe confirmed it.
     */
    private StripeCardDetailsDto resolveSucceededSetupCard(final String setupIntentId, final String customerId) {
        final StripeSetupIntentDto setupIntent = stripeCardGateway.retrieveSetupIntent(setupIntentId);
        if (!customerId.equals(setupIntent.customerId())) {
            log.warn("Card setup {} rejected: it belongs to another Stripe customer", setupIntentId);
            throw new UnauthorizedBankCardAccessException(FOREIGN_SETUP_MESSAGE);
        }
        if (!SETUP_INTENT_SUCCEEDED.equals(setupIntent.status()) || setupIntent.paymentMethodId() == null) {
            throw new InvalidCardSetupException("Card setup " + setupIntentId + " is not completed (status "
                    + setupIntent.status() + ")");
        }
        final StripeCardDetailsDto card = stripeCardGateway.retrievePaymentMethod(setupIntent.paymentMethodId());
        if (card.customerId() != null && !customerId.equals(card.customerId())) {
            throw new UnauthorizedBankCardAccessException(FOREIGN_SETUP_MESSAGE);
        }
        return card;
    }

    private static void applyStripeCard(final BankCardEntity bankCard, final StripeCardDetailsDto card, final UserEntity user) {
        bankCard.setStripePaymentMethodId(card.paymentMethodId());
        bankCard.setCardType(BankCardType.fromStripeBrand(card.brand()));
        bankCard.setLastFourDigits(card.lastFourDigits());
        bankCard.setExpiryDate(YearMonth.of(card.expYear(), card.expMonth()).format(EXPIRY_FORMATTER));
        // Stripe Elements' card field does not collect a name unless the frontend passes one.
        bankCard.setCardHolderName(card.holderName() == null || card.holderName().isBlank()
                ? fullName(user)
                : card.holderName());
    }

    private void releaseAtStripe(final BankCardEntity bankCard) {
        if (bankCard.isChargeable()) {
            eventPublisher.publishEvent(new StripePaymentMethodReleasedEvent(bankCard.getStripePaymentMethodId()));
        }
    }

    private static String fullName(final UserEntity user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }

    private static YearMonth parseExpiry(final String expiryDate) {
        // Defense in depth: @Valid on the request DTO already rejects blank/malformed values, but
        // YearMonth.parse(null, ...) would throw a raw NullPointerException.
        if (expiryDate == null || expiryDate.isBlank()) {
            throw new IllegalArgumentException("Invalid expiryDate format; expected MM/yyyy, got: " + expiryDate);
        }
        try {
            return YearMonth.parse(expiryDate, EXPIRY_FORMATTER);
        } catch (final DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid expiryDate format; expected MM/yyyy, got: " + expiryDate, e);
        }
    }

    private static void validateExpiryNotInThePast(final YearMonth expiry) {
        if (expiry.isBefore(YearMonth.now())) {
            throw new BankCardExpiredException("Card is expired (expiry=" + expiry.format(EXPIRY_FORMATTER) + ")");
        }
    }
}
