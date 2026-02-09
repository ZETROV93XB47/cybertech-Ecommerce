package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends CrudBaseRepository<OrderEntity, Long> {
    List<OrderEntity> findAllByUuid(final UUID userUuid);

    List<OrderEntity> getAllByStatusIs(OrderStatus status);

    List<OrderEntity> findByStatusAndOrderDateBefore(OrderStatus status, LocalDateTime date);

    List<OrderEntity> findByStatus(OrderStatus status);

}
