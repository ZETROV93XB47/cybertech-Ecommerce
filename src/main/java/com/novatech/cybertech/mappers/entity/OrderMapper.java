package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.order.OrderUpdateRequestDto;
import com.novatech.cybertech.dto.response.order.OrderItemResponseDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.OrderItemEntity;
import com.novatech.cybertech.entities.valueObjects.Address;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {

    /**
     * Map an {@link OrderPlacingRequestDto} into a fresh {@link OrderEntity}.
     * <p>
     * Address construction is delegated to an {@code @AfterMapping} hook
     * because {@link Address} is now an immutable value object (see BUG-132)
     * and no longer exposes setters that MapStruct could call via the
     * {@code target = "shippingAddress.xxx"} dotted-path syntax.
     * </p>
     */
    @Mapping(target = "orderDate", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "shippingAddress", ignore = true)
    OrderEntity mapFromOrderPlacingRequestDtoToOrderEntity(final OrderPlacingRequestDto orderPlacingRequestDto);

    /**
     * Populate the immutable {@link Address} on the freshly-mapped order from the
     * incoming placing request. Invoked by MapStruct after the main mapping.
     */
    @AfterMapping
    default void populateShippingAddressFromPlacing(final OrderPlacingRequestDto src, @MappingTarget final OrderEntity target) {
        if (src == null) {
            return;
        }
        target.setShippingAddress(new Address(
                src.getShippingStreet(),
                src.getShippingCity(),
                src.getShippingZipCode(),
                src.getShippingCountry()));
    }

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

    /**
     * Patch a managed {@link OrderEntity} from an {@link OrderUpdateRequestDto}.
     * <p>
     * Shipping-address fields are now applied via an {@code @AfterMapping} hook
     * (see BUG-132). When the existing entity address is {@code null}, a fresh
     * {@link Address} is constructed from the incoming DTO. When it is non-null,
     * each provided field is propagated through the immutable {@code withXxx(...)}
     * withers, preserving the {@code NullValuePropertyMappingStrategy.IGNORE}
     * semantics the previous setter-based mapping enforced.
     * </p>
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "orderDate", ignore = true)
    @Mapping(target = "userEntity", ignore = true)
    @Mapping(target = "orderItemEntities", ignore = true) // La gestion des listes (ajout/suppression) est préférable dans le service
    @Mapping(target = "paymentAttempts", ignore = true) // Le paiement est géré via le service de paiement et non par mapping direct
    @Mapping(target = "status", ignore = true) // Le statut suit une machine à états stricte
    @Mapping(target = "totalAmount", ignore = true) // Le montant est recalculé par le service
    @Mapping(target = "shippingAddress", ignore = true)
    void updateOrderFromOrderUpdateRequestDto(final OrderUpdateRequestDto orderUpdateRequestDto, @MappingTarget final OrderEntity orderEntity);

    /**
     * Apply the (possibly partial) shipping fields of an update request onto the
     * managed order's immutable {@link Address}. Invoked by MapStruct after the
     * scalar fields have been merged.
     */
    @AfterMapping
    default void mergeShippingAddressFromUpdate(final OrderUpdateRequestDto src, @MappingTarget final OrderEntity target) {
        if (src == null) {
            return;
        }
        Address current = target.getShippingAddress();
        if (current == null) {
            target.setShippingAddress(new Address(
                    src.getShippingStreet(),
                    src.getShippingCity(),
                    src.getShippingZipCode(),
                    src.getShippingCountry()));
            return;
        }
        if (src.getShippingStreet() != null) {
            current = current.withStreet(src.getShippingStreet());
        }
        if (src.getShippingCity() != null) {
            current = current.withCity(src.getShippingCity());
        }
        if (src.getShippingZipCode() != null) {
            current = current.withZipCode(src.getShippingZipCode());
        }
        if (src.getShippingCountry() != null) {
            current = current.withCountry(src.getShippingCountry());
        }
        target.setShippingAddress(current);
    }

    // --- Méthodes de conversion par défaut pour les Value Objects ---

    default String mapAddressToString(Address address) {
        if (address == null) return null;
        return String.format("%s, %s %s, %s", address.getStreet(), address.getZipCode(), address.getCity(), address.getCountry());
    }
}
