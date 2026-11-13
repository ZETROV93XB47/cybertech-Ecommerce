package com.novatech.cybertech.dto.request.user;

import com.novatech.cybertech.entities.enums.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleUpdateRequestDto {

    @NotNull(message = "Role cannot be null")
    private Role role;
}
