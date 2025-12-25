package com.novatech.cybertech.dto.response.user;

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
    private Sex sex;
    private String address;
    private Date birthDate;
    private Role role;
    private String keycloakId;
}