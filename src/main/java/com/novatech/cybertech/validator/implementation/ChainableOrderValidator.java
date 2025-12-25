package com.novatech.cybertech.validator.implementation;

import com.novatech.cybertech.dto.data.OrderValidationDto;
import com.novatech.cybertech.validator.core.OrderValidator;

public abstract class ChainableOrderValidator implements OrderValidator {
    protected OrderValidator next;

    public ChainableOrderValidator setNext(OrderValidator next) {
        this.next = next;
        return this;
    }

    protected void nextStep(final OrderValidationDto orderValidationDto) {
        if (next != null) next.validate(orderValidationDto);
    }
}