package com.novatech.cybertech.integration.stock;

import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.support.AbstractIntegrationTest;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.StockRepository;
import com.novatech.cybertech.services.core.StockService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.RESERVATION_KEY_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end reservation expiry against real Redis keyspace notifications + MySQL.
 *
 * <p>Regression for the stock leak: {@code RedisExpirationListener} was registered twice (its own
 * self-subscription + a manual one in {@code AppConfig}) and invoked raw, outside any transaction —
 * the product row lock threw {@code TransactionRequiredException} after the reservation row was
 * already saved EXPIRED, so {@code reservedStock} was never given back (observed: stays at 3).
 */
class ReservationExpiryIT extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private StockService stockService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void expiredUnpaidReservation_givesReservedUnitsBackAndDeletesRows() {
        final ProductEntity product = ProductEntityBuilder.aValidProductBuilder()
                .name("Expiry product " + UUID.randomUUID())
                .stock(10)
                .reservedStock(0)
                .build();
        productRepository.save(product);
        // No order row: an unknown order is treated as unpaid, which is the path under test.
        final UUID orderUuid = UUID.randomUUID();

        stockService.reserveStock(orderUuid, Map.of(product.getUuid(), 3));
        assertThat(productRepository.findByUuid(product.getUuid()).orElseThrow().getReservedStock()).isEqualTo(3);

        // Shorten the 5-minute TTL so Redis fires the expiry event right away.
        redisTemplate.expire(RESERVATION_KEY_PREFIX + orderUuid, Duration.ofSeconds(1));

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(productRepository.findByUuid(product.getUuid()).orElseThrow().getReservedStock()).isZero();
            assertThat(stockRepository.findByOrderUuid(orderUuid)).isEmpty();
        });
        // Warehouse stock itself is untouched — the units were never sold.
        assertThat(productRepository.findByUuid(product.getUuid()).orElseThrow().getStock()).isEqualTo(10);
    }
}
