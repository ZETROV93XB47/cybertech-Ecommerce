package com.novatech.cybertech.fixtures.builders;

import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.Role;
import com.novatech.cybertech.entities.enums.Sex;
import com.novatech.cybertech.entities.valueObjects.Address;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test fixture builder for {@link UserEntity}. Presets {@code uuid} explicitly because builders
 * bypass {@code BaseEntity#prePersist}. Note the prod typo {@code favoriteCommunicationChanel} is
 * preserved for compatibility.
 */
public final class UserEntityBuilder {

    private UserEntityBuilder() {
    }

    public static UserEntity aValidUser() {
        return aValidUserBuilder().build();
    }

    public static UserEntity.UserEntityBuilder<?, ?> aValidUserBuilder() {
        return UserEntity.builder()
                .uuid(UUID.randomUUID())
                .email("user@example.com")
                .firstName("Jane")
                .lastName("Doe")
                .sex(Sex.F)
                .address(Address.builder()
                        .street("1 rue de Test")
                        .city("Paris")
                        .zipCode("75001")
                        .country("FR")
                        .build())
                .birthDate(LocalDateTime.now().minusYears(30))
                .keycloakId("keycloak-" + UUID.randomUUID())
                .role(Role.USER)
                .phoneNumber("+33600000000")
                .isActive(true)
                .favoriteCommunicationChanel(CommunicationChanel.EMAIL)
                .numberOfHatefulComments(0);
    }
}
