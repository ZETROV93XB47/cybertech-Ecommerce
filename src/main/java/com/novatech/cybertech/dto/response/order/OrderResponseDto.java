package com.novatech.cybertech.dto.response.order;

import com.novatech.cybertech.entities.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponseDto {

    //TODO: mapper correctement ce champ
    private UUID uuid;
    //TODO: mapper correctement ce champ
    private UUID userUuid;
    
    private Date orderDate;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String shippingAddress;

    //TODO: mapper correctement ce champ
    private List<OrderItemResponseDto> orderItems;
}
