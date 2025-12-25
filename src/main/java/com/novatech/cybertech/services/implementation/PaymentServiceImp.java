package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.factory.PaymentStrategyFactory;
import com.novatech.cybertech.services.core.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImp implements PaymentService {

    private final PaymentStrategyFactory paymentStrategyFactory;

    @Override
    public PaymentEntity processPayment(final PaymentType paymentType, final BigDecimal paymentAmount) {

        PaymentEntity paymentEntity = PaymentEntity.builder()
                .uuid(UUID.randomUUID())
                .amount(paymentAmount)
                .paymentType(paymentType)
                .paymentStatus(null)
                .paymentDate(LocalDateTime.now())
                .build();

        return paymentStrategyFactory.getServiceFromPaymentType(paymentEntity.getPaymentType()).processPayment(paymentEntity);
    }
}
