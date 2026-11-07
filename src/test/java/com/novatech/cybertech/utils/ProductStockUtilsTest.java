package com.novatech.cybertech.utils;

import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link ProductStockUtils}. */
class ProductStockUtilsTest {

    @ParameterizedTest(name = "stock={0}, reserved={1} → available {2}")
    @CsvSource(nullValues = "null", value = {
            "10, 3, 7",
            "5, 5, 0",
            "2, 4, 0",     // over-reservation never shows as negative
            "null, 0, 0",
            "4, null, 4",
            "null, null, 0",
            "0, 0, 0"
    })
    @DisplayName("availableStock = max(0, stock - reserved), null counted as 0")
    void availableStock(final Integer stock, final Integer reserved, final int expected) {
        final ProductEntity product = ProductEntityBuilder.aValidProductBuilder().stock(stock).reservedStock(reserved).build();

        assertThat(ProductStockUtils.availableStock(product)).isEqualTo(expected);
        assertThat(ProductStockUtils.isAvailable(product)).isEqualTo(expected > 0);
    }
}
