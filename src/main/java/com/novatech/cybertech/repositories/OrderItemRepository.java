package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.OrderItemEntity;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderItemRepository extends CrudBaseRepository<OrderItemEntity, Long> {
}
