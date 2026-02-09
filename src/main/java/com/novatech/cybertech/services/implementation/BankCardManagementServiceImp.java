package com.novatech.cybertech.services.implementation;


import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.entities.BankCardEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.exceptions.BankCardNotFoundException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.mappers.entity.BankCardMapper;
import com.novatech.cybertech.repositories.BankCardRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.BankCardManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankCardManagementServiceImp implements BankCardManagementService {

    private final BankCardMapper bankCardMapper;
    private final UserRepository userRepository;
    private final BankCardRepository bankCardRepository;

    // --- Méthodes Spécifiques (User Context) ---

    @Override
    @Transactional
    public BankCardResponseDto addBankCard(String keycloakId, BankCardCreationRequestDto dto) {
        UserEntity user = userRepository.findByKeycloakId(keycloakId).orElseThrow(() -> new UserNotFoundException("User not found"));

        if (user.getBankCardEntity() != null) {
            throw new IllegalStateException("User already has a bank card.");
        }

        BankCardEntity bankCardEntity = bankCardMapper.mapFromCreationRequestToEntity(dto);
        bankCardEntity.setUserEntity(user);

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
}