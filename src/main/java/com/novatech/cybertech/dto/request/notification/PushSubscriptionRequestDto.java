package com.novatech.cybertech.dto.request.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PushSubscriptionRequestDto {

    @NotBlank
    @Size(max = 2048)
    private String endpoint;

    @NotNull
    @Valid
    private PushSubscriptionKeysDto keys;

    @Positive
    private Long expirationTime;
}
