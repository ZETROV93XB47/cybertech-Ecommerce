package com.novatech.cybertech.fixtures.builders;

import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.entities.enums.TransactionType;
import com.novatech.cybertech.entities.valueObjects.CurrencyCode;
import com.novatech.cybertech.entities.valueObjects.Money;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Test fixture builder for {@link PaymentEntity}. Presets {@code uuid} explicitly and seeds a
 * non-blank {@code idempotencyKey} so callers don't trip the NOT-NULL DB constraint by accident.
 */
public final class PaymentEntityBuilder {

    private PaymentEntityBuilder() {
    }

    public static PaymentEntity aValidPayment() {
        return aValidPaymentBuilder().build();
    }

    public static PaymentEntity.PaymentEntityBuilder<?, ?> aValidPaymentBuilder() {
        return PaymentEntity.builder()
                .uuid(UUID.randomUUID())
                .amount(new Money(new BigDecimal("100.00"), CurrencyCode.EUR))
                .paymentType(PaymentType.VISA)
                .transactionType(TransactionType.PAYMENT)
                .status(PaymentAttemptStatus.SUCCESS)
                .idempotencyKey("idem-" + UUID.randomUUID())
                .orderEntity(OrderEntityBuilder.aValidOrder());
    }
}
