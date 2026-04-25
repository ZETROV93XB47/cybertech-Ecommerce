package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
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
}
