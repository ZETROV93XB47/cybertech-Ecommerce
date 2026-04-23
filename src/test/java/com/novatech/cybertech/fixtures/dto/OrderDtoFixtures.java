package com.novatech.cybertech.fixtures.dto;

import com.novatech.cybertech.dto.request.order.OrderCancellationRequestDto;
import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.order.OrderUpdateRequestDto;
import com.novatech.cybertech.dto.response.order.OrderItemResponseDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Tiny DTO factories for the order surface. Note: {@code OrderPlacingRequestDto} on disk has no
 * {@code discountType} field today — orchestration brief mentioned setting NO_DISCOUNT here but
 * that field lives on the entity, not the request DTO. Recorded as wave note.
 */
public final class OrderDtoFixtures {

    private OrderDtoFixtures() {
    }

    public static OrderPlacingRequestDto aValidPlaceOrderRequest() {
        return aValidPlaceOrderRequestBuilder().build();
    }

    public static OrderPlacingRequestDto.OrderPlacingRequestDtoBuilder aValidPlaceOrderRequestBuilder() {
        return OrderPlacingRequestDto.builder()
                .userUuid(UUID.randomUUID())
                .paymentType(PaymentType.VISA)
                .shippingType(ShippingType.STANDARD)
                .shippingProvider(ShippingProvider.DHL)
                .shippingStreet("1 rue de Test")
                .shippingCity("Paris")
                .shippingZipCode("75001")
                .shippingCountry("FR");
    }

    public static OrderUpdateRequestDto aValidUpdateRequest() {
        return aValidUpdateRequestBuilder().build();
    }

    public static OrderUpdateRequestDto.OrderUpdateRequestDtoBuilder aValidUpdateRequestBuilder() {
        return OrderUpdateRequestDto.builder()
                .uuid(UUID.randomUUID())
                .paymentType(PaymentType.VISA)
                .shippingType(ShippingType.STANDARD)
                .shippingProvider(ShippingProvider.DHL)
                .shippingStreet("1 rue de Test")
                .shippingCity("Paris")
                .shippingZipCode("75001")
                .shippingCountry("FR")
                .itemUpdateRequestDtoList(List.of());
    }

    public static OrderCancellationRequestDto aValidCancellationRequest() {
        OrderCancellationRequestDto dto = new OrderCancellationRequestDto();
        dto.setOrderUuid(UUID.randomUUID());
        return dto;
    }

    public static OrderResponseDto aSampleOrderResponse() {
        return aSampleOrderResponseBuilder().build();
    }

    public static OrderResponseDto.OrderResponseDtoBuilder aSampleOrderResponseBuilder() {
        return OrderResponseDto.builder()
                .uuid(UUID.randomUUID())
                .userUuid(UUID.randomUUID())
                .orderDate(LocalDate.now())
                .status(OrderStatus.CREATED)
                .totalAmount(new BigDecimal("100.00"))
                .shippingAddress("1 rue de Test, Paris, 75001, FR")
                .orderItems(List.of(aSampleOrderItemResponse()));
    }

    public static OrderItemResponseDto aSampleOrderItemResponse() {
        return aSampleOrderItemResponseBuilder().build();
    }

    public static OrderItemResponseDto.OrderItemResponseDtoBuilder aSampleOrderItemResponseBuilder() {
        return OrderItemResponseDto.builder()
                .orderItemUuid(UUID.randomUUID())
                .productUuid(UUID.randomUUID())
                .productName("Sample Product")
                .quantity(1)
                .unitPrice(new BigDecimal("99.99"))
                .lineItemTotalPrice(new BigDecimal("99.99"));
    }
}
