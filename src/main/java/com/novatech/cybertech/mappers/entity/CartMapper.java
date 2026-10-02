package com.novatech.cybertech.mappers.entity;

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

/**
 * Entity → response mapping only. Deliberately not a {@link BaseMapper}: a cart is never built
 * from a request DTO by MapStruct — its lines need product lookups and stock checks, which live
 * in {@code CartWriteTransactionalDelegateImp}. The inherited request → entity mappings used to
 * silently produce an empty cart (the {@code /cart/create} bug), so they are not generated anymore.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CartMapper {

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
    @Mapping(target = "lineItemTotalPrice", expression = "java(lineItemTotalPrice(cartItemEntity))")
    CartItemResponseDto mapFromCartItemEntityToResponseDto(CartItemEntity cartItemEntity);

    default BigDecimal lineItemTotalPrice(CartItemEntity cartItemEntity) {
        if (cartItemEntity == null || cartItemEntity.getProductEntity() == null
                || cartItemEntity.getProductEntity().getPrice() == null) {
            return BigDecimal.ZERO;
        }
        return cartItemEntity.getProductEntity().getPrice().multiply(BigDecimal.valueOf(cartItemEntity.getQuantity()));
    }

    @Named("calculateTotalPrice")
    default BigDecimal calculateTotalPrice(List<CartItemEntity> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return items.stream()
                .map(this::lineItemTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
