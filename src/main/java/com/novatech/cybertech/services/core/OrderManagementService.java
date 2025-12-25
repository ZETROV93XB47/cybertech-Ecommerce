package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;

import java.util.UUID;

public interface OrderManagementService {
    OrderResponseDto placeOrder(final OrderPlacingRequestDto orderPlacingRequestDto, final String jwt);

    OrderResponseDto cancelOrder(final UUID orderUUID, final String jwt);
}
