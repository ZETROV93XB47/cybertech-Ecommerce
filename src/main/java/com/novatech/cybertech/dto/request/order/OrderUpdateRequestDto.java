package com.novatech.cybertech.dto.request.order;

import com.novatech.cybertech.dto.request.orderItem.OrderItemCreateRequestDto;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    private UUID orderUuid;

    @NotNull(message = "The payment Method Type cannot be null")
    private PaymentType paymentType;

    @NotNull(message = "Shipping Type cannot be null")
    private ShippingType shippingType;

    @NotNull(message = "Shipping Provider cannot be null")
    private ShippingProvider shippingProvider;

    @Size(max = 255, message = "Shipping address must be at most 255 characters")
    private String shippingAddress;

    private List<OrderItemCreateRequestDto> itemUpdateRequestDtoList;
}
