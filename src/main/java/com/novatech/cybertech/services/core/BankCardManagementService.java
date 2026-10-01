package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface BankCardManagementService extends CrudBaseService<UUID, BankCardCreationRequestDto, BankCardUpdateRequestDto, BankCardResponseDto, String> {

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

    // deleteByUUID(UUID, String) is now the inherited CrudBaseService method itself — the admin
    // caller's identity rides along for the audit trail (see BankCardManagementServiceImp), it is
    // not an ownership check: admin callers may delete any user's card by design.

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
