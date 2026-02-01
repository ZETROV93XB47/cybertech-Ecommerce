package com.novatech.cybertech.dto.request.order;

import com.novatech.cybertech.dto.request.orderItem.OrderItemCreateRequestDto;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderUpdateRequestDto {

    @NotNull(message = "Order UUID cannot be null")
    private UUID uuid;

    @NotNull(message = "The payment Method Type cannot be null")
    private PaymentType paymentType;

    @NotNull(message = "Shipping Type cannot be null")
    private ShippingType shippingType;

    @NotNull(message = "Shipping Provider cannot be null")
    private ShippingProvider shippingProvider;

    @NotBlank(message = "Shipping Street cannot be blank")
    private String shippingStreet;

    @NotBlank(message = "Shipping City cannot be blank")
    private String shippingCity;

    @NotBlank(message = "Shipping Zip code cannot be blank")
    private String shippingZipCode;

    @NotBlank(message = "Shipping Country cannot be blank")
    private String shippingCountry;

    @NotNull(message = "Idempotency Key cannot be null")
    @NotBlank(message = "Idempotency Key cannot be blank")
    private String idempotencyKey;


    private List<OrderItemCreateRequestDto> itemUpdateRequestDtoList;
}
