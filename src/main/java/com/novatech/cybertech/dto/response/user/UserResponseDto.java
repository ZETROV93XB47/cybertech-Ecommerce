package com.novatech.cybertech.dto.response.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.novatech.cybertech.entities.enums.Role;
import com.novatech.cybertech.entities.enums.Sex;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class UserResponseDto {
    private UUID uuid;
    private String email;
    private String firstName;
    private String lastName;
    private String username;
    private Sex sex;
    private String address;
    private Date birthDate;
    private Role role;

    /**
     * BUG-LEAK-D6: stop leaking the Keycloak subject on every user-fetch response.
     *
     * <p>The {@code POST /register} response is built from a {@code Map.of("id", ..., "keycloakId", ...)}
     * server-side (see {@link com.novatech.cybertech.api.controllers.implementation.UserManagementController#register}),
     * NOT from this DTO — registration still returns the {@code keycloakId} to the client because
     * it reads the field via the Lombok-generated getter at {@code Map.of} construction time
     * <em>before</em> Jackson serialises the {@code Map}; only the <em>field</em> (when serialised
     * as part of {@code UserResponseDto}) is suppressed.</p>
     */
    @JsonIgnore
    private String keycloakId;
}