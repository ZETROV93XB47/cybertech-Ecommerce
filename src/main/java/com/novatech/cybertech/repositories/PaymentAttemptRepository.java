package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.PaymentAttemptEntity;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentAttemptRepository extends CrudBaseRepository<PaymentAttemptEntity, Long> {
    List<PaymentAttemptEntity> findByStatus(PaymentAttemptStatus status);
    Optional<PaymentAttemptEntity> findByIdempotencyKey(String idempotencyKey);
    boolean existsByOrderEntity_UuidAndStatus(UUID orderUuid, PaymentAttemptStatus status);
}
