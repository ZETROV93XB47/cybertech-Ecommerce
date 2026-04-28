package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface BankCardManagementService extends CrudBaseService<UUID, BankCardCreationRequestDto, BankCardUpdateRequestDto, BankCardResponseDto> {

    BankCardResponseDto addBankCard(String keycloakId, BankCardCreationRequestDto bankCardCreationRequestDto);

    void deleteBankCard(String keycloakId);

    BankCardResponseDto updateBankCard(String keycloakId, BankCardUpdateRequestDto bankCardUpdateRequestDto);

    Page<BankCardResponseDto> getAll(Pageable pageable);

    /**
     * BUG-038: marks a specific card as the user's default.
     *
     * <p>Verifies that the card belongs to the caller (otherwise
     * {@code UnauthorizedBankCardAccessException}), clears the default flag on every other
     * card the user owns, and flips {@code isDefault=true} on the target.</p>
     *
     * @param cardUuid the card to promote to default.
     * @param keycloakId the authenticated user's subject identifier, used for ownership.
     */
    void setDefault(UUID cardUuid, String keycloakId);

    /**
     * BUG-038: returns the user's default card as a masked response DTO.
     *
     * @param keycloakId the authenticated user's subject identifier.
     * @return the masked DTO of the default card.
     */
    BankCardResponseDto getDefaultCard(String keycloakId);

    /**
     * BUG-161: ownership-checked delete-by-UUID for non-admin callers.
     *
     * <p>Loads the card, asserts the caller's keycloakId matches the card owner,
     * then deletes. Mirrors {@code CartServiceImp#deleteByUUID(UUID, String)}.
     * The single-arg {@link #deleteByUUID(java.util.UUID)} is kept for the
     * {@link CrudBaseService} contract and admin-only call sites.</p>
     *
     * @param uuid       card to delete.
     * @param keycloakId caller identity (JWT subject).
     * @throws com.novatech.cybertech.exceptions.BankCardNotFoundException        when no card with that UUID exists.
     * @throws com.novatech.cybertech.exceptions.UnauthorizedBankCardAccessException when the caller does not own the card.
     */
    void deleteByUUID(java.util.UUID uuid, String keycloakId);

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
