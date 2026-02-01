package com.novatech.cybertech.dto.request.order;

import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPlacingRequestDto {

    @NotNull
    private UUID userUuid;

    @NotNull
    private PaymentType paymentType;

    @NotNull
    private ShippingType shippingType;

    @NotNull
    private ShippingProvider shippingProvider;

    @NotBlank
    private String shippingStreet;
    @NotBlank
    private String shippingCity;
    @NotBlank
    private String shippingZipCode;
    @NotBlank
    private String shippingCountry;

    @NotNull(message = "Idempotency Key cannot be null")
    @NotBlank(message = "Idempotency Key cannot be blank")
    private String idempotencyKey;

}