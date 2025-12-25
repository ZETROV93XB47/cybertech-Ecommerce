package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.ReviewEntity;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends CrudBaseRepository<ReviewEntity, Long> {
}
