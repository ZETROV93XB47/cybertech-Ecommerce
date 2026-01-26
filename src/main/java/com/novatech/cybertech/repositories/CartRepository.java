package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.CartEntity;
import org.springframework.stereotype.Repository;

@Repository
public interface CartRepository extends CrudBaseRepository<CartEntity, Long> {
}