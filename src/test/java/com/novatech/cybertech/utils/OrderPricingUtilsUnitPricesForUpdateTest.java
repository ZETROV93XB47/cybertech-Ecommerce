package com.novatech.cybertech.utils;

import com.novatech.cybertech.entities.OrderItemEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.fixtures.builders.OrderItemEntityBuilder;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link OrderPricingUtils#unitPricesForUpdate}. */
class OrderPricingUtilsUnitPricesForUpdateTest {

    private static final BigDecimal ORDERED_PRICE = new BigDecimal("100.00");
    private static final BigDecimal CURRENT_PRICE = new BigDecimal("120.00");
    private static final int ORDERED_QUANTITY = 2;

    private static ProductEntity product() {
        return ProductEntityBuilder.aValidProductBuilder().price(CURRENT_PRICE).build();
    }

    private static OrderItemEntity orderedLine(final ProductEntity product, final BigDecimal unitPrice) {
        return OrderItemEntityBuilder.aValidOrderItemBuilder()
                .productEntity(product).quantity(ORDERED_QUANTITY).unitPrice(unitPrice).build();
    }

    @ParameterizedTest(name = "ordered 2 at 100, now {0} -> {1}")
    @CsvSource({
            "1, 100.00",   // lower quantity: price paid
            "2, 100.00",   // same quantity: price paid
            "3, 120.00"    // higher quantity: current price for the whole line
    })
    @DisplayName("an ordered line keeps its price unless its quantity goes up")
    void orderedLine(final int newQuantity, final String expected) {
        final ProductEntity product = product();

        final Map<UUID, BigDecimal> prices = OrderPricingUtils.unitPricesForUpdate(
                List.of(orderedLine(product, ORDERED_PRICE)), List.of(product), Map.of(product.getUuid(), newQuantity));

        assertThat(prices.get(product.getUuid())).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("a product added to the order is priced at the current price")
    void newProduct() {
        final ProductEntity ordered = product();
        final ProductEntity added = ProductEntityBuilder.aValidProductBuilder().price(new BigDecimal("49.90")).build();

        final Map<UUID, BigDecimal> prices = OrderPricingUtils.unitPricesForUpdate(
                List.of(orderedLine(ordered, ORDERED_PRICE)), List.of(ordered, added),
                Map.of(ordered.getUuid(), ORDERED_QUANTITY, added.getUuid(), 1));

        assertThat(prices.get(ordered.getUuid())).isEqualByComparingTo(ORDERED_PRICE);
        assertThat(prices.get(added.getUuid())).isEqualByComparingTo("49.90");
    }

    @Test
    @DisplayName("an ordered line without a stored price falls back to the current price")
    void orderedLineWithoutPrice() {
        final ProductEntity product = product();

        final Map<UUID, BigDecimal> prices = OrderPricingUtils.unitPricesForUpdate(
                List.of(orderedLine(product, null)), List.of(product), Map.of(product.getUuid(), 1));

        assertThat(prices.get(product.getUuid())).isEqualByComparingTo(CURRENT_PRICE);
    }
}
