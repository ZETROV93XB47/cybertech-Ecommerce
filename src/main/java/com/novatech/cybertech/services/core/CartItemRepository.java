package com.novatech.cybertech.services.core;

import com.novatech.cybertech.entities.CartItemEntity;
import com.novatech.cybertech.repositories.CrudBaseRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartItemRepository extends CrudBaseRepository<CartItemEntity, Long> {
}