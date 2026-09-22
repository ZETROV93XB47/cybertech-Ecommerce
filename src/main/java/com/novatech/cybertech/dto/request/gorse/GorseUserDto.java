package com.novatech.cybertech.dto.request.gorse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Shape expected by Gorse's {@code POST /api/users} batch-insert endpoint
 * (https://gorse.io/docs/api/restful-api.html) — field names are PascalCase on the wire.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GorseUserDto {

    @JsonProperty("UserId")
    private String userId;
}
