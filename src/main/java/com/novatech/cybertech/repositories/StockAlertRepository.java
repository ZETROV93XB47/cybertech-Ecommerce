package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.StockAlertEntity;
import com.novatech.cybertech.entities.enums.StockAlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockAlertRepository extends CrudBaseRepository<StockAlertEntity, Long> {

    Optional<StockAlertEntity> findByUser_KeycloakIdAndProduct_Uuid(String keycloakId, UUID productUuid);

    /** The caller's alerts in one status ("my alerts" page), with the product loaded. */
    @EntityGraph(attributePaths = "product")
    Page<StockAlertEntity> findByUser_KeycloakIdAndStatus(String keycloakId, StockAlertStatus status, Pageable pageable);

    /**
     * Ids of the {@code PENDING} alerts whose product can be ordered again, after {@code afterId}
     * in id order: the job reads them page by page with the last id of the previous page.
     */
    @Query("""
            SELECT a.id
            FROM   StockAlertEntity a
            WHERE  a.status = com.novatech.cybertech.entities.enums.StockAlertStatus.PENDING
            AND    COALESCE(a.product.stock, 0) - COALESCE(a.product.reservedStock, 0) > 0
            AND    a.id > :afterId
            ORDER  BY a.id
            """)
    List<Long> findPendingIdsWithAvailableProductAfter(@Param("afterId") Long afterId, Pageable pageable);

    /** Alert with its user and product, for the notification. */
    @Query("""
            SELECT a
            FROM   StockAlertEntity a
            JOIN   FETCH a.user
            JOIN   FETCH a.product
            WHERE  a.id = :id
            """)
    Optional<StockAlertEntity> findWithUserAndProductById(@Param("id") Long id);

    /** {@code PENDING} alerts requested before {@code cutoff} become {@code EXPIRED}. */
    @Modifying
    @Query("""
            UPDATE StockAlertEntity a
            SET    a.status = com.novatech.cybertech.entities.enums.StockAlertStatus.EXPIRED
            WHERE  a.status = com.novatech.cybertech.entities.enums.StockAlertStatus.PENDING
            AND    a.requestedAt < :cutoff
            """)
    int expirePendingRequestedBefore(@Param("cutoff") LocalDateTime cutoff);
}
