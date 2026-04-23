package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.order.OrderUpdateRequestDto;
import com.novatech.cybertech.dto.response.order.OrderItemResponseDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.OrderItemEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.entities.valueObjects.Address;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.entities.valueObjects.Money;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.OrderItemEntityBuilder;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.dto.OrderDtoFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OrderMapper}.
 *
 * <p>Tech-debt observations carried from SA1.5 (still observable today):
 * <ul>
 *   <li>{@code totalAmount.currencyCode} is dropped (mapped to {@code BigDecimal} only).</li>
 *   <li>{@code mapFromEntityToResponseDto} dereferences {@code userEntity.uuid} without a null
 *       guard — pinned by {@code whenUserEntityNull_thenMappingThrows}.</li>
 *   <li>{@code mapFromOrderPlacingRequestDtoToOrderEntity} silently drops {@code userUuid} and
 *       {@code paymentType}.</li>
 * </ul>
 */
class OrderMapperTest {

    private final OrderMapper mapper = Mappers.getMapper(OrderMapper.class);

    @Nested
    @DisplayName("mapFromOrderPlacingRequestDtoToOrderEntity(OrderPlacingRequestDto)")
    class FromPlacingRequest {

        @Test
        void shouldMapShippingFieldsAndStampOrderDate() {
            OrderPlacingRequestDto dto = OrderDtoFixtures.aValidPlaceOrderRequest();
            LocalDateTime before = LocalDateTime.now().minusSeconds(1);

            OrderEntity entity = mapper.mapFromOrderPlacingRequestDtoToOrderEntity(dto);

            assertThat(entity).isNotNull();
            assertThat(entity.getShippingType()).isEqualTo(dto.getShippingType());
            assertThat(entity.getShippingProvider()).isEqualTo(dto.getShippingProvider());
            assertThat(entity.getShippingAddress()).isNotNull();
            assertThat(entity.getShippingAddress().getStreet()).isEqualTo(dto.getShippingStreet());
            assertThat(entity.getShippingAddress().getCity()).isEqualTo(dto.getShippingCity());
            assertThat(entity.getShippingAddress().getZipCode()).isEqualTo(dto.getShippingZipCode());
            assertThat(entity.getShippingAddress().getCountry()).isEqualTo(dto.getShippingCountry());
            assertThat(entity.getOrderDate()).isAfterOrEqualTo(before);
        }

