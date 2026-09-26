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
import static com.novatech.cybertech.entities.enums.ReservationStatus.COMMITTED;
import static com.novatech.cybertech.entities.enums.ReservationStatus.RELEASED;


/**
 * JPA + Redis-backed implementation of the {@link StockService} contract.
 *
 * <p>Reservation lifecycle managed here:
 * <pre>
 *     reserveStock()  → ACTIVE
 *     commitStock()   → ACTIVE → COMMITTED   (order paid: stock + reservedStock both decremented)
 *     releaseStock()  → ACTIVE → RELEASED    (cancellation / payment failure / refund)
 *     [TTL expiry]    → ACTIVE → EXPIRED     (Redis listener: see RedisExpirationListener)
 * </pre>
 *
 * <p>Each {@link ProductEntity} carries two columns: {@code stock} (the absolute warehouse
 * quantity) and {@code reservedStock} (units pinned to live reservations). The "available"
 * quantity used during reservation is {@code stock - reservedStock}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockServiceImp implements StockService {

    private final StockRepository stockRepository;
    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // How long stock stays reserved while we wait for the payment-confirmation webhook. Kept
    // short on purpose: we hold inventory at most 5 minutes for an unpaid order, then release it.
    // (Stripe's own delivery-retry window of up to 3 days is external and not configurable here.)
    private static final Duration RESERVATION_TTL = Duration.ofMinutes(5);


    /**
     * {@inheritDoc}
     *
     * <p>Implementation notes:
     * <ul>
     *   <li>Idempotent: a retried call for an order that already has a reservation refreshes the
     *       Redis TTL and returns. Rebuilding would open a concurrency window where another
     *       order could grab the freed stock between release and re-lock.</li>
     *   <li>Products are locked in canonical order (sorted by {@link UUID#toString()}
     *       via {@link TreeMap}) so two concurrent reservations covering an overlapping product
     *       set always acquire DB row locks in the same order — eliminating A→B / B→A
     *       deadlocks.</li>
     *   <li>The Redis sentinel key is written ONLY after every per-product reservation has
     *       succeeded; a partial-batch failure leaves no orphan Redis key behind.</li>
     * </ul>
     */
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

        // Canonical lock order across all callers prevents A-B / B-A deadlocks on
        // concurrent orders that touch the same product set in different input order.
        final Map<UUID, Integer> ordered = new TreeMap<>(Comparator.comparing(UUID::toString));
        ordered.putAll(quantities);
        ordered.entrySet().forEach(entry -> lockAndReserveProduct(orderUuid, entry));
        redisTemplate.opsForValue().set(reservationKey(orderUuid), ACTIVE.name(), RESERVATION_TTL);

        log.info("Reserved successfully stock for order {}", orderUuid);
    }


    /**
     * {@inheritDoc}
     *
     * <p>Reservation rows are flipped {@code ACTIVE -> COMMITTED} and kept (not deleted): a
     * later cancellation/refund on the now-PAID order still needs to know which product/quantity
     * pairs were committed so {@link #releaseStock(UUID)} can restore {@code product.stock}. Only
     * {@code releaseStock} deletes the rows, once the reservation reaches a terminal state.
     *
     * <p>Each row is skipped unless still {@code ACTIVE} — same double-commit guard
     * {@link #releaseStock(UUID)} already has, for the same reason: a replayed/duplicate
     * payment-success webhook racing another commit on the same order must not decrement
     * {@code stock}/{@code reservedStock} twice for the same reservation.
     *
     * <p>When called for an order with no active reservation we log a WARN with the offending
     * {@code orderUuid} so an upstream double-commit (or a webhook replay landing after the order
     * was cancelled) is observable in logs.
     */
    @Override
    @Transactional
    public void commitStock(UUID orderUuid) {

        log.info("Starting stock commit for order {}", orderUuid);

        final List<StockEntity> reservations = stockRepository.findByOrderUuid(orderUuid);

        if (reservations.isEmpty()) {
            // Silent no-op used to hide upstream double-commit bugs. The WARN
            // does NOT change behaviour — cleanup steps still run and stay no-ops.
            log.warn("commitStock called but no ACTIVE reservation for order={}", orderUuid);
        }

        reservations.stream()
                .filter(r -> r.getReservationStatus() == ACTIVE)
                .forEach(this::updateStockForCommit);

        redisTemplate.delete(reservationKey(orderUuid));

        log.info("Stock successfully committed for order {}", orderUuid);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Idempotent: empty reservation set short-circuits before any DB or Redis writes — the
     * cleanup batch and the Redis-expiration listener can both call this safely.
     *
     * <p>Handles both reservation lifecycle stages a caller may need to unwind:
     * <ul>
     *   <li>{@code ACTIVE} — never committed (payment failed/expired/order cancelled pre-payment).
     *       Only {@code reservedStock} was ever touched, so only it is reverted.</li>
     *   <li>{@code COMMITTED} — the order reached {@code PAID} and {@link #commitStock(UUID)}
     *       already decremented both {@code stock} and {@code reservedStock}. Cancelling or
     *       refunding such an order must return the units to {@code stock} (the warehouse count);
     *       {@code reservedStock} is not touched again since commit already zeroed it out for
     *       this reservation. This is what lets cancelling/editing a PAID order actually restore
     *       inventory instead of leaking it.</li>
     * </ul>
     *
     * <p>Each row is also skipped unless it's still {@code ACTIVE} or {@code COMMITTED} — mirrors
     * the same guard {@link com.novatech.cybertech.listener.RedisExpirationListener} already has,
     * for the same reason: {@code releaseStock} is called from several independent paths (the
     * cleanup batch, the payment-confirmation listener, order cancellation/update) that can race
     * each other or the Redis listener on the same order. Without this guard, two callers both
     * reading a row as live before either commits would both decrement stock for the same
     * reservation, corrupting the accounting. Rows are flipped to {@code RELEASED} (not deleted
     * individually) before the final bulk delete, so a same-transaction re-read would see the
     * terminal state.
     */
    @Override
    @Transactional
    public void releaseStock(UUID orderUuid) {

        log.info("Starting releasing stock for order {}", orderUuid);

        final List<StockEntity> reservations = stockRepository.findByOrderUuid(orderUuid);

        if (reservations.isEmpty()) return;

        reservations.stream()
                .filter(r -> r.getReservationStatus() == ACTIVE || r.getReservationStatus() == COMMITTED)
                .forEach(this::updateStockForRelease);

        stockRepository.deleteByOrderUuid(orderUuid);
        redisTemplate.delete(reservationKey(orderUuid));

        log.info("Stock successfully released for order {}", orderUuid);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Same DB-level work as calling {@link #releaseStock(UUID)} then {@link #reserveStock}
     * back to back, but the Redis sentinel is touched exactly ONCE, at the very end, only after
     * every DB write (the release AND every new per-product reservation) has succeeded. This
     * matters because a plain {@code releaseStock()} immediately deletes the Redis TTL key —
     * that delete is not transactional, so if a LATER step in the caller's transaction throws
     * (e.g. {@link com.novatech.cybertech.exceptions.NotEnoughStockException} on one of the new
     * products) and the transaction rolls back, the DB reservation rows revert to their pre-release
     * state but the Redis key stays deleted — silently disabling that order's 5-minute
     * auto-expiry safety net. Deferring the Redis write here closes that window.
     */
    @Override
    @Transactional
    public void resizeReservation(UUID orderUuid, Map<UUID, Integer> newQuantities) {

        log.info("Resizing stock reservation for order {}", orderUuid);

        final List<StockEntity> existing = stockRepository.findByOrderUuid(orderUuid);
        existing.stream()
                .filter(r -> r.getReservationStatus() == ACTIVE || r.getReservationStatus() == COMMITTED)
                .forEach(this::updateStockForRelease);
        stockRepository.deleteByOrderUuid(orderUuid);

        final Map<UUID, Integer> ordered = new TreeMap<>(Comparator.comparing(UUID::toString));
        ordered.putAll(newQuantities);
        ordered.entrySet().forEach(entry -> lockAndReserveProduct(orderUuid, entry));

        redisTemplate.opsForValue().set(reservationKey(orderUuid), ACTIVE.name(), RESERVATION_TTL);

        log.info("Resized stock reservation for order {}", orderUuid);
    }

    /**
     * Builds the canonical Redis sentinel key {@code reservation:order:&lt;uuid&gt;} for the
     * given order. Used as both the value and TTL key tracked by
     * {@link com.novatech.cybertech.listener.RedisExpirationListener}.
     */
    private static String reservationKey(UUID orderUuid) {
        return RESERVATION_KEY_PREFIX + orderUuid;
    }


    /**
     * Reverts the effect of a single reservation row and flips it to
     * {@link com.novatech.cybertech.entities.enums.ReservationStatus#RELEASED}. Called from
     * {@link #releaseStock(UUID)} only for rows already filtered to {@code ACTIVE}/{@code
     * COMMITTED}; takes the per-product DB row lock to serialize against concurrent reservations
     * on the same product.
     *
     * <p>An {@code ACTIVE} row was never committed, so only {@code reservedStock} is reverted. A
     * {@code COMMITTED} row already had {@code stock} physically decremented by
     * {@link #commitStock(UUID)}, so releasing it must add the quantity back to {@code stock}
     * instead — {@code reservedStock} is left alone since commit already zeroed out this
     * reservation's contribution to it.
     */
    private void updateStockForRelease(StockEntity r) {
        final ProductEntity product = productRepository.lockByUuid(r.getProductUuid())
                .orElseThrow(() -> new ProductNotFoundException("No product with the UUID : " + r.getProductUuid() + " found"));
        if (r.getReservationStatus() == COMMITTED) {
            product.setStock(product.getStock() + r.getQuantity());
        } else {
            product.setReservedStock(product.getReservedStock() - r.getQuantity());
        }
        productRepository.save(product);
        r.setReservationStatus(RELEASED);
        stockRepository.save(r);
    }

    /**
     * Decrements both {@code stock} and {@code reservedStock} on the product side for a single
     * reservation row and flips it to
     * {@link com.novatech.cybertech.entities.enums.ReservationStatus#COMMITTED}. Called from
     * {@link #commitStock(UUID)}; the row lock guards against concurrent commits/releases on the
     * same product.
     */
    private void updateStockForCommit(StockEntity r) {
        final ProductEntity product = productRepository.lockByUuid(r.getProductUuid()).orElseThrow(() -> new ProductNotFoundException("No product with the UUID : " + r.getProductUuid() + " found"));
        product.setReservedStock(product.getReservedStock() - r.getQuantity());
        product.setStock(product.getStock() - r.getQuantity());
        productRepository.save(product);

        r.setReservationStatus(COMMITTED);
        stockRepository.save(r);
    }

    /**
     * Locks the product row, validates available stock, persists the per-product
     * {@link StockEntity}, and increments the product's {@code reservedStock}. The lock is held
     * until the surrounding {@link Transactional} commits.
     *
     * @throws IllegalArgumentException when {@code qty &lt;= 0}
     * @throws ProductNotFoundException when the product cannot be located
     * @throws NotEnoughStockException  when {@code stock - reservedStock &lt; qty}
     */
    private void lockAndReserveProduct(UUID orderUuid, Map.Entry<UUID, Integer> entry) {
        final UUID productUuid = entry.getKey();
        final int qty = entry.getValue();

        if (qty <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be > 0 for product " + productUuid + " (got " + qty + ")");
        }

        final ProductEntity product = productRepository.lockByUuid(productUuid).orElseThrow(() -> new ProductNotFoundException("Product " + productUuid + " not found"));

        final int available = product.getStock() - product.getReservedStock();
        if (available < qty) {
            throw new NotEnoughStockException("Not enough stock for product " + productUuid + ": requested=" + qty + ", available=" + available);
        }

        final StockEntity reservation = StockEntity.builder()
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
