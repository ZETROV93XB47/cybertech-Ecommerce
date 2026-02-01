package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.order.OrderUpdateRequestDto;
import com.novatech.cybertech.dto.response.order.OrderItemResponseDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.OrderItemEntity;
import com.novatech.cybertech.entities.valueObjects.Address;
import com.novatech.cybertech.entities.valueObjects.Money;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {

//    @Mapping(target = "uuid", expression = "java(java.util.UUID.randomUUID())")
    @Mapping(target = "orderDate", expression = "java(java.time.LocalDateTime.now())")
    //@Mapping(target = "totalAmount", ignore = true) // Sera calculé par le service
    @Mapping(target = "shippingAddress.street", source = "shippingStreet")
    @Mapping(target = "shippingAddress.city", source = "shippingCity")
    @Mapping(target = "shippingAddress.zipCode", source = "shippingZipCode")
    @Mapping(target = "shippingAddress.country", source = "shippingCountry")
    OrderEntity mapFromOrderPlacingRequestDtoToOrderEntity(final OrderPlacingRequestDto orderPlacingRequestDto);


    @Mapping(target = "userUuid", expression = "java(orderEntity.getUserEntity().getUuid())")
    @Mapping(target = "orderItems", source = "orderItemEntities")
    @Mapping(target = "totalAmount", source = "totalAmount.amount")
    @Mapping(target = "shippingAddress", expression = "java(mapAddressToString(orderEntity.getShippingAddress()))")
    OrderResponseDto mapFromEntityToResponseDto(final OrderEntity orderEntity);

    @Mapping(target = "orderItemUuid", source = "uuid")
    @Mapping(target = "productUuid", source = "productEntity.uuid")
    @Mapping(target = "productName", source = "productEntity.name")
    @Mapping(target = "lineItemTotalPrice", source = "subtotal")
    OrderItemResponseDto mapFromOrderItemEntityToOrderItemResponseDto(final OrderItemEntity orderItemEntity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "orderDate", ignore = true)
    @Mapping(target = "userEntity", ignore = true)
    @Mapping(target = "orderItemEntities", ignore = true) // La gestion des listes (ajout/suppression) est préférable dans le service
    @Mapping(target = "paymentAttempts", ignore = true) // Le paiement est géré via le service de paiement et non par mapping direct
    @Mapping(target = "status", ignore = true) // Le statut suit une machine à états stricte
    @Mapping(target = "totalAmount", ignore = true) // Le montant est recalculé par le service
    @Mapping(target = "shippingAddress.street", source = "shippingStreet")
    @Mapping(target = "shippingAddress.city", source = "shippingCity")
    @Mapping(target = "shippingAddress.zipCode", source = "shippingZipCode")
    @Mapping(target = "shippingAddress.country", source = "shippingCountry")
    void updateOrderFromOrderUpdateRequestDto(final OrderUpdateRequestDto orderUpdateRequestDto, @MappingTarget final OrderEntity orderEntity);

    // --- Méthodes de conversion par défaut pour les Value Objects ---

    default String mapAddressToString(Address address) {
        if (address == null) return null;
        return String.format("%s, %s %s, %s", address.getStreet(), address.getZipCode(), address.getCity(), address.getCountry());
    }
}