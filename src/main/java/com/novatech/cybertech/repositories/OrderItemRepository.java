package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.OrderItemEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends CrudBaseRepository<OrderItemEntity, Long> {

    /**
     * Returns {@code true} as soon as the user identified by {@code keycloakId} has at least one
     * order item that references the product identified by {@code productUuid}, regardless of the
     * order's status. Replaces the in-memory walk over the user's lazy order graph performed by
     * {@code ReviewManagementServiceImp#checkIfUserAlreadyBoughtThisProduct} so the check no
     * longer triggers a 4-level lazy-load chain.
     */
    @Query("""
            SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END
            FROM   OrderItemEntity oi
            WHERE  oi.orderEntity.userEntity.keycloakId = :keycloakId
            AND    oi.productEntity.uuid = :productUuid
            """)
    boolean userHasBoughtProduct(
            @Param("keycloakId") String keycloakId,
            @Param("productUuid") UUID productUuid);

    /** Products the user bought in an order whose status is one of {@code statuses}. */
    @Query("""
            SELECT DISTINCT oi.productEntity.uuid
            FROM   OrderItemEntity oi
            WHERE  oi.orderEntity.userEntity.id = :userId
            AND    oi.orderEntity.status IN :statuses
            """)
    Set<UUID> findProductUuidsBoughtByUser(
            @Param("userId") Long userId,
            @Param("statuses") Collection<OrderStatus> statuses);
}
