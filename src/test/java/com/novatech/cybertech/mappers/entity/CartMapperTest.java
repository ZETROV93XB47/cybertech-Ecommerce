package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.request.cart.CartCreateRequestDto;
import com.novatech.cybertech.dto.request.cart.CartItemRemoveRequestDto;
import com.novatech.cybertech.dto.response.cart.CartItemResponseDto;
import com.novatech.cybertech.dto.response.cart.CartResponseDto;
import com.novatech.cybertech.entities.CartEntity;
import com.novatech.cybertech.entities.CartItemEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.fixtures.builders.CartEntityBuilder;
import com.novatech.cybertech.fixtures.builders.CartItemEntityBuilder;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.fixtures.dto.CartDtoFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CartMapper}.
 *
 * <p>Verifies BUG-018 fix (null {@code unitPrice} no longer NPEs in {@code lineItemTotalPrice}).
 */
class CartMapperTest {

    private final CartMapper mapper = Mappers.getMapper(CartMapper.class);

    @Nested
    @DisplayName("mapFromEntityToResponseDto(CartEntity)")
    class CartToResponse {

        @Test
        void shouldMapEntityToResponseDtoWithAllFields() {
            UserEntity user = UserEntityBuilder.aValidUser();
            ProductEntity product = ProductEntityBuilder.aValidProductBuilder()
                    .price(new BigDecimal("10.00"))
                    .build();
            CartItemEntity item = CartItemEntityBuilder.aValidCartItemBuilder()
                    .productEntity(product)
                    .quantity(3)
                    .unitPrice(new BigDecimal("10.00"))
                    .build();
            CartEntity cart = CartEntityBuilder.aValidCartBuilder()
                    .userEntity(user)
                    .cartItems(new ArrayList<>(List.of(item)))
                    .build();

            CartResponseDto dto = mapper.mapFromEntityToResponseDto(cart);

            assertThat(dto).isNotNull();
            assertThat(dto.getCartUuid()).isEqualTo(cart.getUuid());
            assertThat(dto.getUserUuid()).isEqualTo(user.getUuid());
            assertThat(dto.getItems()).hasSize(1);
            assertThat(dto.getTotalPrice()).isEqualByComparingTo("30.00");
        }

        @Test
        void shouldHandleEmptyCartItems() {
            CartEntity cart = CartEntityBuilder.aValidCartBuilder()
                    .cartItems(new ArrayList<>())
                    .build();

            CartResponseDto dto = mapper.mapFromEntityToResponseDto(cart);

            assertThat(dto.getItems()).isNotNull().isEmpty();
            assertThat(dto.getTotalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void shouldHandleNullUserEntity() {
            CartEntity cart = CartEntityBuilder.aValidCartBuilder()
                    .userEntity(null)
                    .build();

            CartResponseDto dto = mapper.mapFromEntityToResponseDto(cart);

            assertThat(dto.getUserUuid()).isNull();
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromEntityToResponseDto((CartEntity) null)).isNull();
        }

        @Test
        void shouldMapCollection() {
            Collection<CartResponseDto> dtos = mapper.mapFromEntityToResponseDto(
                    List.of(CartEntityBuilder.aValidCart(), CartEntityBuilder.aValidCart()));

            assertThat(dtos).hasSize(2);
        }

        @Test
        void shouldReturnNullForNullCollection() {
            assertThat(mapper.mapFromEntityToResponseDto((Collection<CartEntity>) null)).isNull();
        }
    }

    @Nested
    @DisplayName("mapFromCartItemEntityToResponseDto(CartItemEntity)")
    class ItemToResponse {

        @Test
        void shouldMapCartItemEntityWithAllFields() {
            ProductEntity product = ProductEntityBuilder.aValidProductBuilder()
                    .price(new BigDecimal("12.50"))
                    .name("widget")
                    .build();
            CartItemEntity item = CartItemEntityBuilder.aValidCartItemBuilder()
                    .productEntity(product)
                    .quantity(4)
                    .unitPrice(new BigDecimal("12.50"))
                    .build();

            CartItemResponseDto dto = mapper.mapFromCartItemEntityToResponseDto(item);

            assertThat(dto).isNotNull();
            assertThat(dto.getCartItemUuid()).isEqualTo(item.getUuid());
            assertThat(dto.getProductUuid()).isEqualTo(product.getUuid());
            assertThat(dto.getProductName()).isEqualTo("widget");
            // unitPrice on the response is sourced from productEntity.price (see mapper config),
            // not from cartItem.unitPrice.
            assertThat(dto.getUnitPrice()).isEqualByComparingTo("12.50");
            assertThat(dto.getQuantity()).isEqualTo(4);
            assertThat(dto.getLineItemTotalPrice()).isEqualByComparingTo("50.00");
        }

