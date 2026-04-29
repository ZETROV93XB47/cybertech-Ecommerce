package com.novatech.cybertech.validator.implementation;

import com.novatech.cybertech.dto.data.OrderValidationDto;
import com.novatech.cybertech.exceptions.UserNotActiveException;
import org.springframework.stereotype.Component;

@Component
public class ActiveUserValidator extends ChainableOrderValidator {

    /** FIX(EXCEPTION-MAPPING): centralise the contract message so error response bodies stay consistent. */
    private static final String USER_INACTIVE_MESSAGE = "User is inactive";

    @Override
    public void validate(final OrderValidationDto orderValidationDto) {

        if (!orderValidationDto.isUserActive()) {
            // FIX(EXCEPTION-MAPPING): IllegalStateException is not handled in ErrorManagementController,
            // so the previous code surfaced an opaque HTTP 500 to the client. UserNotActiveException is
            // already mapped to USER_NOT_ACTIVE (HTTP 403) — reuse it instead of introducing a duplicate
            // exception type (CLAUDE.md "Pas de duplication cross-classes").
            throw new UserNotActiveException(USER_INACTIVE_MESSAGE);
        }
        nextStep(orderValidationDto);
    }
}
