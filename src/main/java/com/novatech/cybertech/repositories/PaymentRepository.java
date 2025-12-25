package com.novatech.cybertech.repositories;

import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.enums.PaymentStatus;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends CrudBaseRepository<PaymentEntity, Long> {
    List<PaymentEntity> findByPaymentStatus(PaymentStatus status);
}
