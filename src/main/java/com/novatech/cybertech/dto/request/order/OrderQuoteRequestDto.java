package com.novatech.cybertech.dto.request.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.novatech.cybertech.constants.CyberTechAppConstants.NO_DISCOUNT_KEY;

/**
 * Options of the order to quote. The lines are NOT part of the request: they always come from the
 * caller's server-side cart (JWT), at the products' current prices — exactly like
 * {@link OrderPlacingRequestDto}.
 */
@Data
@Builder
@AllArgsConstructor
// Jackson 3 would otherwise bind through the all-args constructor and pass null for an omitted
// discountKey, bypassing its @Builder.Default (-> 400 "must not be blank" instead of NO_DISCOUNT).
@NoArgsConstructor(onConstructor_ = @JsonCreator)
public class OrderQuoteRequestDto {

    @NotNull
    private ShippingType shippingType;

    @NotNull
    private ShippingProvider shippingProvider;

    @Builder.Default
    @NotBlank
    @Schema(description = "Campaign to apply; NO_DISCOUNT when omitted.", defaultValue = NO_DISCOUNT_KEY)
    private String discountKey = NO_DISCOUNT_KEY;
}
