package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.order.OrderUpdateRequestDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public interface OrderManagementService {
    OrderResponseDto placeOrder(final OrderPlacingRequestDto orderPlacingRequestDto, final Jwt jwt);

    OrderResponseDto cancelOrder(final UUID orderUUID, final Jwt jwt);

    void deleteByUUID(final UUID uuid, final Jwt jwt);
    
    OrderResponseDto updateOrder(final OrderUpdateRequestDto orderUpdateRequestDto, final Jwt jwt);
}
