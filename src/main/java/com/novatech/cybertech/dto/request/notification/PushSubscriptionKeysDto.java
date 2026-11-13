package com.novatech.cybertech.dto.request.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PushSubscriptionKeysDto {

    @NotBlank
    @Size(max = 256)
    private String p256dh;

    @NotBlank
    @Size(max = 128)
    private String auth;
}
