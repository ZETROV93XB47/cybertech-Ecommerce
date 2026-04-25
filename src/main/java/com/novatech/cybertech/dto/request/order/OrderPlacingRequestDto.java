package com.novatech.cybertech.dto.request.order;

import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPlacingRequestDto {

    // Identity (userUuid) removed: identity is authoritatively derived from the JWT subject in the
    // controller/service layer. Allowing the client to declare a userUuid in the body created an
    // order-spoofing footgun if any future refactor accidentally read the DTO field instead of the JWT.

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

    @Builder.Default
    private DiscountType discountType = DiscountType.NO_DISCOUNT;

}