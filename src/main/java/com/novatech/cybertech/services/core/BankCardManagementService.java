package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.dto.response.user.BankCardSetupIntentResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Bank card domain service.
 * <p>
 * Deliberately NOT a {@link CrudBaseService}: a user owns at most one card, managed through the
 * caller-scoped methods ({@link #createSetupIntent}, {@link #addBankCard}, {@link #updateBankCard},
 * {@link #deleteBankCard}). The admin back-office only lists, reads and deletes cards.
 * <p>
 * Cards live at Stripe: the browser enters the card in Stripe Elements, the backend only stores
 * the PaymentMethod id, brand, last four digits, expiry and holder name.
 */
public interface BankCardManagementService {

    /**
     * Prepares a card registration: creates the caller's Stripe Customer on first use, then an
     * off-session card SetupIntent that Stripe Elements confirms in the browser.
     *
     * @throws com.novatech.cybertech.exceptions.PaymentProviderNotConfiguredException when
     *         {@code stripe.publishable-key} is not set.
     */
    BankCardSetupIntentResponseDto createSetupIntent(String keycloakId);

    /**
     * Saves the card of a {@code succeeded} SetupIntent of the caller's Customer as the user's only
     * card (it replaces a previous card, which is detached at Stripe after commit) and makes it
     * the Customer's default payment method.
     *
     * @throws com.novatech.cybertech.exceptions.UnauthorizedBankCardAccessException when the
     *         SetupIntent or its PaymentMethod belongs to another Customer (403).
     * @throws com.novatech.cybertech.exceptions.InvalidCardSetupException when the SetupIntent is
     *         unknown or not succeeded (400).
     */
    BankCardResponseDto addBankCard(String keycloakId, BankCardCreationRequestDto bankCardCreationRequestDto);

    void deleteBankCard(String keycloakId);

    BankCardResponseDto updateBankCard(String keycloakId, BankCardUpdateRequestDto bankCardUpdateRequestDto);

    Page<BankCardResponseDto> getAll(Pageable pageable);

    /**
     * Returns the authenticated user's bank card as a masked response DTO. The domain model
     * enforces one card per user ({@code UserEntity.bankCardEntity} is a {@code @OneToOne}),
     * so this simply resolves the caller's single card, if any.
     *
     * @param keycloakId the authenticated user's subject identifier.
     * @return the masked DTO of the user's card.
     * @throws com.novatech.cybertech.exceptions.BankCardNotFoundException when the user has no card.
     */
    BankCardResponseDto getCard(String keycloakId);

    /**
     * Admin read of any card by UUID (masked DTO).
     *
     * @throws com.novatech.cybertech.exceptions.BankCardNotFoundException when no card matches.
     */
    BankCardResponseDto getByUUID(UUID uuid);

    /**
     * Admin delete of any card by UUID. {@code adminKeycloakId} is not an ownership check — admins
     * may delete any user's card by design — it is the acting admin recorded in the PCI audit log.
     */
    void deleteByUUID(UUID uuid, String adminKeycloakId);

}
