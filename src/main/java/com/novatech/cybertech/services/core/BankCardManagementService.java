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
}