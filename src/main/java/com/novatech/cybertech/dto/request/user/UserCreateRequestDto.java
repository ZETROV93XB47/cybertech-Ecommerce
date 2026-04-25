package com.novatech.cybertech.dto.request.user;

import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.Sex;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateRequestDto {

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email should be valid")
    private String email;

    @NotBlank(message = "First name cannot be blank")
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    private String firstName;

    @NotBlank(message = "Last name cannot be blank")
    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    private String lastName;

    @NotNull(message = "Sex cannot be null")
    private Sex sex;

    @NotBlank(message = "Phone number cannot be blank")
    private String phoneNumber;

    @NotNull(message = "Communication chanel cannot be null")
    private CommunicationChanel favoriteCommunicationChanel;

    @NotBlank(message = "Street cannot be blank")
    private String street;

    @NotBlank(message = "City cannot be blank")
    private String city;

    @NotBlank(message = "Zip code cannot be blank")
    private String zipCode;

    @NotBlank(message = "Country cannot be blank")
    private String country;

    // La date de naissance peut être optionnelle
    @Past(message = "Birth date must be in the past")
    private LocalDateTime birthDate;

    @NotBlank(message = "Password cannot be blank")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    // Tu peux ajouter une @Pattern pour la complexité du mot de passe si nécessaire
    private String password;

    /**
     * Optional. Users may sign up without a card and add one later via
     * {@code POST /api/v1/services/bank-card/add}. When present, registration
     * delegates to {@code BankCardManagementService.addBankCard} so the same
     * PCI-DSS rules (encryption + last4 masking, expiry guard) apply.
     */
    private BankCardCreationRequestDto bankCardCreationRequestDto;

}