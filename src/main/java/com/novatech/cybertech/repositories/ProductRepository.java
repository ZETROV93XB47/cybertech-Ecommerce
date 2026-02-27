package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.ProductEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends CrudBaseRepository<ProductEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductEntity p WHERE p.uuid = :uuid")
    Optional<ProductEntity> lockByUuid(UUID uuid);

    @Query("""
    SELECT p
    FROM ProductEntity p
    LEFT JOIN p.orderItemEntities oi
    GROUP BY p
    ORDER BY SUM(oi.quantity) DESC
    """)
    List<ProductEntity> findBestSellers(Pageable pageable);
}
