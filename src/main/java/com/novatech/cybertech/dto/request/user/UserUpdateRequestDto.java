package com.novatech.cybertech.dto.request.user;

import com.novatech.cybertech.entities.enums.Sex;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequestDto {

    @NotNull(message = "User UUID cannot be null")
    private UUID uuid;//Il me faut l'uuid pour pouvoir retrouver le User, pas pour le maj

    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    private String firstName; // Peut être null si non modifié

    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    private String lastName; // Peut être null si non modifié

    private Sex sex; // Peut être null si non modifié

    @Email(message = "Email must be valid")
    private String email;

    private String address; // Peut être null si non modifié

    @Past(message = "Birth date must be in the past")
    private LocalDateTime birthDate; // Peut être null si non modifié
}