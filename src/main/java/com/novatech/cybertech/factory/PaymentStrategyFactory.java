package com.novatech.cybertech.factory;

import com.novatech.cybertech.entities.enums.PaymentType;
import com.novatech.cybertech.services.core.PaymentAttemptProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentStrategyFactory {

    private final Map<Set<PaymentType>, PaymentAttemptProcessor> serviceMap;

    public PaymentAttemptProcessor getServiceFromPaymentType(final PaymentType type) {
        return serviceMap.entrySet().stream()
                .filter(entry -> entry.getKey().contains(type))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No processor for type: " + type));
    }
}
