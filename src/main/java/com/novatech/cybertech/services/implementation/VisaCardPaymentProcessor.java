package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.annotation.PaymentTypeHandler;
import com.novatech.cybertech.entities.PaymentEntity;
import com.novatech.cybertech.entities.enums.PaymentStatus;
import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.services.core.PaymentProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@PaymentTypeHandler(PaymentType.VISA)
public class VisaCardPaymentProcessor implements PaymentProcessor {

    //private final PaymentRepository paymentRepository;

    @Override
    public PaymentEntity processPayment(PaymentEntity payment) {
        //This part will be implmemented correctly later, this code is written just to simulate a call to an external service (the user's bank)
        log.info("calling user's bank to process the payment");
        log.info("Processing credit card payment for amount:  {}", payment.getAmount());
        log.info("Payment processed successfully");
        payment.setPaymentStatus(PaymentStatus.SUCCESS);

        return payment;
    }
}
