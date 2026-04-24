package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
}
