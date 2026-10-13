package com.novatech.cybertech.dto.request.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.novatech.cybertech.constants.CyberTechAppConstants.NO_DISCOUNT_KEY;

@Data
@Builder
@AllArgsConstructor
// Jackson 3 would otherwise bind through the all-args constructor and pass null for an omitted
// discountKey, bypassing its @Builder.Default (-> 400 "must not be blank" instead of NO_DISCOUNT).
@NoArgsConstructor(onConstructor_ = @JsonCreator)
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
    @NotBlank
    private String discountKey = NO_DISCOUNT_KEY;

}