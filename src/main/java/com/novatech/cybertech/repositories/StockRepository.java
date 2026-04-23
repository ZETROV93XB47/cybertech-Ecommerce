package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.StockEntity;
import com.novatech.cybertech.entities.enums.ReservationStatus;
import org.springframework.data.jpa.repository.Modifying;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository extends CrudBaseRepository<StockEntity, Long> {

    List<StockEntity> findByOrderUuid(UUID orderUuid);

    Optional<StockEntity> findByOrderUuidAndProductUuid(UUID orderUuid, UUID productUuid);

    List<StockEntity> findByReservationStatusAndCreatedAtBefore(ReservationStatus reservationStatus, LocalDateTime cutoff);

    @Modifying
    void deleteByOrderUuid(UUID orderUuid);
}