        @Test
        void shouldHandleNullUnitPriceGracefully() {
            // BUG-018 verification (F2 wave): the lineItemTotalPrice helper now null-guards
            // CartItemEntity#unitPrice and returns BigDecimal.ZERO instead of NPE-ing.
            CartItemEntity item = CartItemEntityBuilder.aValidCartItemBuilder()
                    .unitPrice(null)
                    .quantity(2)
                    .build();

            CartItemResponseDto dto = mapper.mapFromCartItemEntityToResponseDto(item);

            assertThat(dto.getLineItemTotalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void shouldHandleNullProductEntity() {
            CartItemEntity item = CartItemEntityBuilder.aValidCartItemBuilder()
                    .productEntity(null)
                    .build();

            CartItemResponseDto dto = mapper.mapFromCartItemEntityToResponseDto(item);

            assertThat(dto.getProductUuid()).isNull();
            assertThat(dto.getProductName()).isNull();
            assertThat(dto.getUnitPrice()).isNull();
        }

        @Test
        void shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromCartItemEntityToResponseDto(null)).isNull();
        }
    }

    @Nested
    @DisplayName("default helpers — lineItemTotalPrice / calculateTotalPrice")
    class Helpers {

        @Test
        void lineItemTotalPrice_shouldReturnZeroForNullEntity() {
            assertThat(mapper.lineItemTotalPrice(null)).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void lineItemTotalPrice_shouldReturnZeroForNullUnitPrice() {
            CartItemEntity item = CartItemEntityBuilder.aValidCartItemBuilder().unitPrice(null).build();
            assertThat(mapper.lineItemTotalPrice(item)).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void lineItemTotalPrice_shouldMultiplyUnitPriceByQuantity() {
            CartItemEntity item = CartItemEntityBuilder.aValidCartItemBuilder()
                    .unitPrice(new BigDecimal("3.00"))
                    .quantity(7)
                    .build();
            assertThat(mapper.lineItemTotalPrice(item)).isEqualByComparingTo("21.00");
        }

        @Test
        void calculateTotalPrice_shouldReturnZeroForNullList() {
            assertThat(mapper.calculateTotalPrice(null)).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void calculateTotalPrice_shouldReturnZeroForEmptyList() {
            assertThat(mapper.calculateTotalPrice(List.of())).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void calculateTotalPrice_shouldSumLineTotals() {
            CartItemEntity a = CartItemEntityBuilder.aValidCartItemBuilder()
                    .unitPrice(new BigDecimal("5.00")).quantity(2).build();
            CartItemEntity b = CartItemEntityBuilder.aValidCartItemBuilder()
                    .unitPrice(new BigDecimal("3.00")).quantity(4).build();

            assertThat(mapper.calculateTotalPrice(List.of(a, b)))
                    .isEqualByComparingTo("22.00");
        }
    }

    @Nested
    @DisplayName("BaseMapper-inherited surface (creation/update via DTO)")
    class BaseSurface {

        @Test
        void mapFromCreationRequestToEntity_shouldReturnEmptyEntity() {
            // CartMapper has no @Mapping for the create DTO; it just builds an empty CartEntity.
            CartCreateRequestDto dto = CartDtoFixtures.aValidCartCreateRequest();

            CartEntity entity = mapper.mapFromCreationRequestToEntity(dto);

            assertThat(entity).isNotNull();
            assertThat(entity.getCartItems()).isNull();
            assertThat(entity.getUserEntity()).isNull();
        }

        @Test
        void mapFromCreationRequestToEntity_shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromCreationRequestToEntity((CartCreateRequestDto) null)).isNull();
        }

        @Test
        void mapFromCreationRequestToEntity_shouldMapCollection() {
            Collection<CartEntity> entities = mapper.mapFromCreationRequestToEntity(
                    List.of(CartDtoFixtures.aValidCartCreateRequest()));

            assertThat(entities).hasSize(1);
        }

        @Test
        void mapFromUpdateRequestToEntity_shouldReturnEmptyEntity() {
            CartItemRemoveRequestDto u = CartDtoFixtures.aValidCartItemRemoveRequest();

            CartEntity entity = mapper.mapFromUpdateRequestToEntity(u);

            assertThat(entity).isNotNull();
        }

        @Test
        void mapFromUpdateRequestToEntity_shouldReturnNullForNullSource() {
            assertThat(mapper.mapFromUpdateRequestToEntity(null)).isNull();
        }
    }
}
