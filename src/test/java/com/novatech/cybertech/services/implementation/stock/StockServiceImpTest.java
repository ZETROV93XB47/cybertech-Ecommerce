package com.novatech.cybertech.services.implementation.stock;

import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.StockEntity;
import com.novatech.cybertech.entities.enums.ReservationStatus;
import com.novatech.cybertech.exceptions.NotEnoughStockException;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.StockEntityBuilder;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.StockRepository;
import com.novatech.cybertech.services.implementation.StockServiceImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link StockServiceImp}.
 *
 * <p>Subagent SA-W3.3 — services/stock. See progress.md for the full coverage matrix and
 * bug pinning conventions. Production code is read-only: when bugs are confirmed they are pinned
 * to their original BUG-### numbers (BUG-060..BUG-064) via {@code @Disabled}.
 */
@ExtendWith(MockitoExtension.class)
class StockServiceImpTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    private StockServiceImp service;

    private static final Duration EXPECTED_TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "reservation:order:";

    @BeforeEach
    void setUp() {
        // Lenient because some tests (release/commit/missing-product) don't touch opsForValue.
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new StockServiceImp(stockRepository, productRepository, redisTemplate);
    }

    private ProductEntity productWithStock(final UUID uuid, final int stock, final int reserved) {
        return ProductEntityBuilder.aValidProductBuilder()
                .uuid(uuid)
                .stock(stock)
                .reservedStock(reserved)
                .build();
    }

    private StockEntity reservation(final UUID orderUuid, final UUID productUuid, final int qty) {
        return StockEntityBuilder.aValidStockBuilder()
                .orderUuid(orderUuid)
                .productUuid(productUuid)
                .quantity(qty)
                .reservationStatus(ReservationStatus.ACTIVE)
                .build();
    }

    // ---------------------------------------------------------------------
    // reserveStock — single-product happy + edges
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("reserveStock: single product happy path — locks, reserves, persists Redis with TTL/key/value")
    void reserveStock_singleProductHappyPath() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        ProductEntity product = productWithStock(productUuid, 10, 0);

        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());
        when(productRepository.lockByUuid(productUuid)).thenReturn(Optional.of(product));

        service.reserveStock(orderUuid, Map.of(productUuid, 3));

        ArgumentCaptor<StockEntity> reservationCaptor = ArgumentCaptor.forClass(StockEntity.class);
        verify(stockRepository).save(reservationCaptor.capture());
        StockEntity savedReservation = reservationCaptor.getValue();
        assertThat(savedReservation.getOrderUuid()).isEqualTo(orderUuid);
        assertThat(savedReservation.getProductUuid()).isEqualTo(productUuid);
        assertThat(savedReservation.getQuantity()).isEqualTo(3);
        assertThat(savedReservation.getReservationStatus()).isEqualTo(ReservationStatus.ACTIVE);

        ArgumentCaptor<ProductEntity> productCaptor = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productRepository).save(productCaptor.capture());
        assertThat(productCaptor.getValue().getReservedStock()).isEqualTo(3);

        verify(valueOps).set(eq(KEY_PREFIX + orderUuid), eq(ReservationStatus.ACTIVE.name()), eq(EXPECTED_TTL));
    }

    @Test
    @DisplayName("reserveStock: shortfall raises NotEnoughStockException — message must be actionable (BUG-061 verify)")
    void reserveStock_notEnough_messageIsActionable_bug061Verify() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        ProductEntity product = productWithStock(productUuid, 5, 3); // available = 2

        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());
        when(productRepository.lockByUuid(productUuid)).thenReturn(Optional.of(product));

        // BUG-061 fix verification (per F2 wave): message MUST include productUuid + requested + available.
        assertThatThrownBy(() -> service.reserveStock(orderUuid, Map.of(productUuid, 5)))
                .isInstanceOf(NotEnoughStockException.class)
                .hasMessageContaining(productUuid.toString())
                .hasMessageContaining("5")  // requested
                .hasMessageContaining("2"); // available

        verify(stockRepository, never()).save(any());
        verify(valueOps, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("reserveStock: respects pre-existing reservedStock when computing availability")
    void reserveStock_respectsExistingReservedStock() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        // stock 10, already reserved 4 ⇒ available = 6
        ProductEntity product = productWithStock(productUuid, 10, 4);

        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());
        when(productRepository.lockByUuid(productUuid)).thenReturn(Optional.of(product));

        service.reserveStock(orderUuid, Map.of(productUuid, 6));

        ArgumentCaptor<ProductEntity> productCaptor = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productRepository).save(productCaptor.capture());
        assertThat(productCaptor.getValue().getReservedStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("reserveStock: requesting exactly the available stock succeeds (boundary)")
    void reserveStock_exactBoundarySucceeds() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        ProductEntity product = productWithStock(productUuid, 7, 2); // available = 5

        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());
        when(productRepository.lockByUuid(productUuid)).thenReturn(Optional.of(product));

        service.reserveStock(orderUuid, Map.of(productUuid, 5));

        verify(stockRepository).save(any(StockEntity.class));
        verify(valueOps).set(eq(KEY_PREFIX + orderUuid), eq(ReservationStatus.ACTIVE.name()), eq(EXPECTED_TTL));
    }

    @Test
    @DisplayName("reserveStock: missing product raises ProductNotFoundException with productUuid in message")
    void reserveStock_missingProduct_throwsProductNotFound() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();

        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());
        when(productRepository.lockByUuid(productUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserveStock(orderUuid, Map.of(productUuid, 1)))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining(productUuid.toString());

        verify(stockRepository, never()).save(any());
        verify(valueOps, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("reserveStock: re-reserve when reservation already exists — refresh TTL only, no double-reserve")
    void reserveStock_idempotentRefreshTtlOnly() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        StockEntity existing = reservation(orderUuid, productUuid, 3);

        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(List.of(existing));

        service.reserveStock(orderUuid, Map.of(productUuid, 3));

        verify(redisTemplate).expire(KEY_PREFIX + orderUuid, EXPECTED_TTL);
        verify(productRepository, never()).lockByUuid(any());
        verify(stockRepository, never()).save(any());
        verify(valueOps, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("reserveStock: partial batch failure (second product short) — Redis ACTIVE key NOT written")
    void reserveStock_partialBatchFailure_doesNotWriteRedisKey() {
        UUID orderUuid = UUID.randomUUID();
        UUID productOk = UUID.randomUUID();
        UUID productShort = UUID.randomUUID();
        ProductEntity okProduct = productWithStock(productOk, 100, 0);
        ProductEntity shortProduct = productWithStock(productShort, 1, 0);

        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());
        // HashMap iteration order is non-deterministic, so either lock may or may not be hit
        // before the NotEnoughStock short-circuit. Use lenient stubs to tolerate either order.
        lenient().when(productRepository.lockByUuid(productOk)).thenReturn(Optional.of(okProduct));
        lenient().when(productRepository.lockByUuid(productShort)).thenReturn(Optional.of(shortProduct));

        Map<UUID, Integer> qty = new HashMap<>();
        qty.put(productOk, 1);
        qty.put(productShort, 5);

        assertThatThrownBy(() -> service.reserveStock(orderUuid, qty))
                .isInstanceOf(NotEnoughStockException.class);

        verify(valueOps, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("BUG-062: qty=-5 rejected with IllegalArgumentException (verify F2 fix)")
    void reserveStock_negativeQty_rejected_bug062Verify() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        ProductEntity product = productWithStock(productUuid, 10, 0);

        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());
        // lockByUuid is invoked AFTER qty validation in the source we read — so it must NOT be called
        // for qty<=0. We don't stub it; if invoked Mockito returns Optional.empty() and ProductNotFound
        // would be thrown instead — which would still fail this test (wrong type).

        assertThatThrownBy(() -> service.reserveStock(orderUuid, Map.of(productUuid, -5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(productUuid.toString())
                .hasMessageContaining("-5");

        verify(stockRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("BUG-062: qty=0 rejected with IllegalArgumentException (verify F2 fix)")
    void reserveStock_zeroQty_rejected_bug062Verify() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();

        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.reserveStock(orderUuid, Map.of(productUuid, 0)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(stockRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // commitStock
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("commitStock: happy — decrements stock + reservedStock; InOrder repo + redis")
    void commitStock_happyPath_inOrderAcrossRepoAndRedis() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        StockEntity res = reservation(orderUuid, productUuid, 4);
        ProductEntity product = productWithStock(productUuid, 10, 4);

        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(List.of(res));
        when(productRepository.lockByUuid(productUuid)).thenReturn(Optional.of(product));

        service.commitStock(orderUuid);

        ArgumentCaptor<ProductEntity> productCaptor = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productRepository).save(productCaptor.capture());
        ProductEntity saved = productCaptor.getValue();
        assertThat(saved.getStock()).isEqualTo(6);          // 10 - 4
        assertThat(saved.getReservedStock()).isEqualTo(0);  // 4 - 4

        InOrder inOrder = inOrder(productRepository, stockRepository, redisTemplate);
        inOrder.verify(productRepository).save(any(ProductEntity.class));
        inOrder.verify(stockRepository).deleteByOrderUuid(orderUuid);
        inOrder.verify(redisTemplate).delete(KEY_PREFIX + orderUuid);
    }

    @Test
    @DisplayName("commitStock: missing product on commit raises ProductNotFoundException")
    void commitStock_missingProduct_throwsProductNotFound() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        StockEntity res = reservation(orderUuid, productUuid, 2);

        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(List.of(res));
        when(productRepository.lockByUuid(productUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.commitStock(orderUuid))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining(productUuid.toString());
    }

    @Test
    @DisplayName("commitStock: empty reservation set — behaviour-preserving cleanup (delete + redis.delete still fire)")
    void commitStock_emptyReservations_currentBehaviour_pinsBug064() {
        UUID orderUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());

        service.commitStock(orderUuid);

        verify(productRepository, never()).save(any());
        verify(productRepository, never()).lockByUuid(any());
        verify(stockRepository).deleteByOrderUuid(orderUuid);
        verify(redisTemplate).delete(KEY_PREFIX + orderUuid);
    }

    @Test
    @DisplayName("BUG-064 FIX: commitStock with no reservation logs a WARN (signal for upstream double-commit)")
    void commitStock_emptyReservations_logsWarn_bug064() {
        // BUG-064 FIX: behaviour preserved (no throw), but a WARN is emitted naming the orderUuid
        // so a double-commit / replayed webhook is observable in logs.
        final UUID orderUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());

        final ch.qos.logback.classic.Logger stockLogger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(StockServiceImp.class);
        final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        stockLogger.addAppender(appender);
        try {
            service.commitStock(orderUuid);
        } finally {
            stockLogger.detachAppender(appender);
        }

        assertThat(appender.list)
                .anyMatch(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN
                        && e.getFormattedMessage().contains(orderUuid.toString())
                        && e.getFormattedMessage().contains("commitStock"));
    }

    // ---------------------------------------------------------------------
    // releaseStock
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("releaseStock: idempotent no-op when reservation is missing")
    void releaseStock_missingReservation_isNoOp() {
        UUID orderUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());

        service.releaseStock(orderUuid);

        verify(productRepository, never()).lockByUuid(any());
        verify(productRepository, never()).save(any());
        verify(stockRepository, never()).deleteByOrderUuid(any());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("releaseStock: multi-product — restores reservedStock for each, deletes reservations + Redis key")
    void releaseStock_multiProduct_restoresEach() {
        UUID orderUuid = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        StockEntity r1 = reservation(orderUuid, p1, 2);
        StockEntity r2 = reservation(orderUuid, p2, 5);
        ProductEntity prod1 = productWithStock(p1, 10, 2);
        ProductEntity prod2 = productWithStock(p2, 20, 5);

        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(List.of(r1, r2));
        when(productRepository.lockByUuid(p1)).thenReturn(Optional.of(prod1));
        when(productRepository.lockByUuid(p2)).thenReturn(Optional.of(prod2));

        service.releaseStock(orderUuid);

        ArgumentCaptor<ProductEntity> savedProducts = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productRepository, times(2)).save(savedProducts.capture());
        List<ProductEntity> saved = savedProducts.getAllValues();
        assertThat(saved).extracting(ProductEntity::getReservedStock).containsExactlyInAnyOrder(0, 0);
        // Stock untouched by release.
        assertThat(saved).extracting(ProductEntity::getStock).containsExactlyInAnyOrder(10, 20);

        verify(stockRepository).deleteByOrderUuid(orderUuid);
        verify(redisTemplate).delete(KEY_PREFIX + orderUuid);
    }

    @Test
    @DisplayName("BUG-063: releaseStock with missing product throws domain ProductNotFoundException (verify F2 fix)")
    void releaseStock_missingProduct_throwsDomainException_bug063Verify() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        StockEntity res = reservation(orderUuid, productUuid, 1);

        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(List.of(res));
        when(productRepository.lockByUuid(productUuid)).thenReturn(Optional.empty());

        // F2 fix: throws domain ProductNotFoundException (not raw NoSuchElementException).
        assertThatThrownBy(() -> service.releaseStock(orderUuid))
                .isInstanceOf(ProductNotFoundException.class)
                .isNotInstanceOf(NoSuchElementException.class);
    }

    // ---------------------------------------------------------------------
    // Multi-product canonical lock order (BUG-060 — F1+F2 fix verification)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("BUG-060 verify: HashMap input — productRepository.lockByUuid called in canonical (sorted) UUID order")
    void reserveStock_multiProduct_canonicalLockOrder_bug060Verify() {
        UUID orderUuid = UUID.randomUUID();
        // Two UUIDs whose toString() ordering is unambiguous and stable.
        UUID lower = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higher = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffffe");

        ProductEntity p1 = productWithStock(lower, 10, 0);
        ProductEntity p2 = productWithStock(higher, 10, 0);

        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());
        when(productRepository.lockByUuid(lower)).thenReturn(Optional.of(p1));
        when(productRepository.lockByUuid(higher)).thenReturn(Optional.of(p2));

        // HashMap insertion order intentionally REVERSE of canonical sort.
        Map<UUID, Integer> qty = new HashMap<>();
        qty.put(higher, 1);
        qty.put(lower, 1);

        service.reserveStock(orderUuid, qty);

        ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(productRepository, times(2)).lockByUuid(uuidCaptor.capture());
        List<UUID> lockOrder = uuidCaptor.getAllValues();
        // Canonical order = sorted ascending by UUID.toString()
        assertThat(lockOrder).containsExactly(lower, higher);
    }

    @Test
    @DisplayName("reserveStock: LinkedHashMap with REVERSE canonical insertion order — still locks in canonical order")
    void reserveStock_linkedHashMap_reverseInsertion_stillCanonical() {
        UUID orderUuid = UUID.randomUUID();
        UUID a = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        UUID b = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        UUID c = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());
        when(productRepository.lockByUuid(a)).thenReturn(Optional.of(productWithStock(a, 5, 0)));
        when(productRepository.lockByUuid(b)).thenReturn(Optional.of(productWithStock(b, 5, 0)));
        when(productRepository.lockByUuid(c)).thenReturn(Optional.of(productWithStock(c, 5, 0)));

        Map<UUID, Integer> qty = new LinkedHashMap<>();
        qty.put(c, 1);
        qty.put(b, 1);
        qty.put(a, 1);

        service.reserveStock(orderUuid, qty);

        ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(productRepository, times(3)).lockByUuid(uuidCaptor.capture());
        assertThat(uuidCaptor.getAllValues()).containsExactly(a, b, c);
    }

    // ---------------------------------------------------------------------
    // Concurrency — ReentrantLock simulating DB pessimistic lock
    // ---------------------------------------------------------------------

    /**
     * Wraps {@link ProductRepository#lockByUuid(UUID)} so two concurrent reserves on the same
     * product serialize through a single {@link ReentrantLock} that mirrors the DB row-level lock.
     * The lock is released through a SAVE-time hook attached to the productRepository mock.
     */
    @Test
    @DisplayName("Concurrency: two threads race for limited stock — exactly one success, exactly one NotEnoughStockException")
    void concurrency_twoThreadsRace_oneWinsOneFails() throws Exception {
        UUID p = UUID.randomUUID();
        UUID order1 = UUID.randomUUID();
        UUID order2 = UUID.randomUUID();
        // stock=3, available=3; each thread asks for 2 ⇒ only one can win
        ProductEntity sharedProduct = productWithStock(p, 3, 0);

        ReentrantLock dbLock = new ReentrantLock();

        when(stockRepository.findByOrderUuid(any(UUID.class))).thenReturn(Collections.emptyList());
        when(productRepository.lockByUuid(p)).thenAnswer(inv -> {
            dbLock.lock();
            return Optional.of(sharedProduct);
        });
        // Mock save to unlock — mirrors transaction commit releasing row lock.
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(inv -> {
            if (dbLock.isHeldByCurrentThread()) {
                dbLock.unlock();
            }
            return inv.getArgument(0);
        });

        runRaceAndAssertOneWinner(order1, order2, p, dbLock);
    }

    private void runRaceAndAssertOneWinner(UUID order1, UUID order2, UUID p, ReentrantLock dbLock) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger successes = new AtomicInteger();
            AtomicInteger notEnoughStockFailures = new AtomicInteger();
            AtomicReference<Throwable> unexpected = new AtomicReference<>();

            Runnable task1 = () -> runReserve(order1, p, 2, start, dbLock, successes, notEnoughStockFailures, unexpected);
            Runnable task2 = () -> runReserve(order2, p, 2, start, dbLock, successes, notEnoughStockFailures, unexpected);

            executor.submit(task1);
            executor.submit(task2);

            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            assertThat(unexpected.get()).isNull();
            assertThat(successes.get()).isEqualTo(1);
            assertThat(notEnoughStockFailures.get()).isEqualTo(1);
        } finally {
            if (!executor.isTerminated()) executor.shutdownNow();
            // Defensive cleanup — release lock if a thread died holding it.
            while (dbLock.isHeldByCurrentThread()) dbLock.unlock();
        }
    }

    private void runReserve(UUID orderUuid, UUID p, int qty, CountDownLatch start,
                            ReentrantLock dbLock, AtomicInteger successes,
                            AtomicInteger notEnoughStockFailures, AtomicReference<Throwable> unexpected) {
        try {
            start.await();
            try {
                service.reserveStock(orderUuid, Map.of(p, qty));
                successes.incrementAndGet();
            } catch (NotEnoughStockException ex) {
                notEnoughStockFailures.incrementAndGet();
            } finally {
                // If the service threw before save() was called, the dbLock is still held.
                if (dbLock.isHeldByCurrentThread()) dbLock.unlock();
            }
        } catch (Throwable t) {
            unexpected.set(t);
        }
    }

    @Test
    @DisplayName("Concurrency: two threads with enough headroom — both succeed, additive reservation")
    void concurrency_bothSucceed_withHeadroom() throws Exception {
        UUID p = UUID.randomUUID();
        UUID order1 = UUID.randomUUID();
        UUID order2 = UUID.randomUUID();
        ProductEntity shared = productWithStock(p, 100, 0);

        ReentrantLock dbLock = new ReentrantLock();

        when(stockRepository.findByOrderUuid(any(UUID.class))).thenReturn(Collections.emptyList());
        when(productRepository.lockByUuid(p)).thenAnswer(inv -> {
            dbLock.lock();
            return Optional.of(shared);
        });
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(inv -> {
            if (dbLock.isHeldByCurrentThread()) dbLock.unlock();
            return inv.getArgument(0);
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger successes = new AtomicInteger();
            AtomicReference<Throwable> unexpected = new AtomicReference<>();

            Runnable r1 = () -> runReserve(order1, p, 10, start, dbLock, successes, new AtomicInteger(), unexpected);
            Runnable r2 = () -> runReserve(order2, p, 20, start, dbLock, successes, new AtomicInteger(), unexpected);
            executor.submit(r1);
            executor.submit(r2);

            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(unexpected.get()).isNull();
            assertThat(successes.get()).isEqualTo(2);
            // Serialized additive: both reservations applied
            assertThat(shared.getReservedStock()).isEqualTo(30);
        } finally {
            if (!executor.isTerminated()) executor.shutdownNow();
            while (dbLock.isHeldByCurrentThread()) dbLock.unlock();
        }
    }

    @Test
    @DisplayName("Concurrency: release+reserve race — invariant holds (no oversubscription)")
    void concurrency_releaseAndReserveRace_invariantHolds() throws Exception {
        UUID p = UUID.randomUUID();
        UUID releaseOrder = UUID.randomUUID();
        UUID reserveOrder = UUID.randomUUID();
        // Initial: stock=10, reserved=8 by releaseOrder.
        ProductEntity shared = productWithStock(p, 10, 8);

        ReentrantLock dbLock = new ReentrantLock();

        when(stockRepository.findByOrderUuid(reserveOrder)).thenReturn(Collections.emptyList());
        when(stockRepository.findByOrderUuid(releaseOrder))
                .thenReturn(List.of(reservation(releaseOrder, p, 8)));
        when(productRepository.lockByUuid(p)).thenAnswer(inv -> {
            dbLock.lock();
            return Optional.of(shared);
        });
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(inv -> {
            if (dbLock.isHeldByCurrentThread()) dbLock.unlock();
            return inv.getArgument(0);
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<Throwable> unexpected = new AtomicReference<>();
            AtomicInteger reserveSuccesses = new AtomicInteger();
            AtomicInteger reserveFailures = new AtomicInteger();

            Runnable releaser = () -> {
                try {
                    start.await();
                    try {
                        service.releaseStock(releaseOrder);
                    } finally {
                        if (dbLock.isHeldByCurrentThread()) dbLock.unlock();
                    }
                } catch (Throwable t) { unexpected.set(t); }
            };
            Runnable reserver = () -> runReserve(reserveOrder, p, 5, start, dbLock,
                    reserveSuccesses, reserveFailures, unexpected);

            executor.submit(releaser);
            executor.submit(reserver);
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(unexpected.get()).isNull();
            // Invariant: at the end, reserved cannot exceed stock (10), regardless of interleave.
            assertThat(shared.getReservedStock()).isLessThanOrEqualTo(shared.getStock());
            // Either reserve succeeded (release ran first → reserved=5) or released-only (reserved=0 + reserve fail)
            assertThat(reserveSuccesses.get() + reserveFailures.get()).isEqualTo(1);
        } finally {
            if (!executor.isTerminated()) executor.shutdownNow();
            while (dbLock.isHeldByCurrentThread()) dbLock.unlock();
        }
    }

    @Test
    @DisplayName("Concurrency sanity: WITHOUT lock, both threads see stale availability — proves prod lock is load-bearing")
    void concurrency_noLocking_provesProductionLockIsLoadBearing() throws Exception {
        UUID p = UUID.randomUUID();
        UUID order1 = UUID.randomUUID();
        UUID order2 = UUID.randomUUID();

        when(stockRepository.findByOrderUuid(any(UUID.class))).thenReturn(Collections.emptyList());

        // Barrier so both threads READ a fresh, independent copy of the product simultaneously.
        // This simulates the absence of pessimistic locking: each transaction sees a consistent
        // snapshot taken before either has written. Both copies have stock=3, reserved=0.
        CountDownLatch barrier = new CountDownLatch(2);
        when(productRepository.lockByUuid(p)).thenAnswer(inv -> {
            barrier.countDown();
            barrier.await(2, TimeUnit.SECONDS);
            // Fresh copy per call → simulates snapshot-isolation WITHOUT serialization.
            return Optional.of(productWithStock(p, 3, 0));
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger successes = new AtomicInteger();
            AtomicReference<Throwable> err = new AtomicReference<>();

            Runnable t1 = () -> {
                try {
                    start.await();
                    try {
                        service.reserveStock(order1, Map.of(p, 2));
                        successes.incrementAndGet();
                    } catch (NotEnoughStockException ignored) { /* expected for at most one */ }
                } catch (Throwable th) { err.set(th); }
            };
            Runnable t2 = () -> {
                try {
                    start.await();
                    try {
                        service.reserveStock(order2, Map.of(p, 2));
                        successes.incrementAndGet();
                    } catch (NotEnoughStockException ignored) { /* expected for at most one */ }
                } catch (Throwable th) { err.set(th); }
            };

            executor.submit(t1);
            executor.submit(t2);
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(err.get()).isNull();
            // Without serialized locking BOTH pass the availability check on their independent snapshots.
            // This proves the production pessimistic lock is load-bearing — without it, oversubscription occurs.
            assertThat(successes.get()).isEqualTo(2);
        } finally {
            if (!executor.isTerminated()) executor.shutdownNow();
        }
    }

    // ---------------------------------------------------------------------
    // Redis-specific assertions
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Redis: reservation key uses 'reservation:order:<uuid>' format")
    void redis_reservationKeyFormat() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());
        when(productRepository.lockByUuid(productUuid)).thenReturn(Optional.of(productWithStock(productUuid, 10, 0)));

        service.reserveStock(orderUuid, Map.of(productUuid, 1));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(keyCaptor.capture(), any(), any(Duration.class));
        assertThat(keyCaptor.getValue()).isEqualTo("reservation:order:" + orderUuid);
    }

    @Test
    @DisplayName("Redis: TTL value equals Duration.ofMinutes(5)")
    void redis_ttlIsFiveMinutes() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());
        when(productRepository.lockByUuid(productUuid)).thenReturn(Optional.of(productWithStock(productUuid, 10, 0)));

        service.reserveStock(orderUuid, Map.of(productUuid, 1));

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(anyString(), any(), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("Redis: stored value equals ACTIVE.name()")
    void redis_valueIsActiveEnumName() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());
        when(productRepository.lockByUuid(productUuid)).thenReturn(Optional.of(productWithStock(productUuid, 10, 0)));

        service.reserveStock(orderUuid, Map.of(productUuid, 1));

        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(valueOps).set(anyString(), valueCaptor.capture(), any(Duration.class));
        assertThat(valueCaptor.getValue()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Redis: releaseStock does NOT call opsForValue() (only delete)")
    void redis_releaseStock_doesNotUseOpsForValue() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid))
                .thenReturn(List.of(reservation(orderUuid, productUuid, 1)));
        when(productRepository.lockByUuid(productUuid))
                .thenReturn(Optional.of(productWithStock(productUuid, 10, 1)));

        service.releaseStock(orderUuid);

        // valueOps must never be touched; we use verifyNoInteractions on the mock itself.
        verifyNoInteractions(valueOps);
        verify(redisTemplate).delete(KEY_PREFIX + orderUuid);
    }

    @Test
    @DisplayName("Redis: refresh-TTL path on idempotent re-reserve uses expire(key, TTL) with the canonical key")
    void redis_refreshTtl_usesCanonicalKey() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid))
                .thenReturn(List.of(reservation(orderUuid, productUuid, 1)));

        service.reserveStock(orderUuid, Map.of(productUuid, 1));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(redisTemplate).expire(keyCaptor.capture(), ttlCaptor.capture());
        assertThat(keyCaptor.getValue()).isEqualTo("reservation:order:" + orderUuid);
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMinutes(5));
    }

    // ---------------------------------------------------------------------
    // Additional edge cases
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("reserveStock: empty quantities map — no products locked, but Redis ACTIVE key is still set")
    void reserveStock_emptyQuantitiesMap_pinsCurrentBehaviour() {
        UUID orderUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());

        service.reserveStock(orderUuid, new HashMap<>());

        verify(productRepository, never()).lockByUuid(any());
        verify(stockRepository, never()).save(any());
        // Redis sentinel still written — pinned current behaviour.
        verify(valueOps).set(eq(KEY_PREFIX + orderUuid), eq("ACTIVE"), eq(EXPECTED_TTL));
    }

    @Test
    @DisplayName("commitStock: multi-product happy — each product saved, stock+reserved decremented")
    void commitStock_multiProduct_decrementsBoth() {
        UUID orderUuid = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        StockEntity r1 = reservation(orderUuid, p1, 3);
        StockEntity r2 = reservation(orderUuid, p2, 7);
        ProductEntity prod1 = productWithStock(p1, 10, 3);
        ProductEntity prod2 = productWithStock(p2, 20, 7);

        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(List.of(r1, r2));
        when(productRepository.lockByUuid(p1)).thenReturn(Optional.of(prod1));
        when(productRepository.lockByUuid(p2)).thenReturn(Optional.of(prod2));

        service.commitStock(orderUuid);

        ArgumentCaptor<ProductEntity> savedCap = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productRepository, times(2)).save(savedCap.capture());
        List<ProductEntity> saved = new ArrayList<>(savedCap.getAllValues());
        assertThat(saved).extracting(ProductEntity::getStock).containsExactlyInAnyOrder(7, 13);
        assertThat(saved).extracting(ProductEntity::getReservedStock).containsExactlyInAnyOrder(0, 0);

        verify(stockRepository).deleteByOrderUuid(orderUuid);
        verify(redisTemplate).delete(KEY_PREFIX + orderUuid);
    }

    @Test
    @DisplayName("releaseStock: InOrder over per-product save then deleteByOrderUuid then redis.delete")
    void releaseStock_inOrderAcrossRepoAndRedis() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid))
                .thenReturn(List.of(reservation(orderUuid, productUuid, 2)));
        when(productRepository.lockByUuid(productUuid))
                .thenReturn(Optional.of(productWithStock(productUuid, 10, 2)));

        service.releaseStock(orderUuid);

        InOrder inOrder = inOrder(productRepository, stockRepository, redisTemplate);
        inOrder.verify(productRepository).save(any(ProductEntity.class));
        inOrder.verify(stockRepository).deleteByOrderUuid(orderUuid);
        inOrder.verify(redisTemplate).delete(KEY_PREFIX + orderUuid);
    }

    @Test
    @DisplayName("reserveStock: idempotent path does NOT delete or re-save reservations")
    void reserveStock_idempotent_noDeleteNoResave() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid))
                .thenReturn(List.of(reservation(orderUuid, productUuid, 1)));

        service.reserveStock(orderUuid, Map.of(productUuid, 1));

        verify(stockRepository, never()).deleteByOrderUuid(any());
        verify(stockRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("reserveStock: ProductNotFoundException message matches F2 contract (\"Product <uuid> not found\")")
    void reserveStock_productNotFound_messageContainsLiteralProductPrefix_bug061Sibling() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());
        when(productRepository.lockByUuid(productUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserveStock(orderUuid, Map.of(productUuid, 1)))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("Product")
                .hasMessageContaining(productUuid.toString())
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("commitStock: Redis delete key uses canonical 'reservation:order:<uuid>' format")
    void commitStock_redisDeleteUsesCanonicalKey() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid))
                .thenReturn(List.of(reservation(orderUuid, productUuid, 1)));
        when(productRepository.lockByUuid(productUuid))
                .thenReturn(Optional.of(productWithStock(productUuid, 10, 1)));

        service.commitStock(orderUuid);

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).delete(keyCap.capture());
        assertThat(keyCap.getValue()).isEqualTo("reservation:order:" + orderUuid);
    }

    @Test
    @DisplayName("reserveStock: locks product BEFORE saving the StockEntity (InOrder)")
    void reserveStock_lockOccursBeforeSave() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());
        when(productRepository.lockByUuid(productUuid))
                .thenReturn(Optional.of(productWithStock(productUuid, 10, 0)));

        service.reserveStock(orderUuid, Map.of(productUuid, 1));

        InOrder inOrder = inOrder(productRepository, stockRepository, valueOps);
        inOrder.verify(productRepository).lockByUuid(productUuid);
        inOrder.verify(stockRepository).save(any(StockEntity.class));
        inOrder.verify(productRepository).save(any(ProductEntity.class));
        inOrder.verify(valueOps).set(anyString(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("reserveStock: redisTemplate.opsForValue() is NOT invoked when shortfall raises NotEnoughStock")
    void reserveStock_shortfall_doesNotCallOpsForValueSet() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());
        when(productRepository.lockByUuid(productUuid))
                .thenReturn(Optional.of(productWithStock(productUuid, 1, 0)));

        assertThatThrownBy(() -> service.reserveStock(orderUuid, Map.of(productUuid, 5)))
                .isInstanceOf(NotEnoughStockException.class);

        verify(valueOps, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("releaseStock: when reservation is missing, redis.delete is NOT called")
    void releaseStock_missingReservation_noRedisDelete() {
        UUID orderUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());

        service.releaseStock(orderUuid);

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("commitStock: empty reservations — opsForValue() is NOT invoked (only delete + redis.delete)")
    void commitStock_empty_doesNotUseOpsForValue() {
        UUID orderUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());

        service.commitStock(orderUuid);

        verifyNoInteractions(valueOps);
    }

    @Test
    @DisplayName("commitStock: stockRepository.findByOrderUuid called exactly once")
    void commitStock_findByOrderUuid_calledOnce() {
        UUID orderUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());

        service.commitStock(orderUuid);

        verify(stockRepository, times(1)).findByOrderUuid(orderUuid);
    }

    @Test
    @DisplayName("reserveStock: stockRepository.findByOrderUuid called exactly once before locking")
    void reserveStock_findByOrderUuid_calledOnce() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(Collections.emptyList());
        when(productRepository.lockByUuid(productUuid))
                .thenReturn(Optional.of(productWithStock(productUuid, 5, 0)));

        service.reserveStock(orderUuid, Map.of(productUuid, 1));

        verify(stockRepository, times(1)).findByOrderUuid(orderUuid);
        verify(productRepository, atLeastOnce()).lockByUuid(productUuid);
    }
}
