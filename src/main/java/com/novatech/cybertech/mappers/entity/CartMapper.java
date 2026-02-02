package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemRemoveRequestDto;
import com.novatech.cybertech.dto.response.cart.CartItemResponseDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.entities.CartEntity;
import com.novatech.cybertech.entities.CartItemEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CartMapper extends BaseMapper<CartEntity, CartCreateRequestDto, CartItemRemoveRequestDto, CartResponseDto> {

    @Override
    @Mapping(source = "uuid", target = "cartUuid")
    @Mapping(source = "userEntity.uuid", target = "userUuid")
    @Mapping(source = "cartItems", target = "items")
    @Mapping(source = "cartItems", target = "totalPrice", qualifiedByName = "calculateTotalPrice")
    CartResponseDto mapFromEntityToResponseDto(CartEntity cartEntity);

    @Mapping(expression = "java(cartItemEntity.getUuid())", target = "cartItemUuid")
    @Mapping(source = "productEntity.uuid", target = "productUuid")
    @Mapping(source = "productEntity.name", target = "productName")
    @Mapping(source = "productEntity.price", target = "unitPrice")
    @Mapping(source = "quantity", target = "quantity")
    @Mapping(target = "lineItemTotalPrice", expression = "java(cartItemEntity.getUnitPrice().multiply(java.math.BigDecimal.valueOf(cartItemEntity.getQuantity())))")
    CartItemResponseDto mapFromCartItemEntityToResponseDto(CartItemEntity cartItemEntity);

    @Named("calculateTotalPrice")
    default BigDecimal calculateTotalPrice(List<CartItemEntity> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
