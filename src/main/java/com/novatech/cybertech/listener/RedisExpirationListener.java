package com.novatech.cybertech.listener;

import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.StockEntity;
import com.novatech.cybertech.entities.enums.ReservationStatus;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.StockRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.RESERVATION_KEY_PREFIX;

/**
 * Redis Pub/Sub listener that reacts to keyspace expiration events for stock-reservation keys.
 *
 * <p>When a {@code reservation:order:&lt;uuid&gt;} key TTLs out, this listener walks every
 * {@link StockEntity} for that order, flips its {@link ReservationStatus} from
 * {@link ReservationStatus#ACTIVE ACTIVE} to {@link ReservationStatus#EXPIRED EXPIRED},
 * decrements the matching {@link ProductEntity#getReservedStock() product.reservedStock},
 * then deletes the reservation rows in a single batch.
 *
 * <p>The transition to {@code EXPIRED} (rather than direct deletion before the per-product
 * release) is important to avoid double-release when both this listener and the cleanup
 * batch race to clean up the same expired reservation.
 */
@Slf4j
@Component
public class RedisExpirationListener extends KeyExpirationEventMessageListener {

    private final StockRepository stockRepository;
    private final ProductRepository productRepository;

    public RedisExpirationListener(RedisMessageListenerContainer container,
                                   StockRepository stockRepository,
                                   ProductRepository productRepository) {
        super(container);
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
    }

    /**
     * Handles a Redis key-expiration event.
     *
     * <p>Implementation notes:
     * <ul>
     *   <li>Early-returns when the expired key does not start with {@link
     *       com.novatech.cybertech.constants.CyberTechAppConstants#RESERVATION_KEY_PREFIX}, so we
     *       only do work for keys we own.</li>
     *   <li>Wraps {@link UUID#fromString(String)} in a try/catch (BUG-121 fix). A malformed key
     *       tail used to surface as an {@link IllegalArgumentException} swallowed by the Redis
     *       listener container, silently dropping the event. We now log a WARN and return early
     *       so the malformed key is observable in logs but does not crash the listener loop.</li>
     *   <li>Skips reservations not in {@link ReservationStatus#ACTIVE} state (e.g. already
     *       {@code EXPIRED}/{@code RELEASED} by a competing path) to keep the operation
     *       idempotent.</li>
     * </ul>
     *
     * @param message the Redis message whose body is the expired key
     * @param pattern the subscription pattern (unused)
     */
    @Override
    @Transactional
    public void onMessage(Message message, byte[] pattern) {

        final String key = message.toString();
        if (!key.startsWith(RESERVATION_KEY_PREFIX)) return;

        final UUID orderUuid;
        try {
            orderUuid = UUID.fromString(key.substring(RESERVATION_KEY_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            // BUG-121: a malformed UUID tail used to bubble up as IllegalArgumentException and be
            // swallowed by the Redis listener container, losing the event entirely. We log it now
            // so the bad key is visible in logs and return early without further work.
            log.warn("Ignoring malformed reservation expiration key '{}': {}", key, ex.getMessage());
            return;
        }

        log.warn("Reservation expired for order {}", orderUuid);

        final List<StockEntity> reservations = stockRepository.findByOrderUuid(orderUuid);
        if (reservations.isEmpty()) return;

        for (StockEntity r : reservations) {
            if (r.getReservationStatus() != ReservationStatus.ACTIVE) continue;
            // Mark as EXPIRED to avoid a double release between this listener and the cleanup batch.
            r.setReservationStatus(ReservationStatus.EXPIRED);
            stockRepository.save(r);

            final ProductEntity product = productRepository.lockByUuid(r.getProductUuid())
                    .orElseThrow(() -> new ProductNotFoundException("No product with the UUID : " + r.getProductUuid() + " found"));

            product.setReservedStock(product.getReservedStock() - r.getQuantity());
            productRepository.save(product);
        }
        // Bulk delete keeps the WAL/journal small versus per-row deletes.
        stockRepository.deleteByOrderUuid(orderUuid);
    }
}
