package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.NotificationEntity;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends CrudBaseRepository<NotificationEntity, Long> {
}
