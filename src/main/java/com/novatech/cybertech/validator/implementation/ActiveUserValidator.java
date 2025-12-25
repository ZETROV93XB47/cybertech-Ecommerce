package com.novatech.cybertech.validator.implementation;

import com.novatech.cybertech.dto.data.OrderValidationDto;
import org.springframework.stereotype.Component;

@Component
public class ActiveUserValidator extends ChainableOrderValidator {

    @Override
    public void validate(final OrderValidationDto orderValidationDto) {

        if (!orderValidationDto.isUserActive()) {
            throw new IllegalStateException("Utilisateur inactif !");
        }
        nextStep(orderValidationDto);
    }
}
