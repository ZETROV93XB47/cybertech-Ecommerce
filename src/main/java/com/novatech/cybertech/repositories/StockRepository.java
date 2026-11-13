package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.StockEntity;
import com.novatech.cybertech.entities.enums.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository extends CrudBaseRepository<StockEntity, Long> {

    List<StockEntity> findByOrderUuid(UUID orderUuid);

    /**
     * {@code SELECT ... FOR UPDATE} on an order's reservation rows. Serialises concurrent handlers
     * of the SAME reservation (every app instance receives the Redis expiry event): the second one
     * waits, then finds the rows already deleted instead of giving the same units back twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockEntity s WHERE s.orderUuid = :orderUuid")
    List<StockEntity> lockByOrderUuid(@Param("orderUuid") UUID orderUuid);

    Optional<StockEntity> findByOrderUuidAndProductUuid(UUID orderUuid, UUID productUuid);

    List<StockEntity> findByReservationStatusAndCreatedAtBefore(ReservationStatus reservationStatus, LocalDateTime cutoff);

    @Modifying
    void deleteByOrderUuid(UUID orderUuid);
}
