package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Bank card domain service.
 * <p>
 * Deliberately NOT a {@link CrudBaseService}: a user owns at most one card, managed through the
 * caller-scoped methods ({@link #addBankCard}, {@link #updateBankCard}, {@link #deleteBankCard}).
 * The admin back-office only lists, reads and deletes cards — an admin never types in a
 * customer's card number nor edits someone else's card, so the generic {@code create} /
 * {@code update} had no legitimate use case and were removed.
 */
public interface BankCardManagementService {

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
    BankCardResponseDto getDefaultCard(String keycloakId);

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

    /**
     * Frontend-gap #4 — list every bank card belonging to the authenticated user.
     *
     * <p>The current domain model enforces 1-card-per-user via {@code UserEntity.bankCardEntity}
     * (a {@code @OneToOne}), so this endpoint will return either an empty list or a list with
     * exactly one masked DTO. The list shape is preserved to keep the contract forward-
     * compatible: should the team relax the 1-card constraint in the future, no clients will
     * need to change.</p>
     *
     * @param keycloakId the JWT subject of the authenticated caller.
     * @return all cards owned by that user (PCI-masked DTOs); empty list when the user has none.
     */
    List<BankCardResponseDto> findAllMine(String keycloakId);
}
