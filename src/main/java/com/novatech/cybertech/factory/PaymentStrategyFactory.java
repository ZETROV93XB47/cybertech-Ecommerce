package com.novatech.cybertech.factory;

import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.services.core.PaymentAttemptProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class PaymentStrategyFactory {

    private final Map<PaymentType, PaymentAttemptProcessor> serviceMap;

    public PaymentAttemptProcessor getServiceFromPaymentType(final PaymentType type) {
        return serviceMap.get(type);
    }
}
