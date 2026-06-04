package com.novatech.cybertech.fixtures.dto;

import com.novatech.cybertech.dto.request.user.BankCardCreationRequestDto;
import com.novatech.cybertech.dto.request.user.BankCardUpdateRequestDto;
import com.novatech.cybertech.dto.request.user.UserCreateRequestDto;
import com.novatech.cybertech.dto.request.user.UserUpdateRequestDto;
import com.novatech.cybertech.dto.response.user.BankCardResponseDto;
import com.novatech.cybertech.dto.response.user.UserResponseDto;
import com.novatech.cybertech.entities.enums.BankCardType;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.Role;
import com.novatech.cybertech.entities.enums.Sex;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;

/**
 * Tiny DTO factories for the user/bank-card surface.
 *
 * <p>Note: {@link UserResponseDto#getBirthDate()} is {@link Date} while {@link UserCreateRequestDto}
 * and {@link UserUpdateRequestDto} both use {@link LocalDateTime}. Cross-DTO conversions in tests
 * must hop through {@link java.time.Instant}.
 */
public final class UserDtoFixtures {

    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("MM/yyyy");

    private UserDtoFixtures() {
    }

    public static UserCreateRequestDto aValidCreateRequest() {
        return aValidCreateRequestBuilder().build();
    }

    public static UserCreateRequestDto.UserCreateRequestDtoBuilder aValidCreateRequestBuilder() {
        return UserCreateRequestDto.builder()
                .email("user@example.com")
                .firstName("Jane")
                .lastName("Doe")
                .sex(Sex.F)
                .phoneNumber("+33600000000")
                .favoriteCommunicationChanel(CommunicationChanel.EMAIL)
                .street("1 rue de Test")
                .city("Paris")
                .zipCode("75001")
                .country("FR")
                .birthDate(LocalDateTime.now().minusYears(30))
                .password("Sup3rSecur3!")
                .bankCardCreationRequestDto(aValidBankCardCreationRequest());
    }

    public static UserUpdateRequestDto aValidUpdateRequest() {
        UserUpdateRequestDto dto = new UserUpdateRequestDto();
        dto.setUuid(UUID.randomUUID());
        dto.setFirstName("Jane");
        dto.setLastName("Doe");
        dto.setSex(Sex.F);
        dto.setEmail("user@example.com");
        dto.setAddress("1 rue de Test, Paris");
        dto.setBirthDate(LocalDateTime.now().minusYears(30));
        return dto;
    }

    public static BankCardCreationRequestDto aValidBankCardCreationRequest() {
        return aValidBankCardCreationRequestBuilder().build();
    }

    public static BankCardCreationRequestDto.BankCardCreationRequestDtoBuilder aValidBankCardCreationRequestBuilder() {
        return BankCardCreationRequestDto.builder()
                .cardHolderName("Jane Doe")
                .cardNumber("4242424242424242")
                .expiryDate(LocalDate.now().plusYears(5).format(EXPIRY_FORMAT))
                .cardType(BankCardType.VISA);
    }

    public static UserResponseDto aSampleUserResponse() {
        return aSampleUserResponseBuilder().build();
    }

    public static UserResponseDto.UserResponseDtoBuilder aSampleUserResponseBuilder() {
        return UserResponseDto.builder()
                .uuid(UUID.randomUUID())
                .email("user@example.com")
                .firstName("Jane")
                .lastName("Doe")
                .username("jane.doe")
                .sex(Sex.F)
                .address("1 rue de Test")
                .birthDate(Date.from(LocalDateTime.now()
                        .minusYears(30)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()))
                .role(Role.USER)
                .keycloakId("keycloak-" + UUID.randomUUID());
    }

    public static BankCardUpdateRequestDto aValidBankCardUpdateRequest() {
        return aValidBankCardUpdateRequestBuilder().build();
    }

    public static BankCardUpdateRequestDto.BankCardUpdateRequestDtoBuilder aValidBankCardUpdateRequestBuilder() {
        return BankCardUpdateRequestDto.builder()
                .uuid(UUID.randomUUID())
                .cardHolderName("Jane Doe")
                .expiryDate(LocalDate.now().plusYears(5).format(EXPIRY_FORMAT));
    }

    public static BankCardResponseDto aSampleBankCardResponse() {
        return aSampleBankCardResponseBuilder().build();
    }

    public static BankCardResponseDto.BankCardResponseDtoBuilder aSampleBankCardResponseBuilder() {
        return BankCardResponseDto.builder()
                .uuid(UUID.randomUUID())
                .cardHolderName("Jane Doe")
                .cardNumber("4242424242424242")
                .expiryDate(LocalDate.now().plusYears(5).format(EXPIRY_FORMAT))
                .cardType(BankCardType.VISA)
                .userUuid(UUID.randomUUID());
    }
}
