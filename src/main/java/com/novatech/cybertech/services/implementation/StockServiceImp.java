package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.StockEntity;
import com.novatech.cybertech.exceptions.NotEnoughStockException;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.StockRepository;
import com.novatech.cybertech.services.core.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.RESERVATION_KEY_PREFIX;
import static com.novatech.cybertech.entities.enums.ReservationStatus.ACTIVE;


@Slf4j
@Service
@RequiredArgsConstructor
public class StockServiceImp implements StockService {

    private final StockRepository stockRepository;
    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final Duration RESERVATION_TTL = Duration.ofMinutes(10);


    @Override
    @Transactional
    public void reserveStock(UUID orderUuid, Map<UUID, Integer> quantities) {
        log.info("Starting stock Reservation for order {}", orderUuid);

        // A retried reserveStock must be idempotent: if a reservation already exists,
        // just refresh the Redis TTL. Rebuilding would open a concurrency window
        // where another order could grab the freed stock between release and re-lock.
        if (!stockRepository.findByOrderUuid(orderUuid).isEmpty()) {
            log.info("Existing reservation found for order {}, refreshing TTL.", orderUuid);
            redisTemplate.expire(reservationKey(orderUuid), RESERVATION_TTL);
            return;
        }

        // Canonical lock order across all callers to prevent A-B / B-A deadlocks on concurrent orders.
        final Map<UUID, Integer> ordered = new TreeMap<>(Comparator.comparing(UUID::toString));
        ordered.putAll(quantities);
        ordered.entrySet().forEach(entry -> lockAndReserveProduct(orderUuid, entry));
        redisTemplate.opsForValue().set(reservationKey(orderUuid), ACTIVE.name(), RESERVATION_TTL);

        log.info("Reserved successfully stock for order {}", orderUuid);
    }


    @Override
    @Transactional
    public void commitStock(UUID orderUuid) {

        log.info("Starting stock commit for order {}", orderUuid);

        final List<StockEntity> reservations = stockRepository.findByOrderUuid(orderUuid);

        reservations.stream().map(this::updateStockForCommit).forEach(productRepository::save);

        stockRepository.deleteByOrderUuid(orderUuid);

        redisTemplate.delete(reservationKey(orderUuid));

        log.info("Stock successfully committed for order {}", orderUuid);
    }

    @Override
    @Transactional
    public void releaseStock(UUID orderUuid) {

        log.info("Starting releasing stock for order {}", orderUuid);

        List<StockEntity> reservations = stockRepository.findByOrderUuid(orderUuid);

        if (reservations.isEmpty()) return;

        reservations.forEach(this::updateStockForRelease);

        stockRepository.deleteByOrderUuid(orderUuid);
        redisTemplate.delete(reservationKey(orderUuid));

        log.info("Stock successfully released for order {}", orderUuid);
    }

    private static String reservationKey(UUID orderUuid) {
        return RESERVATION_KEY_PREFIX + orderUuid;
    }


    private void updateStockForRelease(StockEntity r) {
        ProductEntity product = productRepository.lockByUuid(r.getProductUuid())
                .orElseThrow(() -> new ProductNotFoundException("No product with the UUID : " + r.getProductUuid() + " found"));
        product.setReservedStock(product.getReservedStock() - r.getQuantity());
        productRepository.save(product);
    }

    private ProductEntity updateStockForCommit(StockEntity r) {
        final ProductEntity product = productRepository.lockByUuid(r.getProductUuid()).orElseThrow(() -> new ProductNotFoundException("No product with the UUID : " + r.getProductUuid() + " found"));
        product.setReservedStock(product.getReservedStock() - r.getQuantity());
        product.setStock(product.getStock() - r.getQuantity());

        return product;
    }

    private void lockAndReserveProduct(UUID orderUuid, Map.Entry<UUID, Integer> entry) {
        UUID productUuid = entry.getKey();
        int qty = entry.getValue();

        if (qty <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be > 0 for product " + productUuid + " (got " + qty + ")");
        }

        ProductEntity product = productRepository.lockByUuid(productUuid).orElseThrow(() -> new ProductNotFoundException("Product " + productUuid + " not found"));

        int available = product.getStock() - product.getReservedStock();
        if (available < qty) {
            throw new NotEnoughStockException("Not enough stock for product " + productUuid + ": requested=" + qty + ", available=" + available);
        }

        StockEntity reservation = StockEntity.builder()
                .orderUuid(orderUuid)
                .productUuid(productUuid)
                .reservationStatus(ACTIVE)
                .quantity(qty)
                .build();

        stockRepository.save(reservation);

        product.setReservedStock(product.getReservedStock() + qty);
        productRepository.save(product);
    }

}
