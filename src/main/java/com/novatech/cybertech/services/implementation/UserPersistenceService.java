package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.Role;
import com.novatech.cybertech.entities.valueObjects.Address;
import com.novatech.cybertech.mappers.entity.UserMapper;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.BankCardManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence-only collaborator for user creation.
 *
 * <p>Extracted from {@link UserManagementServiceImp} so that the database write (and the
 * bank-card creation, which must share the same SQL transaction) runs in its own
 * {@link Propagation#REQUIRES_NEW} transaction. The orchestrating
 * {@link UserManagementServiceImp#create(UserCreateRequestDto)} stays NON-transactional, which
 * lets it run a compensating Keycloak {@code deleteUser} OUTSIDE any rolling-back TX context if
 * the local persistence fails — fixing the orphaned-Keycloak-user inconsistency that the previous
 * single-{@code @Transactional} flow could not avoid.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPersistenceService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final BankCardManagementService bankCardManagementService;

    /**
     * Persist the local {@link UserEntity} (and any optional bank card) under a fresh transaction.
     *
     * <p>Uses {@link Propagation#REQUIRES_NEW} so the SQL work is isolated from any caller TX —
     * a rollback here cannot affect the caller, and the caller is free to run its compensating
     * Keycloak {@code deleteUser} after we throw without those operations being swept into a
     * rolling-back transactional context.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserResponseDto saveNewUser(final UserCreateRequestDto req, final String keycloakId) {
        final UserEntity user = UserEntity.builder()
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
        // so PCI-DSS rules (PAN encryption + last4 masking, expiry guard) are applied. The call
        // must stay inside this TX so a card persistence failure also rolls back the user insert.
        if (req.getBankCardCreationRequestDto() != null) {
            bankCardManagementService.addBankCard(keycloakId, req.getBankCardCreationRequestDto());
        }

        return userMapper.mapFromEntityToResponseDto(savedUser);
    }
}
