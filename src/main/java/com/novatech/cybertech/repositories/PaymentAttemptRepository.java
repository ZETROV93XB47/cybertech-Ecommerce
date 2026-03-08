package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentAttemptRepository extends CrudBaseRepository<PaymentEntity, Long> {
    List<PaymentEntity> findByStatus(PaymentAttemptStatus status);
    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);
    boolean existsByOrderEntity_UuidAndStatus(UUID orderUuid, PaymentAttemptStatus status);
    Optional<PaymentEntity> findByStripePaymentID(String stripePaymentID);
    Optional<PaymentEntity> findByProviderEventId(String providerEventId);
}