        @Test
        void shouldDropUserUuidAndPaymentType_documentingTechDebt() {
            // Both userUuid and paymentType have no @Mapping target — they are silently dropped.
            // Service layer is expected to wire UserEntity itself; PaymentType has no field on OrderEntity.
            OrderPlacingRequestDto dto = OrderDtoFixtures.aValidPlaceOrderRequestBuilder()
                    .userUuid(UUID.randomUUID())
                    .paymentType(PaymentType.MASTERCARD)
                    .build();

            OrderEntity entity = mapper.mapFromOrderPlacingRequestDtoToOrderEntity(dto);

            assertThat(entity.getUserEntity()).isNull();
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromOrderPlacingRequestDtoToOrderEntity(null)).isNull();
        }
    }

    @Nested
    @DisplayName("mapFromEntityToResponseDto(OrderEntity)")
    class ToResponseDto {

        @Test
        void shouldMapEntityToResponseDtoWithAllFields() {
            UserEntity user = UserEntityBuilder.aValidUser();
            OrderItemEntity item = OrderItemEntityBuilder.aValidOrderItemBuilder()
                    .quantity(2)
                    .unitPrice(new BigDecimal("50.00"))
                    .subtotal(new BigDecimal("100.00"))
                    .build();
            OrderEntity entity = OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(user)
                    .orderItemEntities(new ArrayList<>(List.of(item)))
                    .build();

            OrderResponseDto dto = mapper.mapFromEntityToResponseDto(entity);

            assertThat(dto).isNotNull();
            assertThat(dto.getUuid()).isEqualTo(entity.getUuid());
            assertThat(dto.getUserUuid()).isEqualTo(user.getUuid());
            assertThat(dto.getStatus()).isEqualTo(entity.getStatus());
            assertThat(dto.getTotalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(dto.getOrderDate()).isEqualTo(entity.getOrderDate().toLocalDate());
            assertThat(dto.getShippingAddress())
                    .isEqualTo("1 rue de Test, 75001 Paris, FR");
            assertThat(dto.getOrderItems()).hasSize(1);
            assertThat(dto.getOrderItems().get(0).getProductUuid()).isEqualTo(item.getProductEntity().getUuid());
        }

        @Test
        void shouldMapTotalAmount_dropsCurrency_documentingTechDebt() {
            OrderEntity entity = OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(UserEntityBuilder.aValidUser())
                    .totalAmount(new Money(new BigDecimal("250.00"), CurrencyCode.USD))
                    .build();

            OrderResponseDto dto = mapper.mapFromEntityToResponseDto(entity);

            assertThat(dto.getTotalAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
            // Currency is intentionally dropped — only Money.amount is wired to the BigDecimal target.
        }

        @Test
        void shouldHandleEmptyOrderItemsCollection() {
            OrderEntity entity = OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(UserEntityBuilder.aValidUser())
                    .orderItemEntities(new ArrayList<>())
                    .build();

            OrderResponseDto dto = mapper.mapFromEntityToResponseDto(entity);

            assertThat(dto.getOrderItems()).isNotNull().isEmpty();
        }

        @Test
        void shouldHandleNullShippingAddressGracefully() {
            OrderEntity entity = OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(UserEntityBuilder.aValidUser())
                    .shippingAddress(null)
                    .build();

            OrderResponseDto dto = mapper.mapFromEntityToResponseDto(entity);

            assertThat(dto.getShippingAddress()).isNull();
        }

        @Test
        void shouldHandleNullTotalAmount() {
            OrderEntity entity = OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(UserEntityBuilder.aValidUser())
                    .totalAmount(null)
                    .build();

            OrderResponseDto dto = mapper.mapFromEntityToResponseDto(entity);

            assertThat(dto.getTotalAmount()).isNull();
        }

        @Test
        void whenUserEntityNull_thenMappingThrows_documentsBrittleness() {
            // OrderMapper uses expression `orderEntity.getUserEntity().getUuid()` with no null guard.
            // Pinned to surface a regression if/when a guard is added.
            OrderEntity entity = OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(null)
                    .build();

            assertThatThrownBy(() -> mapper.mapFromEntityToResponseDto(entity))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromEntityToResponseDto((OrderEntity) null)).isNull();
        }
    }

    @Nested
    @DisplayName("mapFromOrderItemEntityToOrderItemResponseDto(OrderItemEntity)")
    class ItemToResponseDto {

        @Test
        void shouldMapAllItemFields() {
            OrderItemEntity item = OrderItemEntityBuilder.aValidOrderItemBuilder()
                    .quantity(3)
                    .unitPrice(new BigDecimal("12.50"))
                    .subtotal(new BigDecimal("37.50"))
                    .build();

            OrderItemResponseDto dto = mapper.mapFromOrderItemEntityToOrderItemResponseDto(item);

            assertThat(dto).isNotNull();
            assertThat(dto.getOrderItemUuid()).isEqualTo(item.getUuid());
            assertThat(dto.getProductUuid()).isEqualTo(item.getProductEntity().getUuid());
            assertThat(dto.getProductName()).isEqualTo(item.getProductEntity().getName());
            assertThat(dto.getQuantity()).isEqualTo(3);
            assertThat(dto.getUnitPrice()).isEqualByComparingTo("12.50");
            assertThat(dto.getLineItemTotalPrice()).isEqualByComparingTo("37.50");
        }

        @Test
        void shouldHandleNullProductEntity() {
            OrderItemEntity item = OrderItemEntityBuilder.aValidOrderItemBuilder()
                    .productEntity(null)
                    .build();

            OrderItemResponseDto dto = mapper.mapFromOrderItemEntityToOrderItemResponseDto(item);

            assertThat(dto).isNotNull();
            assertThat(dto.getProductUuid()).isNull();
            assertThat(dto.getProductName()).isNull();
            assertThat(dto.getOrderItemUuid()).isEqualTo(item.getUuid());
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromOrderItemEntityToOrderItemResponseDto(null)).isNull();
        }
    }

    @Nested
    @DisplayName("updateOrderFromOrderUpdateRequestDto(OrderUpdateRequestDto, OrderEntity)")
    class UpdateOrder {

        @Test
        void shouldUpdateShippingAndPreserveProtectedFields() {
            UUID originalUuid = UUID.randomUUID();
            LocalDateTime originalDate = LocalDateTime.now().minusDays(1);
            OrderEntity entity = OrderEntityBuilder.aValidOrderBuilder()
                    .uuid(originalUuid)
                    .orderDate(originalDate)
                    .status(OrderStatus.PAID)
                    .userEntity(UserEntityBuilder.aValidUser())
                    .build();

            OrderUpdateRequestDto dto = OrderDtoFixtures.aValidUpdateRequestBuilder()
                    .shippingStreet("99 new street")
                    .shippingCity("Lyon")
                    .shippingZipCode("69000")
                    .shippingCountry("FR")
                    .shippingType(ShippingType.EXPRESS)
                    .shippingProvider(ShippingProvider.FEDEX)
                    .build();

            mapper.updateOrderFromOrderUpdateRequestDto(dto, entity);

            // Protected (ignored) fields untouched.
            assertThat(entity.getUuid()).isEqualTo(originalUuid);
            assertThat(entity.getOrderDate()).isEqualTo(originalDate);
            assertThat(entity.getStatus()).isEqualTo(OrderStatus.PAID);
            // Shipping updated.
            assertThat(entity.getShippingAddress().getStreet()).isEqualTo("99 new street");
            assertThat(entity.getShippingAddress().getCity()).isEqualTo("Lyon");
            assertThat(entity.getShippingAddress().getZipCode()).isEqualTo("69000");
            assertThat(entity.getShippingAddress().getCountry()).isEqualTo("FR");
            assertThat(entity.getShippingType()).isEqualTo(ShippingType.EXPRESS);
            assertThat(entity.getShippingProvider()).isEqualTo(ShippingProvider.FEDEX);
        }

        @Test
        void shouldNoOpWhenDtoIsNull() {
            OrderEntity entity = OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(UserEntityBuilder.aValidUser())
                    .build();
            String addrBefore = entity.getShippingAddress().getStreet();

            mapper.updateOrderFromOrderUpdateRequestDto(null, entity);

            assertThat(entity.getShippingAddress().getStreet()).isEqualTo(addrBefore);
        }

        @Test
        void shouldInitialiseAddressWhenEntityAddressIsNull() {
            OrderEntity entity = OrderEntityBuilder.aValidOrderBuilder()
                    .userEntity(UserEntityBuilder.aValidUser())
                    .shippingAddress(null)
                    .build();

            mapper.updateOrderFromOrderUpdateRequestDto(
                    OrderDtoFixtures.aValidUpdateRequestBuilder().shippingStreet("X").build(),
                    entity);

            assertThat(entity.getShippingAddress()).isNotNull();
            assertThat(entity.getShippingAddress().getStreet()).isEqualTo("X");
        }
    }

    @Nested
    @DisplayName("mapAddressToString(Address) — value-object helper")
    class AddressHelper {

        @Test
        void shouldReturnNullForNullAddress() {
            assertThat(mapper.mapAddressToString(null)).isNull();
        }

        @Test
        void shouldFormatAddressFields() {
            Address address = Address.builder()
                    .street("10 rue Foo")
                    .city("Paris")
                    .zipCode("75002")
                    .country("FR")
                    .build();

            assertThat(mapper.mapAddressToString(address))
                    .isEqualTo("10 rue Foo, 75002 Paris, FR");
        }
    }
}
