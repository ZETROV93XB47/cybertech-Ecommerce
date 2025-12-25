package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.StockEntity;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository extends CrudBaseRepository<StockEntity, Long> {

    List<StockEntity> findByOrderUuid(UUID orderUuid);

    Optional<StockEntity> findByOrderUuidAndProductUuid(UUID orderUuid, UUID productUuid);

    @Modifying
    void deleteByOrderUuid(UUID orderUuid);
}
