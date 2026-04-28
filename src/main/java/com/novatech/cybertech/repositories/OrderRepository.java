package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends CrudBaseRepository<OrderEntity, Long> {
    List<OrderEntity> findAllByUuid(final UUID userUuid);

    List<OrderEntity> getAllByStatusIs(OrderStatus status);

    List<OrderEntity> findByStatusAndOrderDateBefore(OrderStatus status, LocalDateTime date);

    List<OrderEntity> findByStatus(OrderStatus status);

    /**
     * Loads, in a single SQL round-trip, every order that belongs to the user identified by
     * {@code keycloakId} and whose status is in {@code statuses}, eagerly joining its order
     * items and the products they reference. Used by the "reviewable products" endpoint to
     * avoid the N+M lazy-load chain triggered by walking {@code order.getOrderItemEntities()}
     * and {@code item.getProductEntity()} in memory.
     */
    @Query("""
            SELECT DISTINCT o
            FROM   OrderEntity o
            LEFT   JOIN FETCH o.orderItemEntities oi
            LEFT   JOIN FETCH oi.productEntity
            WHERE  o.userEntity.keycloakId = :keycloakId
            AND    o.status IN :statuses
            """)
    List<OrderEntity> findReviewableOrdersWithItemsByKeycloakIdAndStatusIn(
            @Param("keycloakId") String keycloakId,
            @Param("statuses") Collection<OrderStatus> statuses);

    /**
     * Frontend-gap #1 — paginated listing of every order belonging to the authenticated user
     * (resolved via {@code keycloakId}), optionally filtered by an explicit status set.
     *
     * <p>When {@code statuses} is {@code null} the WHERE clause short-circuits the second
     * predicate (no JPQL conditional needed thanks to the {@code :statuses IS NULL OR ...}
     * idiom), so callers can pass {@code null} to retrieve every order regardless of state.
     * The query mirrors the J1 pattern: a single round-trip backed by a count(*) sibling
     * automatically derived by Spring Data for {@link Page}.</p>
     */
    @Query("""
            SELECT o
            FROM   OrderEntity o
            WHERE  o.userEntity.keycloakId = :keycloakId
            AND    (:statuses IS NULL OR o.status IN :statuses)
            """)
    Page<OrderEntity> findByUserKeycloakIdAndOptionalStatuses(
            @Param("keycloakId") String keycloakId,
            @Param("statuses") Collection<OrderStatus> statuses,
            Pageable pageable);

    /**
     * Frontend-gap #2 — admin paginated listing with optional filters on status set and
     * user keycloakId. Both predicates are nullable so the same JPQL backs the
     * {@code GET /services/admin/management/order/get/all} endpoint regardless of the
     * combination of query params actually supplied by the admin UI.
     */
    @Query("""
            SELECT o
            FROM   OrderEntity o
            WHERE  (:statuses IS NULL OR o.status IN :statuses)
            AND    (:userKeycloakId IS NULL OR o.userEntity.keycloakId = :userKeycloakId)
            """)
    Page<OrderEntity> findAllByOptionalStatusesAndUserKeycloakId(
            @Param("statuses") Collection<OrderStatus> statuses,
            @Param("userKeycloakId") String userKeycloakId,
            Pageable pageable);
}
