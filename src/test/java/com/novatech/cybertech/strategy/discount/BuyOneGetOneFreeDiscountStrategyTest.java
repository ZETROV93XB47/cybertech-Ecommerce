package com.novatech.cybertech.strategy.discount;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.dto.request.order.OrderItemPriceDto;
import com.novatech.cybertech.entities.enums.DiscountCalculationType;
import com.novatech.cybertech.entities.enums.DiscountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BuyOneGetOneFreeDiscountStrategyTest {

    private final BuyOneGetOneFreeDiscountStrategy strategy = new BuyOneGetOneFreeDiscountStrategy();

    private final DiscountContext context = DiscountContext.builder()
            .discountType(DiscountType.BUY_ONE_GET_ONE_FREE)
            .calculationType(DiscountCalculationType.BUY_ONE_GET_ONE_FREE)
            .build();

    private OrderItemPriceDto item(final BigDecimal unitPrice, final int quantity) {
        return OrderItemPriceDto.builder()
                .productUuid(UUID.randomUUID())
                .unitPrice(unitPrice)
                .quantity(quantity)
                .build();
    }

    @Test
    @DisplayName("Single line, qty=2 @ 10.00 => 10.00 discount (one free)")
    void twoOfOneItem() {
        final BigDecimal result = strategy.calculateDiscount(
                new BigDecimal("20.00"),
                List.of(item(new BigDecimal("10.00"), 2)),
                context);
        assertThat(result).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("Single line, qty=3 @ 10.00 => 10.00 discount (one free, one paid)")
    void threeOfOneItem_oddQty() {
        final BigDecimal result = strategy.calculateDiscount(
                new BigDecimal("30.00"),
                List.of(item(new BigDecimal("10.00"), 3)),
                context);
        assertThat(result).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("Single line, qty=1 => 0 discount")
    void oneItem_noFree() {
        final BigDecimal result = strategy.calculateDiscount(
                new BigDecimal("10.00"),
                List.of(item(new BigDecimal("10.00"), 1)),
                context);
        assertThat(result).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Two lines, qty=4 each @ 5.00 => 20.00 discount")
    void twoLines_evenQty() {
        final BigDecimal result = strategy.calculateDiscount(
                new BigDecimal("40.00"),
                List.of(item(new BigDecimal("5.00"), 4), item(new BigDecimal("5.00"), 4)),
                context);
        assertThat(result).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("Empty items => 0 discount")
    void emptyItems() {
        final BigDecimal result = strategy.calculateDiscount(
                BigDecimal.ZERO, List.of(), context);
        assertThat(result).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Null items => 0 discount (defensive)")
    void nullItems() {
        final BigDecimal result = strategy.calculateDiscount(
                BigDecimal.ZERO, null, context);
        assertThat(result).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("maxDiscountAmount caps the BOGO discount")
    void respectsMaxCap() {
        final DiscountContext capped = DiscountContext.builder()
                .discountType(DiscountType.BUY_ONE_GET_ONE_FREE)
                .calculationType(DiscountCalculationType.BUY_ONE_GET_ONE_FREE)
                .maxDiscountAmount(new BigDecimal("3.00"))
                .build();

        final BigDecimal result = strategy.calculateDiscount(
                new BigDecimal("20.00"),
                List.of(item(new BigDecimal("10.00"), 2)),
                capped);
        assertThat(result).isEqualByComparingTo("3.00");
    }
}
