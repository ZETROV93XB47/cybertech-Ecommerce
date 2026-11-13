package com.novatech.cybertech.listener;

import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.services.core.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.RESERVATION_KEY_PREFIX;

/**
 * Redis Pub/Sub listener that reacts to keyspace expiration events for stock-reservation keys.
 *
 * <p>When a {@code reservation:order:&lt;uuid&gt;} key TTLs out (the payment was not confirmed in
 * time), the reservation is unwound through {@link StockService#expireReservation(UUID)} — or
 * committed through {@link StockService#commitStock(UUID)} if the order turns out to be paid.
 *
 * <p><b>Registration:</b> this bean subscribes ITSELF to {@code __keyevent@*__:expired} —
 * {@link KeyExpirationEventMessageListener#afterPropertiesSet()} calls {@code init()}, which calls
 * {@code container.addMessageListener(this, ...)}. It must NOT also be added by hand to the
 * container ({@code AppConfig} used to), or every expiry is handled twice concurrently.
 *
 * <p><b>No DB work here, and no {@code @Transactional}:</b> the container invokes the raw
 * {@code this} it registered, never the Spring proxy, so a {@code @Transactional} on
 * {@link #onMessage} is silently ignored. It used to be: the product-row lock
 * ({@code SELECT ... FOR UPDATE}) then ran outside any transaction and threw
 * {@code TransactionRequiredException} right after the reservation row had been saved
 * {@code EXPIRED}, so {@code reservedStock} was never given back — the units stayed blocked
 * forever on every unpaid order. All the work now happens behind {@link StockService}'s
 * transactional proxy.
 */
@Slf4j
@Component
public class RedisExpirationListener extends KeyExpirationEventMessageListener {

    private final OrderRepository orderRepository;
    private final StockService stockService;

    public RedisExpirationListener(RedisMessageListenerContainer container,
                                   OrderRepository orderRepository,
                                   StockService stockService) {
        super(container);
        this.orderRepository = orderRepository;
        this.stockService = stockService;
    }

    /**
     * Handles a Redis key-expiration event.
     *
     * <ul>
     *   <li>Early-returns for keys not starting with {@link
     *       com.novatech.cybertech.constants.CyberTechAppConstants#RESERVATION_KEY_PREFIX}.</li>
     *   <li>A malformed UUID tail is logged at WARN and ignored instead of surfacing as an
     *       {@link IllegalArgumentException} swallowed by the listener container.</li>
     *   <li>Order already paid (the webhook won the race with the TTL): commit the stock instead
     *       of releasing it, otherwise the same units could be sold twice.</li>
     * </ul>
     *
     * @param message the Redis message whose body is the expired key
     * @param pattern the subscription pattern (unused)
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {

        final String key = message.toString();
        if (!key.startsWith(RESERVATION_KEY_PREFIX)) return;

        final UUID orderUuid;
        try {
            orderUuid = UUID.fromString(key.substring(RESERVATION_KEY_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            log.warn("Ignoring malformed reservation expiration key '{}': {}", key, ex.getMessage());
            return;
        }

        log.warn("Reservation expired for order {}", orderUuid);

        final boolean alreadyPaid = orderRepository.findByUuid(orderUuid)
                .map(order -> order.getStatus().getCode() >= OrderStatus.PAID.getCode())
                .orElse(false);
        if (alreadyPaid) {
            log.warn("Reservation for order {} expired but the order is already paid — committing stock instead of releasing it.", orderUuid);
            stockService.commitStock(orderUuid);
            return;
        }

        stockService.expireReservation(orderUuid);
    }
}
