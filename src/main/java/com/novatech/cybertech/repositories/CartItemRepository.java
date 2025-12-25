package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.CartItemEntity;
import org.springframework.stereotype.Repository;

@Repository
public interface CartItemRepository extends CrudBaseRepository<CartItemEntity, Long> {
}