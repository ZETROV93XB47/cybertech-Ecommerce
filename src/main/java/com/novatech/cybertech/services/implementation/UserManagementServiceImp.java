package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.Role;
import com.novatech.cybertech.entities.valueObjects.Address;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.mappers.entity.UserMapper;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.BankCardManagementService;
import com.novatech.cybertech.services.core.UserManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementServiceImp implements UserManagementService {

    private final UserMapper userMapper;
    private final UserRepository userRepository;
    private final BankCardManagementService bankCardManagementService;
    private final KeycloakUserManagementService keycloakUserManagementService;

    @Override
    @Transactional
    public UserResponseDto create(final UserCreateRequestDto req) {
        String keycloakId = null;

        log.info("user creation request : {}", req);

        try {
            keycloakId = keycloakUserManagementService.createUser(req.getEmail(), req.getFirstName(), req.getLastName(), req.getPassword(), Role.USER);

            log.info("Keycloak id : {}", keycloakId);

            UserEntity user = UserEntity.builder()
                    //.uuid(UUID.randomUUID())
                    .email(req.getEmail())
                    .firstName(req.getFirstName())
                    .lastName(req.getLastName())
                    .sex(req.getSex())
                    .address(new Address(req.getStreet(), req.getCity(), req.getZipCode(), req.getCountry()))
                    .birthDate(req.getBirthDate())
                    .phoneNumber(req.getPhoneNumber())
                    .favoriteCommunicationChanel(req.getFavoriteCommunicationChanel())
                    .role(Role.USER)
                    .keycloakId(keycloakId)
                    .isActive(true)
                    .numberOfHatefulComments(0)
                    .build();

            final UserEntity savedUser = userRepository.save(user);
            log.info("Saved user : {}", savedUser);

            // Bank card is optional. When provided, delegate to BankCardManagementService.addBankCard
            // so PCI-DSS rules (PAN encryption + last4 masking, expiry guard) are applied — fixes
            // the registration-path leg of BUG-036 (previously stored PAN as plaintext inline).
            if (req.getBankCardCreationRequestDto() != null) {
                bankCardManagementService.addBankCard(keycloakId, req.getBankCardCreationRequestDto());
            }

            return userMapper.mapFromEntityToResponseDto(savedUser);

        }
        catch (RuntimeException e) {
            if (keycloakId != null) {
                keycloakUserManagementService.deleteUser(keycloakId);
            }
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<UserResponseDto> getAll() {
        return userMapper.mapFromEntityToResponseDto(userRepository.findAll());
    }

    @Transactional(readOnly = true)
    public Page<UserResponseDto> getAll(final Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::mapFromEntityToResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDto getByUUID(final UUID uuid) {
        final UserEntity user = userRepository.findByUuid(uuid).orElseThrow(() -> new UserNotFoundException("No user with the UUID: " + uuid + " found"));
        return userMapper.mapFromEntityToResponseDto(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<UserResponseDto> getByUUIDs(final Collection<UUID> uuids) {
        return userMapper.mapFromEntityToResponseDto(userRepository.findAllByUuidIn(uuids));
    }

    @Override
    @Transactional
    public UserResponseDto update(final UserUpdateRequestDto userUpdateRequestDto) {
        final UserEntity user = userRepository.findByUuid(userUpdateRequestDto.getUuid()).orElseThrow(() -> new UserNotFoundException("No user with the UUID: " + userUpdateRequestDto.getUuid() + " found"));

        // Mettre à jour Keycloak si nécessaire (ex: prénom, nom, email)
        keycloakUserManagementService.updateUser(user.getKeycloakId(), userUpdateRequestDto);

        // Mettre à jour l'entité locale
        userMapper.updateEntityFromDto(userUpdateRequestDto, user);
        UserEntity updatedUser = userRepository.save(user);

        return userMapper.mapFromEntityToResponseDto(updatedUser);
    }

    @Override
    @Transactional
    public void deleteByUUID(final UUID uuid) {
        UserEntity user = userRepository.findByUuid(uuid).orElseThrow(() -> new UserNotFoundException("No user with the UUID: " + uuid + " found"));
        keycloakUserManagementService.deleteUser(user.getKeycloakId());
        userRepository.deleteByUuid(uuid);
    }

    @Override
    @Transactional
    public void deleteByUUIDs(final Collection<UUID> uuids) {
        List<UserEntity> users = userRepository.findAllByUuidIn(uuids);

        users.forEach(user -> keycloakUserManagementService.deleteUser(user.getKeycloakId()));
        userRepository.deleteAllByUuidIn(uuids);
    }

    @Transactional
    public Collection<UserResponseDto> createAutomatically(Collection<UserEntity> users) {
        return new ArrayList<>(userMapper.mapFromEntityToResponseDto(users.stream().map(userRepository::save).toList()));
    }
}
